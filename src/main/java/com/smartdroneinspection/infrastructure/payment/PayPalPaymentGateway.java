package com.smartdroneinspection.infrastructure.payment;

import com.smartdroneinspection.subscriptions.gateway.PaymentGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The PayPal Orders v2 settlement channel.
 *
 * <p>Every call here is server to server. The platform creates the order for a price it computed
 * from the contract, captures it itself, and reads the settled amount and currency back out of
 * PayPal's own response. Nothing a browser sends is treated as evidence that money moved.
 */
class PayPalPaymentGateway implements PaymentGateway {

  private static final String TOKEN_PATH = "/v1/oauth2/token";
  private static final String ORDERS_PATH = "/v2/checkout/orders";
  private static final String CAPTURE_PATH = "/v2/checkout/orders/{orderId}/capture";

  private final RestClient client;
  private final PayPalProperties properties;
  private final ObjectMapper objectMapper;

  PayPalPaymentGateway(RestClient client, PayPalProperties properties, ObjectMapper objectMapper) {
    this.client = client;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  public PaymentGatewayOrder createOrder(BigDecimal amount, String currency, String description) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put(
        "intent", "CAPTURE"); // The platform captures server-side, so the payer only authorizes.
    request.put(
        "purchase_units",
        List.of(
            Map.of(
                "description",
                description,
                "amount",
                Map.of(
                    "currency_code",
                    currency,
                    "value",
                    amount.setScale(2, RoundingMode.HALF_UP).toPlainString()))));
    request.put(
        "application_context",
        Map.of(
            "return_url",
            properties.returnUrl(),
            "cancel_url",
            properties.cancelUrl(),
            "user_action",
            "PAY_NOW"));

    JsonNode response = post(ORDERS_PATH, request, true);
    String orderId = text(response, "id");
    if (orderId == null) {
      throw gatewayFailure("PayPal did not return an order id");
    }
    return new PaymentGatewayOrder(orderId, text(response, "status"), approvalUrl(response));
  }

  @Override
  public Capture captureOrder(String orderId) {
    JsonNode response = post(CAPTURE_PATH.replace("{orderId}", orderId), Map.of(), true);
    JsonNode purchaseUnit = firstPurchaseUnit(response);

    JsonNode capture = purchaseUnit == null ? null : purchaseUnit.path("payments").path("captures");
    JsonNode firstCapture =
        capture != null && capture.isArray() && !capture.isEmpty() ? capture.get(0) : null;

    String captureId = firstCapture == null ? null : text(firstCapture, "id");
    String status = firstCapture == null ? null : text(firstCapture, "status");
    JsonNode amountNode = firstCapture == null ? null : firstCapture.path("amount");
    String settledAmount = amountNode == null ? null : text(amountNode, "value");
    String settledCurrency = amountNode == null ? null : text(amountNode, "currency_code");
    String payerEmail = payerEmail(response);

    return new Capture(orderId, captureId, status, settledAmount, settledCurrency, payerEmail);
  }

  private JsonNode firstPurchaseUnit(JsonNode response) {
    JsonNode units = response.path("purchase_units");
    return units.isArray() && !units.isEmpty() ? units.get(0) : null;
  }

  /**
   * The payer-facing link PayPal created for the order. The browser is sent here to approve the
   * payment; it never learns a capture id.
   */
  private String approvalUrl(JsonNode response) {
    JsonNode links = response.path("links");
    if (!links.isArray()) {
      return null;
    }
    for (JsonNode link : links) {
      if ("payer-action".equals(text(link, "rel")) || "approve".equals(text(link, "rel"))) {
        String href = text(link, "href");
        if (!isPayPalApprovalUrl(href)) {
          throw gatewayFailure("PayPal returned an unexpected approval host");
        }
        return href;
      }
    }
    return null;
  }

  private boolean isPayPalApprovalUrl(String rawUrl) {
    if (rawUrl == null || rawUrl.isBlank()) {
      return false;
    }
    try {
      URI uri = URI.create(rawUrl);
      String expectedHost =
          "sandbox".equalsIgnoreCase(properties.mode())
              ? "www.sandbox.paypal.com"
              : "www.paypal.com";
      return "https".equalsIgnoreCase(uri.getScheme())
          && expectedHost.equalsIgnoreCase(uri.getHost());
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private String payerEmail(JsonNode response) {
    JsonNode payer = response.path("payer");
    JsonNode email = payer.path("email_address");
    return email.isMissingNode() || email.isNull() ? null : email.asString();
  }

  private JsonNode post(String path, Object body, boolean authenticated) {
    try {
      var request =
          client
              .post()
              .uri(path)
              .contentType(MediaType.APPLICATION_JSON)
              .headers(
                  headers -> {
                    if (authenticated) {
                      headers.setBearerAuth(accessToken());
                    }
                  });
      if (body instanceof Map<?, ?> map && !map.isEmpty()) {
        request.body(body);
      }
      JsonNode response = request.retrieve().body(JsonNode.class);
      return response == null ? objectMapper.createObjectNode() : response;
    } catch (RestClientException exception) {
      throw gatewayFailure("PayPal request failed: " + exception.getMessage());
    }
  }

  /**
   * Confirms a webhook really came from PayPal.
   *
   * <p>PayPal signs each notification and hosts the verification endpoint. The platform replays the
   * notification's own headers and body to that endpoint and trusts the answer, so a forged
   * notification cannot settle a subscription.
   */
  @Override
  public boolean verifyWebhookSignature(Map<String, String> headers, String rawPayload) {
    String webhookId = properties.webhookId();
    if (webhookId == null || webhookId.isBlank()) {
      // Without a configured webhook id there is nothing to verify against, so nothing is
      // accepted. A deployment that has not registered its webhook must not settle payments
      // from notifications.
      return false;
    }

    String transmissionId = headers.get("paypal-transmission-id");
    String transmissionTime = headers.get("paypal-transmission-time");
    String transmissionSignature = headers.get("paypal-transmission-sig");
    String certificateUrl = headers.get("paypal-cert-url");
    String authAlgo = headers.get("paypal-auth-algo");
    if (transmissionId == null
        || transmissionTime == null
        || transmissionSignature == null
        || certificateUrl == null
        || authAlgo == null) {
      return false;
    }
    String trustedCertificatePrefix =
        "sandbox".equalsIgnoreCase(properties.mode())
            ? "https://api-m.sandbox.paypal.com/"
            : "https://api.paypal.com/";
    if (!certificateUrl.startsWith(trustedCertificatePrefix)) {
      // Only the configured environment's PayPal certificate host is trusted; a self-signed
      // certificate or a certificate from the other environment must not vouch for the event.
      return false;
    }

    JsonNode webhookEvent;
    try {
      webhookEvent = objectMapper.readTree(rawPayload);
      if (webhookEvent == null || !webhookEvent.isObject()) {
        return false;
      }
    } catch (Exception exception) {
      return false;
    }

    Map<String, Object> verification = new LinkedHashMap<>();
    verification.put("transmission_id", transmissionId);
    verification.put("transmission_time", transmissionTime);
    verification.put("transmission_sig", transmissionSignature);
    verification.put("cert_url", certificateUrl);
    verification.put("auth_algo", authAlgo);
    verification.put("webhook_id", webhookId);
    verification.put("webhook_event", webhookEvent);

    try {
      JsonNode response =
          client
              .post()
              .uri("/v1/notifications/verify-webhook-signature")
              .contentType(MediaType.APPLICATION_JSON)
              .headers(headers2 -> headers2.setBearerAuth(accessToken()))
              .body(verification)
              .retrieve()
              .body(JsonNode.class);
      return response != null && "SUCCESS".equals(text(response, "verification_status"));
    } catch (RestClientException exception) {
      throw gatewayFailure("PayPal webhook verification failed: " + exception.getMessage());
    }
  }

  /**
   * Fetches a short-lived access token. PayPal issues one per client, so it is cached until it is
   * close to expiring instead of being requested for every order.
   */
  private String accessToken() {
    Instant now = Instant.now();
    if (cachedToken != null && tokenExpiresAt.isAfter(now.plusSeconds(60))) {
      return cachedToken;
    }
    try {
      String credentials =
          Base64.getEncoder()
              .encodeToString(
                  (properties.clientId() + ":" + properties.clientSecret())
                      .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      JsonNode response =
          client
              .post()
              .uri(TOKEN_PATH)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
              .body("grant_type=client_credentials")
              .retrieve()
              .body(JsonNode.class);
      String token = response == null ? null : text(response, "access_token");
      if (token == null) {
        throw gatewayFailure("PayPal did not return an access token");
      }
      cachedToken = token;
      long expiresIn = response.path("expires_in").asLong(0);
      tokenExpiresAt = now.plusSeconds(Math.max(60, expiresIn));
      return token;
    } catch (RestClientException exception) {
      throw gatewayFailure("PayPal authentication failed: " + exception.getMessage());
    }
  }

  private String text(JsonNode node, String field) {
    if (node == null) {
      return null;
    }
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asString();
  }

  private IllegalStateException gatewayFailure(String message) {
    return new IllegalStateException(
        "PayPal provider request failed", new RuntimeException(message));
  }

  private volatile String cachedToken;
  private volatile Instant tokenExpiresAt = Instant.EPOCH;
}

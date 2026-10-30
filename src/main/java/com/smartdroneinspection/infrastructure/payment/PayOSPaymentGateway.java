package com.smartdroneinspection.infrastructure.payment;

import com.smartdroneinspection.contracts.gateway.PayOSGateway;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * PayOS payment gateway implementation over Spring RestClient.
 *
 * <p>Calls {@code POST /v2/payment-requests} with merchant credentials and HMAC-SHA256 signature,
 * returning authenticated checkout and VietQR parameters.
 */
public class PayOSPaymentGateway implements PayOSGateway {

  private final RestClient client;
  private final PayOSProperties properties;
  private final ObjectMapper objectMapper;

  public PayOSPaymentGateway(
      RestClient client, PayOSProperties properties, ObjectMapper objectMapper) {
    this.client = client;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  public PayOSPaymentLink createPaymentLink(
      long orderCode, long amount, String description, String returnUrl, String cancelUrl) {
    String actualReturnUrl =
        (returnUrl != null && !returnUrl.isBlank()) ? returnUrl : properties.returnUrl();
    String actualCancelUrl =
        (cancelUrl != null && !cancelUrl.isBlank()) ? cancelUrl : properties.cancelUrl();

    if (actualReturnUrl == null || actualReturnUrl.isBlank()) {
      actualReturnUrl = "http://localhost:5173";
    }
    if (actualCancelUrl == null || actualCancelUrl.isBlank()) {
      actualCancelUrl = "http://localhost:5173";
    }

    String cleanDescription =
        (description != null && !description.isBlank()) ? description : ("SDI " + orderCode);
    if (cleanDescription.length() > 25) {
      cleanDescription = cleanDescription.substring(0, 25);
    }

    String signature =
        PayOSCrypto.createPaymentLinkSignature(
            amount,
            actualCancelUrl,
            cleanDescription,
            orderCode,
            actualReturnUrl,
            properties.checksumKey());

    Map<String, Object> request = new LinkedHashMap<>();
    request.put("orderCode", orderCode);
    request.put("amount", amount);
    request.put("description", cleanDescription);
    request.put("cancelUrl", actualCancelUrl);
    request.put("returnUrl", actualReturnUrl);
    request.put("signature", signature);

    JsonNode response;
    try {
      response =
          client
              .post()
              .uri("/v2/payment-requests")
              .contentType(MediaType.APPLICATION_JSON)
              .header("x-client-id", properties.clientId())
              .header("x-api-key", properties.apiKey())
              .body(request)
              .retrieve()
              .body(JsonNode.class);
    } catch (RestClientException exception) {
      throw new IllegalStateException(
          "PayOS payment link request failed: " + exception.getMessage(), exception);
    }

    if (response == null) {
      throw new IllegalStateException("PayOS returned empty response");
    }

    String code = text(response, "code");
    if (!"00".equals(code)) {
      String desc = text(response, "desc");
      throw new IllegalStateException("PayOS returned error code [" + code + "]: " + desc);
    }

    JsonNode data = response.path("data");
    String checkoutUrl = text(data, "checkoutUrl");
    String qrCode = text(data, "qrCode");
    String paymentLinkId = text(data, "paymentLinkId");
    String status = text(data, "status");

    return new PayOSPaymentLink(orderCode, checkoutUrl, qrCode, amount, paymentLinkId, status);
  }

  @Override
  public boolean verifyWebhookSignature(Map<String, Object> data, String receivedSignature) {
    if (properties.checksumKey() == null || properties.checksumKey().isBlank()) {
      return false;
    }
    return PayOSCrypto.verifyWebhookSignature(data, receivedSignature, properties.checksumKey());
  }

  private static String text(JsonNode node, String field) {
    if (node == null) {
      return null;
    }
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asString();
  }
}

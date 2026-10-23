package com.smartdroneinspection.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class PayPalPaymentGatewayTest {

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void sendsWebhookEventAsJsonObjectToPayPalVerificationApi() throws Exception {
    AtomicReference<String> requestBody = new AtomicReference<>();
    startServer(
        exchange -> {
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          respond(exchange, 200, "{\"verification_status\":\"SUCCESS\"}");
        });
    PayPalPaymentGateway gateway = gateway();
    String payload = "{\"id\":\"WH-1\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\"}";

    assertThat(gateway.verifyWebhookSignature(validHeaders(), payload)).isTrue();

    var request = new ObjectMapper().readTree(requestBody.get());
    assertThat(request.path("webhook_event").isObject()).isTrue();
    assertThat(request.path("webhook_event").path("id").asString()).isEqualTo("WH-1");
  }

  @Test
  void doesNotCallProviderForMalformedWebhookPayload() throws Exception {
    AtomicReference<Boolean> called = new AtomicReference<>(false);
    startServer(
        exchange -> {
          called.set(true);
          respond(exchange, 200, "{\"verification_status\":\"SUCCESS\"}");
        });

    assertThat(gateway().verifyWebhookSignature(validHeaders(), "not-json")).isFalse();
    assertThat(called.get()).isFalse();
  }

  private PayPalPaymentGateway gateway() {
    PayPalProperties properties =
        new PayPalProperties(
            true,
            "sandbox",
            "client-id",
            "client-secret",
            "https://api-m.sandbox.paypal.com",
            "https://example.test/return",
            "https://example.test/cancel",
            "webhook-id",
            Duration.ofSeconds(1),
            Duration.ofSeconds(1));
    return new PayPalPaymentGateway(
        RestClient.builder().baseUrl("http://localhost:" + server.getAddress().getPort()).build(),
        properties,
        new ObjectMapper());
  }

  private static Map<String, String> validHeaders() {
    return Map.of(
        "paypal-transmission-id", "transmission-1",
        "paypal-transmission-time", "2026-10-22T10:00:00Z",
        "paypal-transmission-sig", "signature",
        "paypal-cert-url", "https://api-m.sandbox.paypal.com/v1/notifications/cert.pem",
        "paypal-auth-algo", "SHA256withRSA");
  }

  private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/v1/oauth2/token",
        exchange ->
            respond(exchange, 200, "{\"access_token\":\"access-token\",\"expires_in\":300}"));
    server.createContext("/v1/notifications/verify-webhook-signature", handler);
    server.start();
  }

  private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}

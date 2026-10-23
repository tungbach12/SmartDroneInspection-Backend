package com.smartdroneinspection.subscriptions.api;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.gateway.PaymentGateway;
import com.smartdroneinspection.subscriptions.service.PaymentService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Receives PayPal events only after verifying the provider signature. */
@RestController
@RequestMapping("/api/v1/webhooks/paypal")
public class PayPalWebhookController {

  private static final String CAPTURE_COMPLETED = "PAYMENT.CAPTURE.COMPLETED";

  private final PaymentGateway gateway;
  private final PaymentService paymentService;
  private final ObjectMapper objectMapper;

  public PayPalWebhookController(
      PaymentGateway gateway, PaymentService paymentService, ObjectMapper objectMapper) {
    this.gateway = gateway;
    this.paymentService = paymentService;
    this.objectMapper = objectMapper;
  }

  /**
   * Consumes a PayPal webhook. Invalid signatures are rejected without applying its contents.
   *
   * <p>Unknown event types are acknowledged after signature verification, as PayPal retries any
   * delivery that does not receive a 2xx response.
   */
  @PostMapping
  public ResponseEntity<Void> receive(
      @RequestHeader Map<String, String> headers, @RequestBody String rawPayload) {
    Map<String, String> normalizedHeaders = new LinkedHashMap<>();
    headers.forEach((key, value) -> normalizedHeaders.put(key.toLowerCase(Locale.ROOT), value));
    if (!gateway.verifyWebhookSignature(normalizedHeaders, rawPayload)) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "INVALID_PAYMENT_WEBHOOK", "Webhook signature is invalid");
    }

    JsonNode event;
    try {
      event = objectMapper.readTree(rawPayload);
    } catch (Exception exception) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_WEBHOOK", "Webhook body is invalid");
    }
    if (!CAPTURE_COMPLETED.equals(text(event, "event_type"))) {
      return ResponseEntity.noContent().build();
    }

    JsonNode resource = event.path("resource");
    String captureId = text(resource, "id");
    String captureStatus = text(resource, "status");
    JsonNode amountNode = resource.path("amount");
    String amountText = text(amountNode, "value");
    String currency = text(amountNode, "currency_code");
    String orderId = relatedOrderId(resource);

    if (captureId == null || orderId == null || amountText == null || currency == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_PAYMENT_WEBHOOK",
          "Capture event is missing payment fields");
    }
    if (!"COMPLETED".equalsIgnoreCase(captureStatus)) {
      return ResponseEntity.noContent().build();
    }
    if (!"USD".equalsIgnoreCase(currency)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "PAYMENT_AMOUNT_MISMATCH", "Captured currency is not supported");
    }

    BigDecimal amount;
    try {
      amount = new BigDecimal(amountText);
    } catch (NumberFormatException exception) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_WEBHOOK", "Capture amount is invalid");
    }

    paymentService.applyCapturedOrder(orderId, captureId, amount);
    return ResponseEntity.noContent().build();
  }

  private static String relatedOrderId(JsonNode resource) {
    JsonNode supplementaryData = resource.path("supplementary_data").path("related_ids");
    return text(supplementaryData, "order_id");
  }

  private static String text(JsonNode node, String field) {
    if (node == null) {
      return null;
    }
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asString();
  }
}

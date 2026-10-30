package com.smartdroneinspection.contracts.api;

import com.smartdroneinspection.contracts.gateway.PayOSGateway;
import com.smartdroneinspection.contracts.service.ContractWorkflowService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook receiver for automated PayOS VietQR payment confirmations.
 *
 * <p>Receives asynchronous transaction notifications from PayOS, validates the cryptographic
 * HMAC-SHA256 signature, and transitions the corresponding occurrence to PAID and ELIGIBLE for
 * inspector payout.
 */
@RestController
@RequestMapping("/api/v1/webhooks/payos")
public class PayOSWebhookController {

  private final PayOSGateway payOSGateway;
  private final ContractWorkflowService contractWorkflowService;

  public PayOSWebhookController(
      PayOSGateway payOSGateway, ContractWorkflowService contractWorkflowService) {
    this.payOSGateway = payOSGateway;
    this.contractWorkflowService = contractWorkflowService;
  }

  @PostMapping
  public ResponseEntity<Map<String, String>> handleWebhook(
      @RequestBody PayOSWebhookPayload payload) {
    if (payload == null || payload.data() == null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("code", "01", "desc", "Missing webhook payload"));
    }

    if (!payOSGateway.verifyWebhookSignature(payload.data(), payload.signature())) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("code", "01", "desc", "Invalid signature"));
    }

    if ("00".equals(payload.code())) {
      Map<String, Object> data = payload.data();
      long orderCode = parseLong(data.get("orderCode"));
      long amount = parseLong(data.get("amount"));
      String reference = data.get("reference") != null ? data.get("reference").toString() : null;

      // PayOS dashboard webhook validation / handshake ping
      if (orderCode == 123L || "VQRIO123".equals(data.get("description"))) {
        return ResponseEntity.ok(Map.of("code", "00", "desc", "success"));
      }

      contractWorkflowService.applyPayOSWebhookPayment(orderCode, amount, reference);
    }

    return ResponseEntity.ok(Map.of("code", "00", "desc", "success"));
  }

  private static long parseLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String str) {
      return Long.parseLong(str.trim());
    }
    throw new IllegalArgumentException("Expected numeric value but got: " + value);
  }

  public record PayOSWebhookPayload(
      String code, String desc, Map<String, Object> data, String signature) {}
}

package com.smartdroneinspection.contracts.api;

import com.smartdroneinspection.contracts.gateway.VnPayGateway;
import com.smartdroneinspection.contracts.service.ContractWorkflowService;
import com.smartdroneinspection.contracts.service.ContractWorkflowService.VnPayApplyResult;
import com.smartdroneinspection.contracts.service.ContractWorkflowService.VnPayApplyStatus;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook and Return URL receiver for VNPay online payment confirmations (v2.1.0).
 *
 * <p>Validates the cryptographic HMAC-SHA512 signature ({@code vnp_SecureHash}), transitions the
 * corresponding occurrence to PAID and ELIGIBLE for inspector payout, and supports both
 * Server-to-Server IPN and browser Return URL redirection.
 */
@RestController
@RequestMapping("/api/v1/webhooks/vnpay")
public class VnPayWebhookController {

  private final VnPayGateway vnPayGateway;
  private final ContractWorkflowService contractWorkflowService;

  public VnPayWebhookController(
      VnPayGateway vnPayGateway, ContractWorkflowService contractWorkflowService) {
    this.vnPayGateway = vnPayGateway;
    this.contractWorkflowService = contractWorkflowService;
  }

  /**
   * VNPay Server-to-Server IPN endpoint ({@code GET /api/v1/webhooks/vnpay/ipn}).
   *
   * <p>Returns the standard VNPay v2.1.0 JSON response codes: {@code 00} (Confirm Success), {@code
   * 01} (Order not Found), {@code 02} (Order already confirmed), {@code 04} (Invalid Amount), or
   * {@code 97} (Invalid Checksum).
   */
  @GetMapping("/ipn")
  public ResponseEntity<Map<String, String>> handleIpn(@RequestParam Map<String, String> params) {
    String secureHash = params != null ? params.get("vnp_SecureHash") : null;
    if (params == null || params.isEmpty() || !vnPayGateway.verifySecureHash(params, secureHash)) {
      return ResponseEntity.ok(Map.of("RspCode", "97", "Message", "Invalid Checksum"));
    }

    Long orderCode = parseLongOrNull(params.get("vnp_TxnRef"));
    Long rawAmount = parseLongOrNull(params.get("vnp_Amount"));
    if (orderCode == null) {
      return ResponseEntity.ok(Map.of("RspCode", "01", "Message", "Order not Found"));
    }
    if (rawAmount == null || rawAmount <= 0 || rawAmount % 100 != 0) {
      return ResponseEntity.ok(Map.of("RspCode", "04", "Message", "Invalid Amount"));
    }

    long amountVnd = rawAmount / 100L;
    String responseCode = params.get("vnp_ResponseCode");
    String transactionStatus = params.get("vnp_TransactionStatus");
    String transactionNo = params.get("vnp_TransactionNo");

    boolean paymentSuccess =
        "00".equals(responseCode) && (transactionStatus == null || "00".equals(transactionStatus));

    if (!paymentSuccess) {
      if (contractWorkflowService.findInspectionIdByOrderCode(orderCode).isEmpty()) {
        return ResponseEntity.ok(Map.of("RspCode", "01", "Message", "Order not Found"));
      }
      return ResponseEntity.ok(Map.of("RspCode", "00", "Message", "Confirm Success"));
    }

    VnPayApplyResult result =
        contractWorkflowService.applyVnPayPayment(orderCode, amountVnd, transactionNo);

    return switch (result.status()) {
      case NOT_FOUND -> ResponseEntity.ok(Map.of("RspCode", "01", "Message", "Order not Found"));
      case AMOUNT_MISMATCH ->
          ResponseEntity.ok(Map.of("RspCode", "04", "Message", "Invalid Amount"));
      case ALREADY_CONFIRMED ->
          ResponseEntity.ok(Map.of("RspCode", "02", "Message", "Order already confirmed"));
      case CONFIRMED -> ResponseEntity.ok(Map.of("RspCode", "00", "Message", "Confirm Success"));
    };
  }

  /**
   * VNPay Browser Return URL endpoint ({@code GET /api/v1/webhooks/vnpay/return}).
   *
   * <p>Verifies {@code vnp_SecureHash}, applies the payment if successful, and redirects the user's
   * browser back to the Frontend Report Detail page.
   */
  @GetMapping("/return")
  public ResponseEntity<Void> handleReturn(@RequestParam Map<String, String> params) {
    String secureHash = params != null ? params.get("vnp_SecureHash") : null;
    Long orderCode = params != null ? parseLongOrNull(params.get("vnp_TxnRef")) : null;
    Long rawAmount = params != null ? parseLongOrNull(params.get("vnp_Amount")) : null;
    String responseCode = params != null ? params.get("vnp_ResponseCode") : null;
    String transactionStatus = params != null ? params.get("vnp_TransactionStatus") : null;
    String transactionNo = params != null ? params.get("vnp_TransactionNo") : null;

    long resolvedOrderCode = orderCode != null ? orderCode : 0L;
    String inspectionId =
        orderCode != null
            ? contractWorkflowService.findInspectionIdByOrderCode(orderCode).orElse("")
            : "";

    boolean validSignature = params != null && vnPayGateway.verifySecureHash(params, secureHash);
    boolean paymentSuccess =
        validSignature
            && orderCode != null
            && rawAmount != null
            && rawAmount > 0
            && rawAmount % 100 == 0
            && "00".equals(responseCode)
            && (transactionStatus == null || "00".equals(transactionStatus));

    if (paymentSuccess) {
      VnPayApplyResult result =
          contractWorkflowService.applyVnPayPayment(orderCode, rawAmount / 100L, transactionNo);
      if (result.inspectionId() != null && !result.inspectionId().isBlank()) {
        inspectionId = result.inspectionId();
      }
      boolean appliedOk =
          result.status() == VnPayApplyStatus.CONFIRMED
              || result.status() == VnPayApplyStatus.ALREADY_CONFIRMED;
      String redirectUrl =
          vnPayGateway.resolveFrontendReturnUrl(
              inspectionId, resolvedOrderCode, appliedOk, responseCode);
      if (redirectUrl == null || redirectUrl.isBlank()) {
        redirectUrl =
            "http://localhost:3000/client/reports/"
                + inspectionId
                + "?payment="
                + (appliedOk ? "success" : "cancelled")
                + "&gateway=vnpay&orderCode="
                + resolvedOrderCode;
      }
      return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    String redirectUrl =
        vnPayGateway.resolveFrontendReturnUrl(
            inspectionId,
            resolvedOrderCode,
            false,
            validSignature ? responseCode : "invalid_signature");
    if (redirectUrl == null || redirectUrl.isBlank()) {
      redirectUrl =
          "http://localhost:3000/client/reports/"
              + inspectionId
              + "?payment=cancelled&gateway=vnpay&orderCode="
              + resolvedOrderCode;
    }
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
  }

  private static Long parseLongOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}

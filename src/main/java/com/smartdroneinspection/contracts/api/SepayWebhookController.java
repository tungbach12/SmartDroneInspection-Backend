package com.smartdroneinspection.contracts.api;

import com.smartdroneinspection.contracts.domain.GatewayInboundTransaction.Status;
import com.smartdroneinspection.contracts.gateway.SepayGateway;
import com.smartdroneinspection.contracts.service.ContractWorkflowService;
import com.smartdroneinspection.contracts.service.GatewayInboundTransactionService;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook receiver for Sepay bank-transfer payment confirmations.
 *
 * <p>Receives asynchronous transaction notifications from Sepay, verifies the {@code Authorization:
 * Apikey ...} header, and transitions the corresponding occurrence to PAID and ELIGIBLE for
 * inspector payout when the transfer amount matches.
 */
@RestController
@RequestMapping("/api/v1/webhooks/sepay")
public class SepayWebhookController {

  private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("(?i)SDI\\s*(\\d+)");
  private static final Logger log = LoggerFactory.getLogger(SepayWebhookController.class);

  private static final String GATEWAY = "SEPAY";

  private final SepayGateway sepayGateway;
  private final ContractWorkflowService contractWorkflowService;
  private final GatewayInboundTransactionService inbound;

  public SepayWebhookController(
      SepayGateway sepayGateway,
      ContractWorkflowService contractWorkflowService,
      GatewayInboundTransactionService inbound) {
    this.sepayGateway = sepayGateway;
    this.contractWorkflowService = contractWorkflowService;
    this.inbound = inbound;
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> handleWebhook(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestBody(required = false) SepayWebhookPayload payload) {
    if (!sepayGateway.verifyWebhookApiKey(extractApiKey(authorization))) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("success", false, "message", "Invalid API key"));
    }
    if (payload == null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("success", false, "message", "Missing webhook payload"));
    }

    String transferType = payload.transferType();
    if (transferType != null && !"in".equalsIgnoreCase(transferType.trim())) {
      // Outgoing transfers are not payments; acknowledge and ignore.
      return ResponseEntity.ok(Map.of("success", true));
    }

    String externalId = payload.id() != null ? String.valueOf(payload.id()) : null;
    long amount = payload.transferAmount() != null ? payload.transferAmount() : 0L;
    if (inbound.isAlreadyHandled(GATEWAY, externalId)) {
      // Redelivery of a notification that already reached a final outcome.
      return ResponseEntity.ok(Map.of("success", true));
    }

    String expectedAccount = sepayGateway.expectedAccountNumber();
    if (expectedAccount != null && !expectedAccount.isBlank()) {
      String actualAccount = payload.accountNumber() != null ? payload.accountNumber().trim() : "";
      if (!expectedAccount.trim().equalsIgnoreCase(actualAccount)) {
        log.warn(
            "Ignoring Sepay webhook {}: accountNumber '{}' does not match configured receiving account",
            externalId,
            actualAccount);
        record(
            payload,
            externalId,
            null,
            amount,
            Status.REJECTED,
            "ACCOUNT_MISMATCH: received " + actualAccount);
        return ResponseEntity.ok(Map.of("success", true));
      }
    }

    long orderCode = resolveOrderCode(payload);
    if (orderCode <= 0) {
      record(
          payload,
          externalId,
          null,
          amount,
          Status.ORDER_NOT_FOUND,
          "No SDI order code in transfer content");
      return ResponseEntity.ok(Map.of("success", true));
    }
    String reference =
        payload.referenceCode() != null && !payload.referenceCode().isBlank()
            ? payload.referenceCode().trim()
            : ("SEPAY-" + payload.id());

    try {
      contractWorkflowService.applySepayWebhookPayment(orderCode, amount, reference);
      record(payload, externalId, orderCode, amount, Status.APPLIED, null);
    } catch (BusinessException ex) {
      log.warn(
          "Rejected Sepay webhook for orderCode {}: {} - {}",
          orderCode,
          ex.code(),
          ex.getMessage());
      Status status =
          switch (ex.code()) {
            case "PAYMENT_AMOUNT_MISMATCH" -> Status.AMOUNT_MISMATCH;
            case "OCCURRENCE_NOT_FOUND" -> Status.ORDER_NOT_FOUND;
            default -> Status.REJECTED;
          };
      record(payload, externalId, orderCode, amount, status, ex.code() + ": " + ex.getMessage());
      if (status == Status.REJECTED) {
        return ResponseEntity.status(ex.status())
            .body(Map.of("success", false, "message", ex.code()));
      }
      // The transfer is stored for manual reconciliation; retrying cannot change the outcome.
      return ResponseEntity.ok(Map.of("success", true));
    } catch (RuntimeException ex) {
      log.error("Unexpected error applying Sepay webhook for orderCode {}", orderCode, ex);
      record(payload, externalId, orderCode, amount, Status.ERROR, ex.getClass().getSimpleName());
      // A non-2xx response makes Sepay redeliver, so a transient failure cannot lose the payment.
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("success", false, "message", "INTERNAL_ERROR"));
    }
    return ResponseEntity.ok(Map.of("success", true));
  }

  private void record(
      SepayWebhookPayload payload,
      String externalId,
      Long orderCode,
      long amount,
      Status status,
      String detail) {
    try {
      inbound.record(
          GATEWAY,
          externalId,
          orderCode,
          amount,
          payload.accountNumber(),
          payload.toString(),
          status,
          detail);
    } catch (RuntimeException ex) {
      log.error("Failed to record Sepay inbound transaction {}", externalId, ex);
    }
  }

  private static String extractApiKey(String authorization) {
    if (authorization == null) {
      return null;
    }
    String trimmed = authorization.trim();
    if (trimmed.regionMatches(true, 0, "Apikey ", 0, "Apikey ".length())) {
      return trimmed.substring("Apikey ".length()).trim();
    }
    return trimmed;
  }

  private static long resolveOrderCode(SepayWebhookPayload payload) {
    // Sepay's `code` field is its own payment code (for example SEVN63DC8E5C), not the occurrence
    // order code. The order code only exists inside the transfer description we generate as
    // SDI<orderCode>, so every candidate is matched against that pattern instead of being parsed
    // as a bare number.
    for (String candidate :
        new String[] {payload.content(), payload.description(), payload.code()}) {
      if (candidate == null) {
        continue;
      }
      Matcher matcher = ORDER_CODE_PATTERN.matcher(candidate);
      if (matcher.find()) {
        try {
          return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
          // keep looking in the remaining fields
        }
      }
    }
    return -1L;
  }

  public record SepayWebhookPayload(
      Long id,
      String gateway,
      String transactionDate,
      String accountNumber,
      String subAccount,
      String code,
      String content,
      String transferType,
      String description,
      Long transferAmount,
      Long accumulated,
      String referenceCode) {}
}

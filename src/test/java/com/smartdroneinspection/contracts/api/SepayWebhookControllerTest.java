package com.smartdroneinspection.contracts.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.contracts.domain.GatewayInboundTransaction.Status;
import com.smartdroneinspection.contracts.gateway.SepayGateway;
import com.smartdroneinspection.contracts.service.ContractWorkflowService;
import com.smartdroneinspection.contracts.service.GatewayInboundTransactionService;
import com.smartdroneinspection.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Covers the Sepay webhook contract: API-key auth, transfer filtering, and order-code matching. */
class SepayWebhookControllerTest {

  private static final String API_KEY = "test-sepay-key";

  private SepayGateway sepayGateway;
  private ContractWorkflowService workflowService;
  private GatewayInboundTransactionService inbound;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    sepayGateway = mock(SepayGateway.class);
    workflowService = mock(ContractWorkflowService.class);
    inbound = mock(GatewayInboundTransactionService.class);
    when(sepayGateway.verifyWebhookApiKey(API_KEY)).thenReturn(true);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new SepayWebhookController(sepayGateway, workflowService, inbound))
            .build();
  }

  @Test
  @DisplayName("rejects a request without a matching Apikey header")
  void rejectsInvalidApiKey() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("in", "SEVN63DC8E5C", "SDI123", 500)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false));

    verify(workflowService, never()).applySepayWebhookPayment(anyLong(), anyLong(), anyString());
  }

  @Test
  @DisplayName("settles the occurrence whose code appears in the transfer content")
  void settlesFromTransferContent() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey " + API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("in", "SEVN63DC8E5C", "SDI123456789 chuyen tien", 500000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(workflowService).applySepayWebhookPayment(eq(123456789L), eq(500000L), anyString());
  }

  @Test
  @DisplayName("does not treat a bare numeric Sepay code as an occurrence order code")
  void ignoresNumericSepayCodeWithoutSdiPrefix() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey " + API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("in", "123456789", "chuyen tien", 500000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(workflowService, never()).applySepayWebhookPayment(anyLong(), anyLong(), anyString());
  }

  @Test
  @DisplayName("acknowledges outgoing transfers without settling anything")
  void ignoresOutgoingTransfer() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey " + API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("out", "SEVN63DC8E5C", "SDI123456789", 500000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(workflowService, never()).applySepayWebhookPayment(anyLong(), anyLong(), anyString());
  }

  @Test
  @DisplayName("stores an unknown order code for reconciliation and acknowledges it")
  void recordsUnknownOrderCode() throws Exception {
    doThrow(
            new BusinessException(
                HttpStatus.NOT_FOUND, "OCCURRENCE_NOT_FOUND", "Occurrence not found"))
        .when(workflowService)
        .applySepayWebhookPayment(eq(987654321L), anyLong(), anyString());

    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey " + API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("in", "SEVN63DC8E5C", "SDI987654321", 500000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verifyRecorded(Status.ORDER_NOT_FOUND);
  }

  @Test
  @DisplayName("stores an amount mismatch for reconciliation and acknowledges it")
  void recordsAmountMismatch() throws Exception {
    doThrow(
            new BusinessException(
                HttpStatus.BAD_REQUEST, "PAYMENT_AMOUNT_MISMATCH", "Amount does not match"))
        .when(workflowService)
        .applySepayWebhookPayment(eq(123456789L), anyLong(), anyString());

    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey " + API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("in", "SEVN63DC8E5C", "SDI123456789", 1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verifyRecorded(Status.AMOUNT_MISMATCH);
  }

  @Test
  @DisplayName("answers 500 on a system failure so Sepay redelivers the transfer")
  void failsWithServerErrorOnSystemFailure() throws Exception {
    doThrow(new IllegalStateException("database down"))
        .when(workflowService)
        .applySepayWebhookPayment(eq(123456789L), anyLong(), anyString());

    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey " + API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("in", "SEVN63DC8E5C", "SDI123456789", 500000)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.success").value(false));

    verifyRecorded(Status.ERROR);
  }

  @Test
  @DisplayName("records an applied transfer once")
  void recordsAppliedTransfer() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey " + API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("in", "SEVN63DC8E5C", "SDI123456789", 500000)))
        .andExpect(status().isOk());

    verifyRecorded(Status.APPLIED);
  }

  @Test
  @DisplayName("does not apply a redelivered transfer that was already handled")
  void ignoresRedeliveredTransfer() throws Exception {
    when(inbound.isAlreadyHandled("SEPAY", "92704")).thenReturn(true);

    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey " + API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("in", "SEVN63DC8E5C", "SDI123456789", 500000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(workflowService, never()).applySepayWebhookPayment(anyLong(), anyLong(), anyString());
    verify(inbound, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("rejects a transfer credited to a different bank account number")
  void rejectsMismatchedReceivingAccount() throws Exception {
    when(sepayGateway.expectedAccountNumber()).thenReturn("07723264801");

    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey " + API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("in", "SEVN63DC8E5C", "SDI123456789", 500000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(workflowService, never()).applySepayWebhookPayment(anyLong(), anyLong(), anyString());
    verifyRecorded(Status.REJECTED);
  }

  @Test
  @DisplayName("matches lowercase SDI prefix with spaces inserted by bank transfer description")
  void settlesFromLowercaseSpacedTransferContent() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey " + API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    payload("in", "SEVN63DC8E5C", "MBVCB.12345 sdi 123456789 thanh toan", 500000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(workflowService).applySepayWebhookPayment(eq(123456789L), eq(500000L), anyString());
  }

  private void verifyRecorded(Status expected) {
    verify(inbound)
        .record(eq("SEPAY"), eq("92704"), any(), any(), any(), any(), eq(expected), any());
  }

  private static String payload(
      String transferType, String code, String content, long transferAmount) {
    return """
        {
          "id": 92704,
          "gateway": "Vietcombank",
          "transactionDate": "2026-11-21 11:08:33",
          "accountNumber": "1017588888",
          "subAccount": "",
          "code": "%s",
          "content": "%s",
          "transferType": "%s",
          "description": "NGUYEN VAN A chuyen tien",
          "transferAmount": %d,
          "accumulated": 105000000,
          "referenceCode": "FT24012345678"
        }
        """
        .formatted(code, content, transferType, transferAmount);
  }
}

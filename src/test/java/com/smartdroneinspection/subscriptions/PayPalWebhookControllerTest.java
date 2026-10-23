package com.smartdroneinspection.subscriptions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.exception.GlobalExceptionHandler;
import com.smartdroneinspection.subscriptions.api.PayPalWebhookController;
import com.smartdroneinspection.subscriptions.gateway.PaymentGateway;
import com.smartdroneinspection.subscriptions.service.PaymentService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PayPalWebhookControllerTest {

  @Mock PaymentGateway gateway;
  @Mock PaymentService paymentService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new PayPalWebhookController(gateway, paymentService, new ObjectMapper()))
            .setControllerAdvice(
                new GlobalExceptionHandler(
                    new com.smartdroneinspection.shared.exception.ProblemText(
                        new org.springframework.context.support.StaticMessageSource())))
            .build();
  }

  @Test
  void rejectsInvalidSignatureWithoutApplyingSettlement() throws Exception {
    when(gateway.verifyWebhookSignature(any(), any())).thenReturn(false);

    mockMvc
        .perform(
            post("/api/v1/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());

    verify(paymentService, never()).applyCapturedOrder(any(), any(), any());
  }

  @Test
  void appliesVerifiedCompletedCapture() throws Exception {
    when(gateway.verifyWebhookSignature(any(), any())).thenReturn(true);
    String payload =
        "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"resource\":{"
            + "\"id\":\"CAPTURE-1\",\"status\":\"COMPLETED\","
            + "\"amount\":{\"value\":\"10200.00\",\"currency_code\":\"USD\"},"
            + "\"supplementary_data\":{\"related_ids\":{\"order_id\":\"ORDER-1\"}}}}";

    mockMvc
        .perform(
            post("/api/v1/webhooks/paypal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isNoContent());

    verify(paymentService).applyCapturedOrder("ORDER-1", "CAPTURE-1", new BigDecimal("10200.00"));
  }

  @Test
  void acknowledgesVerifiedUnrelatedEventWithoutSettlement() throws Exception {
    when(gateway.verifyWebhookSignature(any(), any())).thenReturn(true);

    mockMvc
        .perform(
            post("/api/v1/webhooks/paypal")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event_type\":\"CHECKOUT.ORDER.APPROVED\"}"))
        .andExpect(status().isNoContent());

    verify(paymentService, never()).applyCapturedOrder(any(), any(), any());
  }

  @Test
  void rejectsMalformedWebhookBodyWithoutSettlement() throws Exception {
    when(gateway.verifyWebhookSignature(any(), any())).thenReturn(true);

    mockMvc
        .perform(
            post("/api/v1/webhooks/paypal")
                .contentType(MediaType.APPLICATION_JSON)
                .content("not-json"))
        .andExpect(status().isBadRequest());

    verify(paymentService, never()).applyCapturedOrder(any(), any(), any());
  }

  @Test
  void mapsUnknownPendingOrderToNotFound() throws Exception {
    when(gateway.verifyWebhookSignature(any(), any())).thenReturn(true);
    org.mockito.Mockito.doThrow(
            new BusinessException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "No order"))
        .when(paymentService)
        .applyCapturedOrder(eq("ORDER-1"), eq("CAPTURE-1"), eq(new BigDecimal("10200.00")));
    String payload =
        "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"resource\":{"
            + "\"id\":\"CAPTURE-1\",\"status\":\"COMPLETED\","
            + "\"amount\":{\"value\":\"10200.00\",\"currency_code\":\"USD\"},"
            + "\"supplementary_data\":{\"related_ids\":{\"order_id\":\"ORDER-1\"}}}}";

    mockMvc
        .perform(
            post("/api/v1/webhooks/paypal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isNotFound());
  }
}

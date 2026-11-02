package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.contracts.api.VnPayWebhookController;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePayoutStatus;
import com.smartdroneinspection.contracts.gateway.VnPayGateway;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.infrastructure.payment.VnPayCrypto;
import com.smartdroneinspection.infrastructure.payment.VnPayPaymentGateway;
import com.smartdroneinspection.infrastructure.payment.VnPayProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VnPayWebhookAndGatewayTest {

  private static final String TEST_SECRET = "VNPAY_SANDBOX_SECRET_KEY_1234567890";

  private ServiceRequestOccurrenceRepository occurrences;
  private VnPayPaymentGateway vnPayGateway;
  private ContractWorkflowService workflowService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    occurrences = mock(ServiceRequestOccurrenceRepository.class);
    VnPayProperties props =
        new VnPayProperties(
            true,
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
            "DEMOTMN1",
            TEST_SECRET,
            "http://localhost:8080/api/v1/webhooks/vnpay/return",
            "http://localhost:3000/client/reports/{inspectionId}",
            "2.1.0",
            "pay",
            "other",
            "VND",
            "vn",
            15);
    vnPayGateway = new VnPayPaymentGateway(props);
    workflowService =
        new ContractWorkflowService(
            null,
            null,
            null,
            occurrences,
            null,
            null,
            null,
            null,
            null,
            Optional.empty(),
            null,
            Optional.empty(),
            null,
            vnPayGateway);
    VnPayWebhookController controller = new VnPayWebhookController(vnPayGateway, workflowService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void createPaymentUrlProducesValidSignedSandboxUrlAndNormalizesIpv6() {
    VnPayGateway.VnPayPaymentLink link =
        vnPayGateway.createPaymentUrl(
            100001L, 5000000L, "Thanh toan SDI OCC 1", "0:0:0:0:0:0:0:1", null);

    assertThat(link.orderCode()).isEqualTo(100001L);
    assertThat(link.amount()).isEqualTo(5000000L);
    assertThat(link.txnRef()).isEqualTo("100001");
    assertThat(link.checkoutUrl())
        .startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?")
        .contains("vnp_Amount=500000000")
        .contains("vnp_TmnCode=DEMOTMN1")
        .contains("vnp_IpAddr=127.0.0.1")
        .contains("&vnp_SecureHash=");
  }

  @Test
  void ipnAndReturnSettleOccurrencePaymentWithValidHmacSha512Signature() throws Exception {
    UUID requestId = UUID.randomUUID();
    UUID inspectionId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID reportVersionId = UUID.randomUUID();
    long orderCode = 100005L;
    long agreedVnd = 5000000L;

    Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(requestId, 1, start, start.plus(2, ChronoUnit.HOURS));
    occurrence.assign(inspectorId, UUID.randomUUID(), inspectionId, UUID.randomUUID());
    occurrence.snapshotFinance(
        "VND",
        BigDecimal.valueOf(agreedVnd),
        BigDecimal.valueOf(4545455),
        BigDecimal.valueOf(10),
        BigDecimal.valueOf(454545),
        BigDecimal.valueOf(15),
        BigDecimal.valueOf(681818),
        BigDecimal.valueOf(3863637),
        BigDecimal.valueOf(10),
        BigDecimal.valueOf(386364),
        BigDecimal.valueOf(3477273));
    occurrence.acceptReport(reportVersionId, customerId);
    occurrence.assignVnPayOrder(orderCode);

    when(occurrences.findWithLockByPayosOrderCode(orderCode)).thenReturn(Optional.of(occurrence));
    when(occurrences.findByPayosOrderCode(orderCode)).thenReturn(Optional.of(occurrence));

    Map<String, String> vnpParams = new LinkedHashMap<>();
    vnpParams.put("vnp_TxnRef", String.valueOf(orderCode));
    vnpParams.put("vnp_Amount", String.valueOf(agreedVnd * 100L));
    vnpParams.put("vnp_ResponseCode", "00");
    vnpParams.put("vnp_TransactionStatus", "00");
    vnpParams.put("vnp_TransactionNo", "14998877");
    vnpParams.put("vnp_BankCode", "NCB");

    String query = VnPayCrypto.buildSignedQueryString(vnpParams, TEST_SECRET);
    String validHash = query.substring(query.indexOf("&vnp_SecureHash=") + 16);

    // 1. Invalid signature is rejected with RspCode 97
    mockMvc
        .perform(
            get("/api/v1/webhooks/vnpay/ipn")
                .param("vnp_TxnRef", String.valueOf(orderCode))
                .param("vnp_Amount", String.valueOf(agreedVnd * 100L))
                .param("vnp_ResponseCode", "00")
                .param("vnp_SecureHash", "forged_hash"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("97"));

    // 2. Valid Return URL settles occurrence and redirects 302 to frontend report page
    mockMvc
        .perform(
            get("/api/v1/webhooks/vnpay/return")
                .param("vnp_TxnRef", String.valueOf(orderCode))
                .param("vnp_Amount", String.valueOf(agreedVnd * 100L))
                .param("vnp_ResponseCode", "00")
                .param("vnp_TransactionStatus", "00")
                .param("vnp_TransactionNo", "14998877")
                .param("vnp_BankCode", "NCB")
                .param("vnp_SecureHash", validHash))
        .andExpect(status().isFound())
        .andExpect(
            header()
                .string(
                    "Location",
                    "http://localhost:3000/client/reports/"
                        + inspectionId
                        + "?payment=success&gateway=vnpay&orderCode="
                        + orderCode));

    assertThat(occurrence.getPaymentStatus()).isEqualTo(OccurrencePaymentStatus.PAID);
    assertThat(occurrence.getPayoutStatus()).isEqualTo(OccurrencePayoutStatus.PAID);
    assertThat(occurrence.getPaymentMethod()).isEqualTo("VNPAY");
    assertThat(occurrence.getPaymentReference()).isEqualTo("VNPAY-14998877");

    // 3. Duplicate IPN returns RspCode 02 (Order already confirmed)
    mockMvc
        .perform(
            get("/api/v1/webhooks/vnpay/ipn")
                .param("vnp_TxnRef", String.valueOf(orderCode))
                .param("vnp_Amount", String.valueOf(agreedVnd * 100L))
                .param("vnp_ResponseCode", "00")
                .param("vnp_TransactionStatus", "00")
                .param("vnp_TransactionNo", "14998877")
                .param("vnp_BankCode", "NCB")
                .param("vnp_SecureHash", validHash))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("02"));
  }
}

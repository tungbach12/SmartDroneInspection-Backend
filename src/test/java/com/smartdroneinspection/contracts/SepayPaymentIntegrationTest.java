package com.smartdroneinspection.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePayoutStatus;
import com.smartdroneinspection.contracts.gateway.SepayGateway;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "app.payment.sepay.enabled=true",
      "app.payment.sepay.api-key=test-sepay-api-key",
      "app.payment.sepay.account-number=0123456789",
      "app.payment.sepay.bank-name=MB",
      "app.payment.sepay.account-holder=TEST HOLDER"
    })
@Import(TestcontainersConfiguration.class)
class SepayPaymentIntegrationTest {

  private static final byte[] VALID_PNG_BYTES =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserRepository users;
  @Autowired AssetCategoryRepository categories;
  @Autowired AssetRepository assets;
  @Autowired DroneRepository drones;
  @Autowired ServiceRequestRepository requests;
  @Autowired ServiceRequestOccurrenceRepository occurrences;
  @Autowired InspectionRepository inspections;
  @Autowired InspectionReportRepository reports;
  @Autowired JdbcTemplate jdbcTemplate;

  @MockitoBean SepayGateway sepayGateway;

  private MockMvc mockMvc;

  private UUID customerUserId;
  private UUID otherCustomerUserId;
  private UUID managerUserId;
  private UUID adminUserId;
  private UUID inspectorUserId;
  private UUID droneId;
  private UUID assetId;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    // Mock Sepay gateway behavior
    when(sepayGateway.createPaymentLink(anyLong(), anyLong(), anyString(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              long orderCode = invocation.getArgument(0);
              long amount = invocation.getArgument(1);
              return new SepayGateway.SepayPaymentLink(
                  orderCode,
                  "https://vietqr.app/img?acc=0123456789&bank=MB&amount="
                      + amount
                      + "&des=SDI"
                      + orderCode,
                  "SDI" + orderCode,
                  amount,
                  String.valueOf(orderCode),
                  "PENDING");
            });

    when(sepayGateway.createPayoutLink(anyLong(), anyLong(), anyString(), anyString(), any()))
        .thenAnswer(
            invocation -> {
              long orderCode = invocation.getArgument(0);
              long amount = invocation.getArgument(1);
              String acc = invocation.getArgument(2);
              String bank = invocation.getArgument(3);
              return new SepayGateway.SepayPaymentLink(
                  orderCode,
                  "https://vietqr.app/img?acc="
                      + acc
                      + "&bank="
                      + bank
                      + "&amount="
                      + amount
                      + "&des=SDIPAY"
                      + orderCode,
                  "SDIPAY" + orderCode,
                  amount,
                  String.valueOf(orderCode),
                  "PENDING");
            });

    when(sepayGateway.verifyWebhookApiKey(any()))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              return "test-sepay-api-key".equals(key);
            });

    // Clean tables
    jdbcTemplate.execute("DELETE FROM inspection_finance_settings");
    jdbcTemplate.execute("DELETE FROM service_request_occurrences");
    jdbcTemplate.execute("DELETE FROM service_requests");
    jdbcTemplate.execute("DELETE FROM service_request_quotes");

    // Users
    managerUserId =
        createPlatformUser(
            UserRole.MANAGER,
            "manager." + UUID.randomUUID() + "@platform.internal",
            "Manager User");

    adminUserId =
        createPlatformUser(
            UserRole.ADMIN, "admin." + UUID.randomUUID() + "@platform.internal", "Admin User");

    try {
      mockMvc
          .perform(
              put("/api/v1/contracts/admin/finance-settings")
                  .with(principal(adminUserId, "ADMIN"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of("commissionRate", 15.00, "vatRate", 10.00, "tncnRate", 10.00))))
          .andExpect(status().isOk());
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

    customerUserId =
        createUser(
            UserRole.CUSTOMER, "customer." + UUID.randomUUID() + "@sepay.test", "Customer Primary");

    otherCustomerUserId =
        createUser(
            UserRole.CUSTOMER, "other." + UUID.randomUUID() + "@other.test", "Customer Other Org");

    inspectorUserId =
        createUser(
            UserRole.INSPECTOR,
            "inspector." + UUID.randomUUID() + "@partner.test",
            "Partner Pilot");

    jdbcTemplate.update(
        "INSERT INTO inspector_profiles (user_id, pilot_license_number, pilot_license_class, pilot_license_expires_at, pilot_license_file_key, payout_bank_name, payout_bank_account_number, payout_bank_account_holder) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        inspectorUserId,
        "VN-UAV-PILOT-2026",
        "CLASS_B",
        java.sql.Timestamp.from(Instant.now().plus(365, ChronoUnit.DAYS)),
        "licenses/pilot-cert.pdf",
        "MB Bank",
        "0987654321",
        "PARTNER PILOT");

    // Register Inspector in Partner Registry
    try {
      mockMvc
          .perform(
              post("/api/v1/contracts/partners/inspectors")
                  .with(principal(managerUserId, "MANAGER"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(Map.of("inspectorUserId", inspectorUserId))))
          .andExpect(status().isCreated());
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

    // Drone
    Drone drone =
        drones.saveAndFlush(
            Drone.createInspectorOwned(
                inspectorUserId,
                "DJI-M350-" + UUID.randomUUID().toString().substring(0, 8),
                "Matrice 350 RTK",
                "DJI",
                "REG-VN-350",
                "drones/registration.pdf",
                Instant.now().plus(365, ChronoUnit.DAYS)));
    droneId = drone.getId();

    try {
      mockMvc
          .perform(
              post("/api/v1/contracts/partners/drones")
                  .with(principal(managerUserId, "MANAGER"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("droneId", droneId))))
          .andExpect(status().isCreated());
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

    // Asset
    AssetCategory category =
        categories.saveAndFlush(
            new AssetCategory(
                "cat-" + UUID.randomUUID().toString().substring(0, 8),
                "Telecom Towers",
                "Telecommunication cellular towers",
                true));

    Asset asset =
        assets.saveAndFlush(
            new Asset(
                customerUserId,
                category.getId(),
                "TOWER-" + UUID.randomUUID().toString().substring(0, 8),
                "Tower Alpha",
                "Primary broadcasting tower",
                "{\"latitude\":10.7769,\"longitude\":106.7009,\"city\":\"Ho Chi Minh City\"}",
                null,
                null,
                null,
                List.of("Section 1", "Section 2"),
                customerUserId));
    assetId = asset.getId();
  }

  @Test
  @DisplayName(
      "Full Sepay Lifecycle: Create Link, Webhook Settlement, Idempotency, and Deliverable Unblocking")
  void testFullSepayLifecycle() throws Exception {
    // 1. Setup Service Request through acceptance and report publishing
    PreparedInspection prepared = prepareInspectionReadyForPayment();
    UUID requestId = prepared.requestId();
    UUID occurrenceId = prepared.occurrenceId();
    UUID inspectionId = prepared.inspectionId();
    UUID reportVersionId = prepared.reportVersionId();
    long agreedPrice = 5000000L; // Quote price is inclusive of VAT

    // 2. Deliverable PDF export is available immediately upon report publication (not locked)
    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{vId}/document",
                    inspectionId,
                    reportVersionId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isOk());

    // 3. Unauthorized org cannot access service request (404 Not Found per multi-tenant isolation)
    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/sepay/create-payment-link",
                    requestId,
                    occurrenceId)
                .with(principal(otherCustomerUserId, "CUSTOMER")))
        .andExpect(status().isNotFound());

    // 4. Customer creates Sepay payment link
    String linkResponse =
        mockMvc
            .perform(
                post(
                        "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/sepay/create-payment-link",
                        requestId,
                        occurrenceId)
                    .with(principal(customerUserId, "CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderCode").isNumber())
            .andExpect(jsonPath("$.data.amount").value(agreedPrice))
            .andExpect(jsonPath("$.data.checkoutUrl").isNotEmpty())
            .andExpect(jsonPath("$.data.qrCode").isNotEmpty())
            .andExpect(jsonPath("$.data.paymentLinkId").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    long allocatedOrderCode = objectMapper.readTree(linkResponse).at("/data/orderCode").longValue();
    assertThat(allocatedOrderCode).isGreaterThanOrEqualTo(100001L);

    // Verify occurrence has been assigned orderCode and method SEPAY
    ServiceRequestOccurrence occAfterLink = occurrences.findById(occurrenceId).orElseThrow();
    assertThat(occAfterLink.getGatewayOrderCode()).isEqualTo(allocatedOrderCode);
    assertThat(occAfterLink.getPaymentMethod()).isEqualTo("SEPAY");
    assertThat(occAfterLink.getPaymentStatus()).isEqualTo(OccurrencePaymentStatus.PAYMENT_DUE);

    // 5. Webhook: Rejects invalid API key with 401
    Map<String, Object> validSepayPayload = new LinkedHashMap<>();
    validSepayPayload.put("id", 1L);
    validSepayPayload.put("gateway", "MB");
    validSepayPayload.put("transactionDate", "2027-03-01 10:00:00");
    validSepayPayload.put("accountNumber", "0123456789");
    validSepayPayload.put("subAccount", null);
    validSepayPayload.put("code", String.valueOf(allocatedOrderCode));
    validSepayPayload.put("content", "SDI" + allocatedOrderCode);
    validSepayPayload.put("transferType", "in");
    validSepayPayload.put("description", "SDI" + allocatedOrderCode);
    validSepayPayload.put("transferAmount", agreedPrice);
    validSepayPayload.put("accumulated", 0L);
    validSepayPayload.put("referenceCode", "SEPAY-TXN-12345");

    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validSepayPayload)))
        .andExpect(status().isUnauthorized());

    // Occurrence remains PAYMENT_DUE
    assertThat(occurrences.findById(occurrenceId).orElseThrow().getPaymentStatus())
        .isEqualTo(OccurrencePaymentStatus.PAYMENT_DUE);

    // 6. Webhook: amount mismatch is acknowledged (200) and recorded for manual reconciliation
    // rather than triggering a Sepay redelivery, which could never change the outcome.
    Map<String, Object> mismatchPayload = new LinkedHashMap<>(validSepayPayload);
    mismatchPayload.put("id", 2L);
    mismatchPayload.put("transferAmount", 1000000L); // mismatch!
    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey test-sepay-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mismatchPayload)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    // The mismatch is audited in the inbound ledger and the occurrence stays unpaid.
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from gateway_inbound_transactions"
                    + " where gateway = 'SEPAY' and external_id = '2'",
                String.class))
        .isEqualTo("AMOUNT_MISMATCH");
    assertThat(occurrences.findById(occurrenceId).orElseThrow().getPaymentStatus())
        .isEqualTo(OccurrencePaymentStatus.PAYMENT_DUE);

    // 7. Webhook: valid API key settles payment and marks occurrence PAID
    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey test-sepay-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validSepayPayload)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    // Customer payment settles; Inspector payout remains PAID with the pre-publish settlement
    // reference.
    ServiceRequestOccurrence settledOcc = occurrences.findById(occurrenceId).orElseThrow();
    assertThat(settledOcc.getPaymentStatus()).isEqualTo(OccurrencePaymentStatus.PAID);
    assertThat(settledOcc.getPayoutStatus()).isEqualTo(OccurrencePayoutStatus.PAID);
    assertThat(settledOcc.getPayoutReference()).startsWith("UNC-SEPAY-");
    assertThat(settledOcc.getPaymentReference()).isEqualTo("SEPAY-TXN-12345");
    assertThat(settledOcc.getPaymentConfirmedAt()).isNotNull();
    assertThat(settledOcc.getPayout().getInspectorId()).isEqualTo(inspectorUserId);

    // 8. Idempotency: Duplicate webhook delivery is a safe no-op returning 200 OK
    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey test-sepay-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validSepayPayload)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    // 9. Deliverable download is unblocked! Customer can download report PDF now
    // 9. Deliverable download is unblocked! Customer can download report PDF now
    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{vId}/document",
                    inspectionId,
                    reportVersionId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Cannot create Sepay payment link before report acceptance (PAYMENT_NOT_DUE)")
  void cannotCreatePaymentLinkBeforeAcceptance() throws Exception {
    ZoneId utc = ZoneId.of("UTC");
    LocalDate reqDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(20);
    Instant start = reqDate.atTime(9, 0).atZone(utc).toInstant();
    Instant end = start.plus(2, ChronoUnit.HOURS);

    String reqResp =
        mockMvc
            .perform(
                post("/api/v1/contracts/requests")
                    .with(principal(customerUserId, "CUSTOMER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "assetId",
                                assetId,
                                "description",
                                "Sepay E2E Pre-Acceptance Test",
                                "requestedFrom",
                                reqDate,
                                "requestedUntil",
                                reqDate,
                                "timezone",
                                "UTC",
                                "requestType",
                                "ONE_TIME"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID requestId = UUID.fromString(objectMapper.readTree(reqResp).at("/data/id").stringValue());

    mockMvc
        .perform(
            post("/api/v1/contracts/requests/{id}/claim", requestId)
                .with(principal(managerUserId, "MANAGER")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/contracts/requests/{id}/quotes", requestId)
                .with(principal(managerUserId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "scopeSnapshot", "Visual check",
                            "firstOccurrenceStartAt", start,
                            "firstOccurrenceEndAt", end,
                            "inspectorId", inspectorUserId,
                            "droneId", droneId,
                            "pricePerOccurrenceVnd", 2000000.00))))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/contracts/requests/{id}/accept", requestId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isOk());

    UUID occurrenceId =
        occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId).getFirst().getId();

    // Occurrence payment status is PENDING (report not accepted yet)
    // Trying to create payment link must return 409 PAYMENT_NOT_DUE
    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/sepay/create-payment-link",
                    requestId,
                    occurrenceId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAYMENT_NOT_DUE"));
  }

  @Test
  @DisplayName("Automatic SePay payout QR and outgoing webhook settle payout and unlock publish")
  void testSepayAutomaticPayoutAndPublish() throws Exception {
    PreparedInspection prepared = prepareInspectionReadyForPayment(true);
    UUID requestId = prepared.requestId();
    UUID occurrenceId = prepared.occurrenceId();
    UUID inspectionId = prepared.inspectionId();
    UUID reportVersionId = prepared.reportVersionId();

    // Create SePay VietQR payout link (auto-targets inspector bank details)
    String payoutResp =
        mockMvc
            .perform(
                post("/api/v1/inspections/{inspectionId}/sepay/create-payout-link", inspectionId)
                    .with(principal(managerUserId, "MANAGER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderCode").isNumber())
            .andExpect(jsonPath("$.data.qrCode").value(Matchers.startsWith("SDIPAY")))
            .andExpect(
                jsonPath("$.data.checkoutUrl").value(Matchers.containsString("acc=0987654321")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    long payoutOrderCode = objectMapper.readTree(payoutResp).at("/data/orderCode").longValue();
    long payoutAmount = objectMapper.readTree(payoutResp).at("/data/amount").longValue();

    // Publishing is still blocked before outgoing SePay webhook arrives
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vId}/publish",
                    inspectionId,
                    reportVersionId)
                .with(principal(managerUserId, "MANAGER")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSPECTOR_PAYOUT_REQUIRED"));

    // Simulate SePay outgoing transfer webhook (transferType = out)
    Map<String, Object> outgoingWebhook = new LinkedHashMap<>();
    outgoingWebhook.put("id", 777001L);
    outgoingWebhook.put("gateway", "TPBank");
    outgoingWebhook.put("transactionDate", "2027-03-01 09:30:00");
    outgoingWebhook.put("accountNumber", "07723264801");
    outgoingWebhook.put("subAccount", "");
    outgoingWebhook.put("code", "SEPAYOUT777");
    outgoingWebhook.put("content", "SDIPAY" + payoutOrderCode + " chi tra inspector");
    outgoingWebhook.put("transferType", "out");
    outgoingWebhook.put("description", "Platform outgoing payout");
    outgoingWebhook.put("transferAmount", payoutAmount);
    outgoingWebhook.put("accumulated", 50000000L);
    outgoingWebhook.put("referenceCode", "FT-SEPAY-OUT-777");

    mockMvc
        .perform(
            post("/api/v1/webhooks/sepay")
                .header("Authorization", "Apikey test-sepay-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(outgoingWebhook)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    ServiceRequestOccurrence settled = occurrences.findById(occurrenceId).orElseThrow();
    assertThat(settled.getPayoutStatus()).isEqualTo(OccurrencePayoutStatus.PAID);
    assertThat(settled.getPayoutReference()).isEqualTo("FT-SEPAY-OUT-777");

    // Publish to Customer succeeds without any manual UNC entry
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vId}/publish",
                    inspectionId,
                    reportVersionId)
                .with(principal(managerUserId, "MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
  }

  private record PreparedInspection(
      UUID requestId, UUID occurrenceId, UUID inspectionId, UUID reportVersionId) {}

  private PreparedInspection prepareInspectionReadyForPayment() throws Exception {
    return prepareInspectionReadyForPayment(false);
  }

  private PreparedInspection prepareInspectionReadyForPayment(boolean stopAtApproved)
      throws Exception {
    ZoneId utc = ZoneId.of("UTC");
    LocalDate reqDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(20);
    Instant start = reqDate.atTime(9, 0).atZone(utc).toInstant();
    Instant end = start.plus(2, ChronoUnit.HOURS);

    // 1. Customer creates Service Request
    String reqResp =
        mockMvc
            .perform(
                post("/api/v1/contracts/requests")
                    .with(principal(customerUserId, "CUSTOMER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "assetId",
                                assetId,
                                "description",
                                "Sepay full lifecycle test",
                                "requestedFrom",
                                reqDate,
                                "requestedUntil",
                                reqDate,
                                "timezone",
                                "UTC",
                                "requestType",
                                "ONE_TIME"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID requestId = UUID.fromString(objectMapper.readTree(reqResp).at("/data/id").stringValue());

    mockMvc
        .perform(
            post("/api/v1/contracts/requests/{id}/claim", requestId)
                .with(principal(managerUserId, "MANAGER")))
        .andExpect(status().isOk());

    // 2. Manager quotes 5,000,000 VND (total with 10% VAT = 5,500,000 VND)
    mockMvc
        .perform(
            post("/api/v1/contracts/requests/{id}/quotes", requestId)
                .with(principal(managerUserId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "scopeSnapshot", "Tower inspection quote",
                            "firstOccurrenceStartAt", start,
                            "firstOccurrenceEndAt", end,
                            "inspectorId", inspectorUserId,
                            "droneId", droneId,
                            "pricePerOccurrenceVnd", 5000000.00))))
        .andExpect(status().isCreated());

    // 4. Customer accepts quote
    mockMvc
        .perform(
            post("/api/v1/contracts/requests/{id}/accept", requestId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isOk());

    ServiceRequestOccurrence occurrence =
        occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId).getFirst();
    UUID occurrenceId = occurrence.getId();

    // 5. Manager uploads signed contract to the occurrence
    byte[] docxBytes = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
    mockMvc
        .perform(
            multipart(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/signed-contract",
                    requestId,
                    occurrenceId)
                .file(
                    new MockMultipartFile(
                        "file",
                        "occurrence-contract.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        docxBytes))
                .with(principal(managerUserId, "MANAGER")))
        .andExpect(status().isOk());

    // 6. Manager assigns occurrence to inspector and drone
    String assignResp =
        mockMvc
            .perform(
                post(
                        "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/assign",
                        requestId,
                        occurrenceId)
                    .with(principal(managerUserId, "MANAGER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "plannedStartAt", start,
                                "plannedEndAt", end,
                                "inspectorId", inspectorUserId,
                                "droneId", droneId))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID inspectionId =
        UUID.fromString(
            objectMapper.readTree(assignResp).at("/data/occurrences/0/inspectionId").stringValue());

    // 7. Inspector accepts assignment and uploads flight permit
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/accept", inspectionId)
                .with(principal(inspectorUserId, "INSPECTOR")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/flight-permit", inspectionId)
                .with(principal(inspectorUserId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "fileKey",
                            "permits/p1.pdf",
                            "fileName",
                            "p1.pdf",
                            "permitNumber",
                            "GP-SEPAY-001",
                            "issuingAuthority",
                            "Cục Tác chiến - BTTM",
                            "validFrom",
                            Instant.now().minus(1, ChronoUnit.HOURS),
                            "validTo",
                            Instant.now().plus(30, ChronoUnit.DAYS)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.flightPermitVerificationStatus").value("APPROVED"));

    // 8. Start & end field session
    String sessionResp =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/field-sessions", inspectionId)
                    .with(principal(inspectorUserId, "INSPECTOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("preFlightChecklistNote", "All preflight checks pass"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID sessionId =
        UUID.fromString(objectMapper.readTree(sessionResp).at("/data/id").stringValue());

    // Upload evidence
    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", inspectionId)
                .file(new MockMultipartFile("file", "evidence1.png", "image/png", VALID_PNG_BYTES))
                .param("fieldSessionId", sessionId.toString())
                .with(principal(inspectorUserId, "INSPECTOR")))
        .andExpect(status().isOk());

    // End field session
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/field-sessions/{sessionId}/completion",
                    inspectionId,
                    sessionId)
                .with(principal(inspectorUserId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("outcomeNotes", "Completed session"))))
        .andExpect(status().isOk());

    // Confirm no findings
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings/confirm", inspectionId)
                .with(principal(inspectorUserId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("noFindingsAcknowledged", true))))
        .andExpect(status().isOk());

    // 9. Draft manual report
    String draftResp =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/report/draft/manual", inspectionId)
                    .with(principal(inspectorUserId, "INSPECTOR")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID reportVersionId =
        UUID.fromString(objectMapper.readTree(draftResp).at("/data/id").stringValue());

    // 10. Inspector verifies and submits report
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vId}/verify",
                    inspectionId,
                    reportVersionId)
                .with(principal(inspectorUserId, "INSPECTOR")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vId}/submit",
                    inspectionId,
                    reportVersionId)
                .with(principal(inspectorUserId, "INSPECTOR")))
        .andExpect(status().isOk());

    // 11. Manager approves report, settles Inspector payout, and publishes report
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vId}/review",
                    inspectionId,
                    reportVersionId)
                .with(principal(managerUserId, "MANAGER"))
                .param("approve", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPROVED"));

    if (stopAtApproved) {
      return new PreparedInspection(requestId, occurrenceId, inspectionId, reportVersionId);
    }

    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/record-payout",
                    requestId,
                    occurrenceId)
                .with(principal(managerUserId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "payoutReference",
                            "UNC-SEPAY-" + occurrenceId.toString().substring(0, 8)))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vId}/publish",
                    inspectionId,
                    reportVersionId)
                .with(principal(managerUserId, "MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

    // 12. Customer accepts report
    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/accept-report",
                    requestId,
                    occurrenceId)
                .with(principal(customerUserId, "CUSTOMER"))
                .param("reportVersionId", reportVersionId.toString()))
        .andExpect(status().isOk());

    return new PreparedInspection(requestId, occurrenceId, inspectionId, reportVersionId);
  }

  private UUID createPlatformUser(UserRole role, String email, String fullName) {
    User user = new User(email, fullName, null, UserStatus.ACTIVE);
    user.addRole(role);
    return users.saveAndFlush(user).getId();
  }

  private UUID createUser(UserRole role, String email, String fullName) {
    User user = new User(email, fullName, null, UserStatus.ACTIVE);
    user.addRole(role);
    return users.saveAndFlush(user).getId();
  }

  private static RequestPostProcessor principal(UUID id, String role) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}

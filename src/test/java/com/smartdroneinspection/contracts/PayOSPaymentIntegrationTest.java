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
import com.smartdroneinspection.contracts.gateway.PayOSGateway;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
import com.smartdroneinspection.infrastructure.payment.PayOSCrypto;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
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
      "app.payment.payos.enabled=true",
      "app.payment.payos.client-id=test-client-id",
      "app.payment.payos.api-key=test-api-key",
      "app.payment.payos.checksum-key=test_payos_checksum_key_12345",
      "app.payment.payos.base-url=https://api-merchant.payos.vn"
    })
@Import(TestcontainersConfiguration.class)
class PayOSPaymentIntegrationTest {

  private static final String TEST_CHECKSUM_KEY = "test_payos_checksum_key_12345";
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

  @MockitoBean PayOSGateway payOSGateway;

  private MockMvc mockMvc;

  private UUID orgId;
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

    // Mock PayOS gateway behavior
    when(payOSGateway.createPaymentLink(anyLong(), anyLong(), anyString(), any(), any()))
        .thenAnswer(
            invocation -> {
              long orderCode = invocation.getArgument(0);
              long amount = invocation.getArgument(1);
              return new PayOSGateway.PayOSPaymentLink(
                  orderCode,
                  "https://pay.payos.vn/web/test-" + orderCode,
                  "00020101021238540010A00000072701260006970422011212345678900208QRIBFTTA5204",
                  amount,
                  "plink_" + orderCode,
                  "PENDING");
            });

    when(payOSGateway.verifyWebhookSignature(any(), any()))
        .thenAnswer(
            invocation -> {
              Map<String, Object> data = invocation.getArgument(0);
              String signature = invocation.getArgument(1);
              return PayOSCrypto.verifyWebhookSignature(data, signature, TEST_CHECKSUM_KEY);
            });

    // Clean tables
    jdbcTemplate.execute("DELETE FROM inspection_finance_settings");
    jdbcTemplate.execute("DELETE FROM service_request_occurrences");
    jdbcTemplate.execute("DELETE FROM service_requests");
    jdbcTemplate.execute("DELETE FROM service_request_quotes");
    jdbcTemplate.execute("DELETE FROM contract_partner_drones");
    jdbcTemplate.execute("DELETE FROM contract_partner_inspectors");

    // Organizations
    orgId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, 'PayOS Test Corp', 'PayOS Corp', ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        orgId,
        "ORG-" + orgId.toString().substring(0, 18));

    UUID otherOrgId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, 'Other Org Corp', 'Other Corp', ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        otherOrgId,
        "ORG-" + otherOrgId.toString().substring(0, 18));

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
            orgId,
            UserRole.CUSTOMER,
            ActorZone.CUSTOMER_ORGANIZATION,
            "customer." + UUID.randomUUID() + "@payos.test",
            "Customer Primary");

    otherCustomerUserId =
        createUser(
            otherOrgId,
            UserRole.CUSTOMER,
            ActorZone.CUSTOMER_ORGANIZATION,
            "other." + UUID.randomUUID() + "@other.test",
            "Customer Other Org");

    inspectorUserId =
        createUser(
            null,
            UserRole.INSPECTOR,
            ActorZone.INSPECTOR_PARTNER,
            "inspector." + UUID.randomUUID() + "@partner.test",
            "Partner Pilot");

    User inspector = users.findDetailedById(inspectorUserId).orElseThrow();
    inspector.updatePilotLicense(
        "VN-UAV-PILOT-2026",
        "CLASS_A",
        Instant.now().plus(365, ChronoUnit.DAYS),
        "licenses/pilot-cert.pdf");
    users.saveAndFlush(inspector);

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
                orgId,
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
      "Full PayOS Lifecycle: Create Link, Webhook Settlement, Idempotency, and Deliverable Unblocking")
  void testFullPayOSLifecycle() throws Exception {
    // 1. Setup Service Request through acceptance and report publishing
    PreparedInspection prepared = prepareInspectionReadyForPayment();
    UUID requestId = prepared.requestId();
    UUID occurrenceId = prepared.occurrenceId();
    UUID inspectionId = prepared.inspectionId();
    UUID reportVersionId = prepared.reportVersionId();
    long agreedPrice = 5000000L; // Quote price is inclusive of VAT

    // 2. Deliverable download is blocked with 402 PAYMENT_REQUIRED
    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{vId}/document",
                    inspectionId,
                    reportVersionId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isPaymentRequired());

    // 3. Unauthorized org cannot access service request (404 Not Found per multi-tenant isolation)
    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/payos/create-payment-link",
                    requestId,
                    occurrenceId)
                .with(principal(otherCustomerUserId, "CUSTOMER")))
        .andExpect(status().isNotFound());

    // 4. Customer creates PayOS payment link
    String linkResponse =
        mockMvc
            .perform(
                post(
                        "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/payos/create-payment-link",
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

    // Verify occurrence has been assigned orderCode and method PAYOS
    ServiceRequestOccurrence occAfterLink = occurrences.findById(occurrenceId).orElseThrow();
    assertThat(occAfterLink.getPayosOrderCode()).isEqualTo(allocatedOrderCode);
    assertThat(occAfterLink.getPaymentMethod()).isEqualTo("PAYOS");
    assertThat(occAfterLink.getPaymentStatus()).isEqualTo(OccurrencePaymentStatus.PAYMENT_DUE);

    // 5. Webhook: Rejects invalid signature with 401
    Map<String, Object> webhookData = new LinkedHashMap<>();
    webhookData.put("orderCode", allocatedOrderCode);
    webhookData.put("amount", agreedPrice);
    webhookData.put("reference", "PAYOS-TXN-12345");
    webhookData.put("code", "00");

    Map<String, Object> invalidPayload =
        Map.of(
            "code", "00",
            "desc", "success",
            "data", webhookData,
            "signature", "invalid_forged_hex_signature");

    mockMvc
        .perform(
            post("/api/v1/webhooks/payos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidPayload)))
        .andExpect(status().isUnauthorized());

    // Occurrence remains PAYMENT_DUE
    assertThat(occurrences.findById(occurrenceId).orElseThrow().getPaymentStatus())
        .isEqualTo(OccurrencePaymentStatus.PAYMENT_DUE);

    // 6. Webhook: Rejects amount mismatch with 400 Bad Request
    Map<String, Object> mismatchData = new LinkedHashMap<>(webhookData);
    mismatchData.put("amount", 1000000L); // mismatch!
    String mismatchCanonical =
        "amount=1000000&code=00&orderCode=" + allocatedOrderCode + "&reference=PAYOS-TXN-12345";
    String mismatchSig = PayOSCrypto.hmacSha256Hex(mismatchCanonical, TEST_CHECKSUM_KEY);

    Map<String, Object> mismatchPayload =
        Map.of("code", "00", "desc", "success", "data", mismatchData, "signature", mismatchSig);

    mockMvc
        .perform(
            post("/api/v1/webhooks/payos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mismatchPayload)))
        .andExpect(status().isBadRequest());

    // 7. Webhook: Valid signature settles payment and marks occurrence PAID
    String validCanonical =
        "amount="
            + agreedPrice
            + "&code=00&orderCode="
            + allocatedOrderCode
            + "&reference=PAYOS-TXN-12345";
    String validSig = PayOSCrypto.hmacSha256Hex(validCanonical, TEST_CHECKSUM_KEY);

    Map<String, Object> validPayload =
        Map.of("code", "00", "desc", "success", "data", webhookData, "signature", validSig);

    mockMvc
        .perform(
            post("/api/v1/webhooks/payos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validPayload)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("00"))
        .andExpect(jsonPath("$.desc").value("success"));

    // Verify occurrence is now PAID, payout is ELIGIBLE
    ServiceRequestOccurrence settledOcc = occurrences.findById(occurrenceId).orElseThrow();
    assertThat(settledOcc.getPaymentStatus()).isEqualTo(OccurrencePaymentStatus.PAID);
    assertThat(settledOcc.getPayoutStatus()).isEqualTo(OccurrencePayoutStatus.ELIGIBLE);
    assertThat(settledOcc.getPaymentReference()).isEqualTo("PAYOS-TXN-12345");
    assertThat(settledOcc.getPaymentConfirmedAt()).isNotNull();

    // 8. Idempotency: Duplicate webhook delivery is a safe no-op returning 200 OK
    mockMvc
        .perform(
            post("/api/v1/webhooks/payos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validPayload)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("00"));

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
  @DisplayName("Cannot create PayOS payment link before report acceptance (PAYMENT_NOT_DUE)")
  void cannotCreatePaymentLinkBeforeAcceptance() throws Exception {
    ZoneId utc = ZoneId.of("UTC");
    Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Instant end = start.plus(2, ChronoUnit.HOURS);
    LocalDate reqDate = start.atZone(utc).toLocalDate();

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
                                "PayOS E2E Pre-Acceptance Test",
                                "requestedFrom",
                                reqDate,
                                "requestedUntil",
                                reqDate.plusDays(1),
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
                .with(principal(customerUserId, "CUSTOMER"))
                .param("versionNo", "1"))
        .andExpect(status().isOk());

    UUID occurrenceId =
        occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId).getFirst().getId();

    // Occurrence payment status is PENDING (report not accepted yet)
    // Trying to create payment link must return 409 PAYMENT_NOT_DUE
    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/payos/create-payment-link",
                    requestId,
                    occurrenceId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAYMENT_NOT_DUE"));
  }

  private record PreparedInspection(
      UUID requestId, UUID occurrenceId, UUID inspectionId, UUID reportVersionId) {}

  private PreparedInspection prepareInspectionReadyForPayment() throws Exception {
    ZoneId utc = ZoneId.of("UTC");
    Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Instant end = start.plus(2, ChronoUnit.HOURS);
    LocalDate reqDate = start.atZone(utc).toLocalDate();

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
                                "PayOS full lifecycle test",
                                "requestedFrom",
                                reqDate,
                                "requestedUntil",
                                reqDate.plusDays(1),
                                "timezone",
                                "UTC",
                                "requestType",
                                "ONE_TIME"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID requestId = UUID.fromString(objectMapper.readTree(reqResp).at("/data/id").stringValue());

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
                .with(principal(customerUserId, "CUSTOMER"))
                .param("versionNo", "1"))
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

    // 7. Upload flight permit
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/flight-permit", inspectionId)
                .with(principal(inspectorUserId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("fileKey", "permits/p1.pdf", "fileName", "p1.pdf"))))
        .andExpect(status().isOk());

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

    // 11. Manager reviews and publishes report
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vId}/review",
                    inspectionId,
                    reportVersionId)
                .with(principal(managerUserId, "MANAGER"))
                .param("approve", "true"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vId}/publish",
                    inspectionId,
                    reportVersionId)
                .with(principal(managerUserId, "MANAGER")))
        .andExpect(status().isOk());

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
    User user = new User(email, fullName, null, UserStatus.ACTIVE, ActorZone.PLATFORM, null);
    user.addRole(role);
    return users.saveAndFlush(user).getId();
  }

  private UUID createUser(
      UUID organizationId, UserRole role, ActorZone zone, String email, String fullName) {
    User user = new User(email, fullName, null, UserStatus.ACTIVE, zone, organizationId);
    user.addRole(role);
    return users.saveAndFlush(user).getId();
  }

  private static RequestPostProcessor principal(UUID id, String role) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}

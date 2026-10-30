package com.smartdroneinspection.contracts;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InspectionFinanceApiIntegrationTest {

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

  private MockMvc mockMvc;

  private UUID orgId;
  private UUID customerUserId;
  private UUID managerUserId;
  private UUID adminUserId;
  private UUID inspectorUserId;
  private UUID droneId;
  private UUID assetId;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    // Clean tables to maintain independent state (respect FK order)
    jdbcTemplate.execute("DELETE FROM inspection_finance_settings");
    jdbcTemplate.execute("DELETE FROM service_request_occurrences");
    jdbcTemplate.execute("DELETE FROM service_requests");
    jdbcTemplate.execute("DELETE FROM service_request_quotes");
    jdbcTemplate.execute("DELETE FROM contract_partner_drones");
    jdbcTemplate.execute("DELETE FROM contract_partner_inspectors");

    // Organization
    orgId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, 'Finance Test Ltd', 'Finance Ltd', ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        orgId,
        "ORG-" + orgId.toString().substring(0, 18));

    // Platform Admin & Manager
    adminUserId =
        createPlatformUser(
            UserRole.ADMIN, "admin." + UUID.randomUUID() + "@platform.internal", "Admin User");
    managerUserId =
        createPlatformUser(
            UserRole.MANAGER,
            "manager." + UUID.randomUUID() + "@platform.internal",
            "Manager User");

    // Customer User in Organization
    customerUserId =
        createUser(
            orgId,
            UserRole.CUSTOMER,
            ActorZone.CUSTOMER_ORGANIZATION,
            "customer." + UUID.randomUUID() + "@finance.test",
            "Customer OrgAdmin");

    // Partner Inspector
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
    mockMvcSilently(
        () ->
            mockMvc
                .perform(
                    post("/api/v1/contracts/partners/inspectors")
                        .with(principal(managerUserId, "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                Map.of("inspectorUserId", inspectorUserId))))
                .andExpect(status().isCreated()));

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

    // Register Drone in Partner Registry
    mockMvcSilently(
        () ->
            mockMvc
                .perform(
                    post("/api/v1/contracts/partners/drones")
                        .with(principal(managerUserId, "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("droneId", droneId))))
                .andExpect(status().isCreated()));

    // Asset
    AssetCategory category =
        categories.saveAndFlush(
            new AssetCategory(
                "cat-" + UUID.randomUUID().toString().substring(0, 8),
                "Bridges & Overpasses",
                "Civil infrastructure bridge category",
                true));

    Asset asset =
        assets.saveAndFlush(
            new Asset(
                orgId,
                category.getId(),
                "BR-FIN-" + UUID.randomUUID().toString().substring(0, 8),
                "Finance Test Bridge",
                "Steel arch bridge",
                "{\"latitude\":10.7712,\"longitude\":106.7082,\"city\":\"Ho Chi Minh City\"}",
                null,
                null,
                null,
                List.of("Girder G1", "Pier 1"),
                customerUserId));
    assetId = asset.getId();
  }

  @Test
  @DisplayName(
      "Full inspection finance lifecycle: settings, VND quote, contract upload, gated deliverables, exact payment, and payout")
  void completeInspectionFinanceWorkflow() throws Exception {
    // -------------------------------------------------------------------------
    // Phase 1: Admin Finance Settings Access Control & Validation
    // -------------------------------------------------------------------------

    // 1.1 Non-admin cannot view or modify settings
    mockMvc
        .perform(
            get("/api/v1/contracts/admin/finance-settings")
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/contracts/admin/finance-settings")
                .with(principal(managerUserId, "MANAGER")))
        .andExpect(status().isForbidden());

    // 1.2 Admin views initially unconfigured settings
    mockMvc
        .perform(
            get("/api/v1/contracts/admin/finance-settings").with(principal(adminUserId, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.configured").value(false));

    // 1.3 Invalid rates (> 100 or < 0) are rejected
    mockMvc
        .perform(
            put("/api/v1/contracts/admin/finance-settings")
                .with(principal(adminUserId, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("commissionRate", 150.00, "vatRate", 10.00, "tncnRate", 10.00))))
        .andExpect(status().isBadRequest());

    // 1.4 Admin configures valid rates: 15% commission, 10% VAT, 10% TNCN
    mockMvc
        .perform(
            put("/api/v1/contracts/admin/finance-settings")
                .with(principal(adminUserId, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("commissionRate", 15.00, "vatRate", 10.00, "tncnRate", 10.00))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.configured").value(true))
        .andExpect(jsonPath("$.data.commissionRate").value(15.00))
        .andExpect(jsonPath("$.data.vatRate").value(10.00))
        .andExpect(jsonPath("$.data.tncnRate").value(10.00))
        .andExpect(jsonPath("$.data.accountingDisclaimer").isNotEmpty());

    // -------------------------------------------------------------------------
    // Phase 2: Service Request, VND Quote, Acceptance, and Contract Attachment Gate
    // -------------------------------------------------------------------------

    ZoneId utc = ZoneId.of("UTC");
    Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Instant end = start.plus(2, ChronoUnit.HOURS);
    LocalDate reqDate = start.atZone(utc).toLocalDate();

    // 2.1 Customer creates a one-time service request
    String createResp =
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
                                "Finance E2E Inspection",
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
    UUID requestId =
        UUID.fromString(objectMapper.readTree(createResp).at("/data/id").stringValue());

    // 2.2 Manager issues a payable VND quote (11,000,000 VND)
    mockMvc
        .perform(
            post("/api/v1/contracts/requests/{requestId}/quotes", requestId)
                .with(principal(managerUserId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "scopeSnapshot", "Visual structural check",
                            "firstOccurrenceStartAt", start,
                            "firstOccurrenceEndAt", end,
                            "inspectorId", inspectorUserId,
                            "droneId", droneId,
                            "pricePerOccurrenceVnd", 11000000.00))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.quotes[0].currency").value("VND"))
        .andExpect(jsonPath("$.data.quotes[0].pricePerOccurrenceVnd").value(11000000.00));

    // 2.3 Customer accepts the quote
    mockMvc
        .perform(
            post("/api/v1/contracts/requests/{requestId}/accept", requestId)
                .with(principal(customerUserId, "CUSTOMER"))
                .param("versionNo", "1"))
        .andExpect(status().isOk());

    ServiceRequestOccurrence occurrence =
        occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId).getFirst();
    UUID occurrenceId = occurrence.getId();

    // Verify financial snapshot on occurrence
    assertThat(occurrence.getCurrency()).isEqualTo("VND");
    assertThat(occurrence.getTotalPrice()).isEqualByComparingTo("11000000.00");
    assertThat(occurrence.getServiceBaseAmount()).isEqualByComparingTo("10000000.00");
    assertThat(occurrence.getVatAmount()).isEqualByComparingTo("1000000.00");
    assertThat(occurrence.getCommissionAmount()).isEqualByComparingTo("1500000.00");
    assertThat(occurrence.getInspectorGrossPayout()).isEqualByComparingTo("8500000.00");
    assertThat(occurrence.getTncnAmount()).isEqualByComparingTo("850000.00");
    assertThat(occurrence.getInspectorNetPayout()).isEqualByComparingTo("7650000.00");
    assertThat(occurrence.getPaymentStatus()).isEqualTo(OccurrencePaymentStatus.PENDING);
    assertThat(occurrence.getPayoutStatus()).isEqualTo(OccurrencePayoutStatus.PENDING);

    // 2.4 Attempting assignment without signed contract is rejected
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
        .andExpect(status().isConflict());

    // 2.5 Manager uploads signed contract to the occurrence
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

    // 2.6 Customer can download the occurrence contract document
    mockMvc
        .perform(
            get(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/contract-document",
                    requestId,
                    occurrenceId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isOk());

    // 2.7 Now assignment succeeds and creates inspection
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

    // -------------------------------------------------------------------------
    // Phase 3: Field Session, Findings, Report Publication & Payment Due
    // -------------------------------------------------------------------------

    // 3.1 Upload flight permit
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/flight-permit", inspectionId)
                .with(principal(inspectorUserId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("fileKey", "permits/p1.pdf", "fileName", "p1.pdf"))))
        .andExpect(status().isOk());

    // 3.2 Start & end field session
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

    // Draft manual report
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

    // Verify & Submit report
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

    // Manager reviews and publishes report
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

    // -------------------------------------------------------------------------
    // Phase 4: Deliverable Gating, Customer Acceptance, Payment Confirmation & Payout
    // -------------------------------------------------------------------------

    // 4.1 Customer can view full report data
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/report/versions/{vId}", inspectionId, reportVersionId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isOk());

    // 4.2 BUT Customer CANNOT download the rendered deliverable document yet! (Payment Required)
    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{vId}/document",
                    inspectionId,
                    reportVersionId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isPaymentRequired());

    // 4.3 Customer accepts the published report version
    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/accept-report",
                    requestId,
                    occurrenceId)
                .with(principal(customerUserId, "CUSTOMER"))
                .param("reportVersionId", reportVersionId.toString()))
        .andExpect(status().isOk());

    // Payment status is now PAYMENT_DUE (not PAID!)
    ServiceRequestOccurrence updatedOcc = occurrences.findById(occurrenceId).orElseThrow();
    assertThat(updatedOcc.getPaymentStatus()).isEqualTo(OccurrencePaymentStatus.PAYMENT_DUE);

    // 4.4 Deliverable document STILL locked for Customer while PAYMENT_DUE
    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{vId}/document",
                    inspectionId,
                    reportVersionId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isPaymentRequired());

    // 4.5 Manager payout recording is blocked before customer payment is confirmed
    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/record-payout",
                    requestId,
                    occurrenceId)
                .with(principal(managerUserId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("payoutReference", "PREMATURE-PAYOUT"))))
        .andExpect(status().isConflict());

    // 4.6 Non-manager cannot confirm customer payment
    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/confirm-payment",
                    requestId,
                    occurrenceId)
                .with(principal(customerUserId, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("paymentReference", "TXN-1", "amount", 11000000.00))))
        .andExpect(status().isForbidden());

    // 4.7 Mismatched payment amount is rejected
    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/confirm-payment",
                    requestId,
                    occurrenceId)
                .with(principal(managerUserId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("paymentReference", "TXN-1", "amount", 10000000.00))))
        .andExpect(status().isBadRequest());

    // 4.8 Manager confirms exact manual bank transfer (11,000,000 VND)
    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/confirm-payment",
                    requestId,
                    occurrenceId)
                .with(principal(managerUserId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("paymentReference", "BANK-TRANSFER-5555", "amount", 11000000.00))))
        .andExpect(status().isOk());

    updatedOcc = occurrences.findById(occurrenceId).orElseThrow();
    assertThat(updatedOcc.getPaymentStatus()).isEqualTo(OccurrencePaymentStatus.PAID);
    assertThat(updatedOcc.getPayoutStatus()).isEqualTo(OccurrencePayoutStatus.ELIGIBLE);
    assertThat(updatedOcc.getPaymentReference()).isEqualTo("BANK-TRANSFER-5555");
    assertThat(updatedOcc.getPaymentConfirmedByUserId()).isEqualTo(managerUserId);

    // 4.9 Final deliverable document is now UNLOCKED for Customer!
    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{vId}/document",
                    inspectionId,
                    reportVersionId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isOk());

    // 4.10 Manager records Inspector payout
    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/record-payout",
                    requestId,
                    occurrenceId)
                .with(principal(managerUserId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("payoutReference", "PAYOUT-BANK-8888"))))
        .andExpect(status().isOk());

    updatedOcc = occurrences.findById(occurrenceId).orElseThrow();
    assertThat(updatedOcc.getPayoutStatus()).isEqualTo(OccurrencePayoutStatus.PAID);
    assertThat(updatedOcc.getPayoutReference()).isEqualTo("PAYOUT-BANK-8888");
    assertThat(updatedOcc.getPayoutRecordedByUserId()).isEqualTo(managerUserId);

    // 4.11 Duplicate payout recording is idempotent
    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/record-payout",
                    requestId,
                    occurrenceId)
                .with(principal(managerUserId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("payoutReference", "PAYOUT-BANK-8888"))))
        .andExpect(status().isOk());

    // -------------------------------------------------------------------------
    // Phase 5: Scoped Financial Views & Snapshot Immutability
    // -------------------------------------------------------------------------

    // 5.1 Customer financial view: internal commission, inspector tax base, and payout are masked
    mockMvc
        .perform(
            get(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/finance",
                    requestId,
                    occurrenceId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalPrice").value(11000000.00))
        .andExpect(jsonPath("$.data.serviceBaseAmount").value(10000000.00))
        .andExpect(jsonPath("$.data.vatAmount").value(1000000.00))
        .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
        .andExpect(jsonPath("$.data.commissionRate").doesNotExist())
        .andExpect(jsonPath("$.data.commissionAmount").doesNotExist())
        .andExpect(jsonPath("$.data.inspectorGrossPayout").doesNotExist())
        .andExpect(jsonPath("$.data.inspectorNetPayout").doesNotExist())
        .andExpect(jsonPath("$.data.payoutStatus").doesNotExist());

    // 5.2 Inspector financial view: sees gross compensation, TNCN withheld, net payout
    mockMvc
        .perform(
            get(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/finance",
                    requestId,
                    occurrenceId)
                .with(principal(inspectorUserId, "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inspectorGrossPayout").value(8500000.00))
        .andExpect(jsonPath("$.data.tncnRate").value(10.00))
        .andExpect(jsonPath("$.data.tncnAmount").value(850000.00))
        .andExpect(jsonPath("$.data.inspectorNetPayout").value(7650000.00))
        .andExpect(jsonPath("$.data.payoutStatus").value("PAID"))
        .andExpect(jsonPath("$.data.totalPrice").doesNotExist())
        .andExpect(jsonPath("$.data.commissionRate").doesNotExist());

    // 5.3 Manager financial view: full transparency
    mockMvc
        .perform(
            get(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/finance",
                    requestId,
                    occurrenceId)
                .with(principal(managerUserId, "MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalPrice").value(11000000.00))
        .andExpect(jsonPath("$.data.serviceBaseAmount").value(10000000.00))
        .andExpect(jsonPath("$.data.vatAmount").value(1000000.00))
        .andExpect(jsonPath("$.data.commissionAmount").value(1500000.00))
        .andExpect(jsonPath("$.data.inspectorGrossPayout").value(8500000.00))
        .andExpect(jsonPath("$.data.tncnAmount").value(850000.00))
        .andExpect(jsonPath("$.data.inspectorNetPayout").value(7650000.00))
        .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
        .andExpect(jsonPath("$.data.payoutStatus").value("PAID"));

    // 5.4 Snapshot immutability: Admin updates settlement rates for future work
    mockMvc
        .perform(
            put("/api/v1/contracts/admin/finance-settings")
                .with(principal(adminUserId, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("commissionRate", 25.00, "vatRate", 8.00, "tncnRate", 20.00))))
        .andExpect(status().isOk());

    // Occurrence snapshots remain completely unchanged!
    mockMvc
        .perform(
            get(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/finance",
                    requestId,
                    occurrenceId)
                .with(principal(managerUserId, "MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.commissionRate").value(15.00))
        .andExpect(jsonPath("$.data.commissionAmount").value(1500000.00))
        .andExpect(jsonPath("$.data.vatRate").value(10.00))
        .andExpect(jsonPath("$.data.vatAmount").value(1000000.00))
        .andExpect(jsonPath("$.data.inspectorNetPayout").value(7650000.00));
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

  private void mockMvcSilently(ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @FunctionalInterface
  interface ThrowingRunnable {
    void run() throws Exception;
  }
}

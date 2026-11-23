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
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.service.PaymentDueExpiryService;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Duration;
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

/**
 * Integration coverage for the customer payment deadline and the payment notice/receipt endpoint.
 *
 * <p>Both behaviours are driven entirely from persisted state, so they are only meaningful against
 * a real database and a real security filter chain. These cases exist because the corresponding
 * Report 5 entries (WF3-026 and WF3-027) were recorded as Pending while only the unit layer ran.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentDueAndDocumentApiIntegrationTest {

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
  @Autowired ServiceRequestOccurrenceRepository occurrences;
  @Autowired PaymentDueExpiryService paymentDueExpiryService;
  @Autowired JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;

  private UUID adminUserId;
  private UUID managerUserId;
  private UUID customerUserId;
  private UUID foreignCustomerUserId;
  private UUID inspectorUserId;
  private UUID droneId;
  private UUID assetId;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    jdbcTemplate.execute("DELETE FROM notifications");
    jdbcTemplate.execute("DELETE FROM inspection_finance_settings");
    jdbcTemplate.execute("DELETE FROM service_request_occurrences");
    jdbcTemplate.execute("DELETE FROM service_requests");
    jdbcTemplate.execute("DELETE FROM service_request_quotes");

    adminUserId =
        createUser(UserRole.ADMIN, "admin." + UUID.randomUUID() + "@platform.internal", "Admin");
    managerUserId =
        createUser(UserRole.MANAGER, "manager." + UUID.randomUUID() + "@platform.internal", "Mgr");
    customerUserId = createUser(UserRole.CUSTOMER, "c." + UUID.randomUUID() + "@due.test", "Cust");
    foreignCustomerUserId =
        createUser(UserRole.CUSTOMER, "foreign." + UUID.randomUUID() + "@due.test", "Other Cust");
    inspectorUserId =
        createUser(UserRole.INSPECTOR, "i." + UUID.randomUUID() + "@partner.test", "Pilot");

    jdbcTemplate.update(
        "INSERT INTO inspector_profiles (user_id, pilot_license_number, pilot_license_class, pilot_license_expires_at, pilot_license_file_key) VALUES (?, ?, ?, ?, ?)",
        inspectorUserId,
        "VN-UAV-PILOT-2026",
        "CLASS_B",
        java.sql.Timestamp.from(Instant.now().plus(365, ChronoUnit.DAYS)),
        "licenses/pilot-cert.pdf");

    silently(
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

    silently(
        () ->
            mockMvc
                .perform(
                    post("/api/v1/contracts/partners/drones")
                        .with(principal(managerUserId, "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("droneId", droneId))))
                .andExpect(status().isCreated()));

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
                customerUserId,
                category.getId(),
                "BR-DUE-" + UUID.randomUUID().toString().substring(0, 8),
                "Payment Due Test Bridge",
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
  @DisplayName("Acceptance stamps one payment deadline and the PDF endpoint gates notice/receipt")
  void acceptanceStampsDeadlineAndPaymentDocumentSwitchesFromNoticeToReceipt() throws Exception {
    UUID requestId = createQuotedAcceptedRequest();
    UUID occurrenceId =
        occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId).getFirst().getId();
    UUID inspectionId = assignAndPublishReport(requestId, occurrenceId);

    ServiceRequestOccurrence beforeAccept = occurrences.findById(occurrenceId).orElseThrow();
    assertThat(beforeAccept.getPaymentDueAt()).isNull();

    // The notice does not exist before the customer accepts the published report.
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/report/payment-document", inspectionId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAYMENT_DOCUMENT_NOT_AVAILABLE"));

    acceptReport(requestId, occurrenceId);

    ServiceRequestOccurrence accepted = occurrences.findById(occurrenceId).orElseThrow();
    assertThat(accepted.getPaymentStatus()).isEqualTo(OccurrencePaymentStatus.PAYMENT_DUE);
    assertThat(accepted.getPaymentDueAt()).isNotNull();
    assertThat(accepted.getPaymentOverdueNotifiedAt()).isNull();
    // Default configured window is 15 days from acceptance.
    Instant expectedDue = Instant.now().plus(Duration.ofDays(15));
    assertThat(Duration.between(expectedDue, accepted.getPaymentDueAt()).abs())
        .isLessThan(Duration.ofMinutes(5));

    // The owning customer receives a notice PDF once the report is accepted but unpaid.
    byte[] notice =
        mockMvc
            .perform(
                get("/api/v1/inspections/{id}/report/payment-document", inspectionId)
                    .with(principal(customerUserId, "CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(notice).isNotEmpty();
    assertThat(new String(notice, 0, 4)).isEqualTo("%PDF");

    // A customer from another organization is scoped out.
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/report/payment-document", inspectionId)
                .with(principal(foreignCustomerUserId, "CUSTOMER")))
        .andExpect(status().isNotFound());

    // A manager may read the same document for review.
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/report/payment-document", inspectionId)
                .with(principal(managerUserId, "MANAGER")))
        .andExpect(status().isOk());

    // The overdue scan does nothing while the deadline has not passed.
    PaymentDueExpiryService.Summary earlyScan = paymentDueExpiryService.runOnce(Instant.now());
    assertThat(earlyScan.overdueNotified()).isZero();

    // Past the deadline the scan raises exactly one event and stamps the occurrence.
    PaymentDueExpiryService.Summary lateScan =
        paymentDueExpiryService.runOnce(accepted.getPaymentDueAt().plus(Duration.ofHours(1)));
    assertThat(lateScan.overdueNotified()).isEqualTo(1);
    assertThat(occurrences.findById(occurrenceId).orElseThrow().getPaymentOverdueNotifiedAt())
        .isNotNull();

    // A second scan is a no-op: the occurrence is no longer returned by the overdue query.
    PaymentDueExpiryService.Summary repeatScan =
        paymentDueExpiryService.runOnce(accepted.getPaymentDueAt().plus(Duration.ofDays(2)));
    assertThat(repeatScan.overdueNotified()).isZero();

    // Overdue never revokes access to the deliverable or changes the payment state.
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/report/payment-document", inspectionId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isOk());
    assertThat(occurrences.findById(occurrenceId).orElseThrow().getPaymentStatus())
        .isEqualTo(OccurrencePaymentStatus.PAYMENT_DUE);

    // After verified payment the same endpoint returns a receipt instead of a notice.
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
                        Map.of("paymentReference", "TXN-DUE-1", "amount", 11000000.00))))
        .andExpect(status().isOk());

    ServiceRequestOccurrence paid = occurrences.findById(occurrenceId).orElseThrow();
    assertThat(paid.getPaymentStatus()).isEqualTo(OccurrencePaymentStatus.PAID);

    byte[] receipt =
        mockMvc
            .perform(
                get("/api/v1/inspections/{id}/report/payment-document", inspectionId)
                    .with(principal(customerUserId, "CUSTOMER")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(receipt).isNotEmpty();
    assertThat(new String(receipt, 0, 4)).isEqualTo("%PDF");
    // Notice and receipt are distinct documents even though both are PDFs.
    assertThat(receipt.length).isNotEqualTo(notice.length);
  }

  private UUID createQuotedAcceptedRequest() throws Exception {
    configureFinance();
    ZoneId utc = ZoneId.of("UTC");
    LocalDate requestDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(20);
    Instant start = requestDate.atTime(9, 0).atZone(utc).toInstant();
    Instant end = start.plus(2, ChronoUnit.HOURS);

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
                                "Payment due E2E",
                                "requestedFrom",
                                requestDate,
                                "requestedUntil",
                                requestDate,
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

    mockMvc
        .perform(
            post("/api/v1/contracts/requests/{requestId}/claim", requestId)
                .with(principal(managerUserId, "MANAGER")))
        .andExpect(status().isOk());

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
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/contracts/requests/{requestId}/accept", requestId)
                .with(principal(customerUserId, "CUSTOMER")))
        .andExpect(status().isOk());

    return requestId;
  }

  private UUID assignAndPublishReport(UUID requestId, UUID occurrenceId) throws Exception {
    Instant start =
        occurrences.findById(occurrenceId).orElseThrow().getPlannedStartAt() != null
            ? occurrences.findById(occurrenceId).orElseThrow().getPlannedStartAt()
            : Instant.now().plus(20, ChronoUnit.DAYS);
    Instant end = start.plus(2, ChronoUnit.HOURS);

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
                            "fileKey", "permits/p1.pdf",
                            "fileName", "p1.pdf",
                            "permitNumber", "GP-DUE-001",
                            "issuingAuthority", "Cuc Tac chien - BTTM",
                            "validFrom", Instant.now().minus(1, ChronoUnit.HOURS),
                            "validTo", Instant.now().plus(30, ChronoUnit.DAYS)))))
        .andExpect(status().isOk());

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

    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", inspectionId)
                .file(new MockMultipartFile("file", "evidence1.png", "image/png", VALID_PNG_BYTES))
                .param("fieldSessionId", sessionId.toString())
                .with(principal(inspectorUserId, "INSPECTOR")))
        .andExpect(status().isOk());

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

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings/confirm", inspectionId)
                .with(principal(inspectorUserId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("noFindingsAcknowledged", true))))
        .andExpect(status().isOk());

    String draftResp =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/report/draft/manual", inspectionId)
                    .with(principal(inspectorUserId, "INSPECTOR")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID versionId = UUID.fromString(objectMapper.readTree(draftResp).at("/data/id").stringValue());

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/versions/{vId}/verify", inspectionId, versionId)
                .with(principal(inspectorUserId, "INSPECTOR")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/versions/{vId}/submit", inspectionId, versionId)
                .with(principal(inspectorUserId, "INSPECTOR")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/versions/{vId}/review", inspectionId, versionId)
                .with(principal(managerUserId, "MANAGER"))
                .param("approve", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

    return inspectionId;
  }

  private void acceptReport(UUID requestId, UUID occurrenceId) throws Exception {
    UUID versionId =
        UUID.fromString(
            jdbcTemplate.queryForObject(
                "select id::text from inspection_reports where inspection_id = (select inspection_id from service_request_occurrences where id = ?::uuid) order by version_no desc limit 1",
                String.class,
                occurrenceId.toString()));
    mockMvc
        .perform(
            post(
                    "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/accept-report",
                    requestId,
                    occurrenceId)
                .with(principal(customerUserId, "CUSTOMER"))
                .param("reportVersionId", versionId.toString()))
        .andExpect(status().isOk());
  }

  private void configureFinance() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/contracts/admin/finance-settings")
                .with(principal(adminUserId, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("commissionRate", 15.00, "vatRate", 10.00, "tncnRate", 10.00))))
        .andExpect(status().isOk());
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

  private void silently(ThrowingRunnable runnable) {
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

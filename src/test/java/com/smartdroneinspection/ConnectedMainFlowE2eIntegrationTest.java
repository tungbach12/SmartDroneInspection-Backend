package com.smartdroneinspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * End-to-end integration test executing the complete connected lifecycle:
 *
 * <pre>
 * Foundation: Self-Registration & Platform Admin Subscription Activation
 *   -> MF1: Asset Inventory, Operational Default Pair, Inspection Creation (SCHEDULED)
 *   -> MF2: Preparation Shot-List, Flight Permit on File, Field Session Execution (FIELD_COMPLETED)
 *   -> MF3: Evidence Upload, Findings Confirmation, PDF Publication, Per-Finding Handoff (COMPLETED)
 *   -> MF4: Repair Candidate Intake, Work Order, Team Designation, Itemized Estimate, Work Logs, Completion Report, Independent Acceptance & Closure (CLOSED)
 * </pre>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConnectedMainFlowE2eIntegrationTest {

  private static final Logger log =
      LoggerFactory.getLogger(ConnectedMainFlowE2eIntegrationTest.class);

  private static final byte[] VALID_PNG_BYTES =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;
  @Autowired OrganizationRepository organizations;
  @Autowired UserRepository users;
  @Autowired AssetCategoryRepository categories;
  @Autowired AssetRepository assets;
  @Autowired DroneRepository drones;
  @Autowired ServiceRequestRepository serviceRequests;
  @Autowired ServiceRequestOccurrenceRepository occurrences;
  @Autowired InspectionRepository inspections;
  @Autowired FindingRepository findings;
  @Autowired JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("Full-Flow E2E: Execute connected lifecycle from MF1 through MF4")
  void executeCompleteConnectedLifecycleMf1ThroughMf4() throws Exception {
    log.info("================================================================================");
    log.info("STARTING CONNECTED E2E LIFECYCLE: FOUNDATION -> MF1 -> MF2 -> MF3 -> MF4");
    log.info("================================================================================");

    // =========================================================================
    // STEP 0: FOUNDATION - Organization, Subscription, and Workforce Provisioning
    // =========================================================================
    log.info("[FOUNDATION] Step 0.1: Setting up Organization, Platform Admin, and Org Admin...");

    UUID platformAdminId = createPlatformAdmin();
    UUID organizationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, 'SkyView Infrastructure Ltd', 'SkyView Ltd', ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        organizationId,
        "ORG-" + organizationId.toString().substring(0, 18));

    UUID orgAdminId =
        createUser(
            organizationId,
            UserRole.CUSTOMER,
            ActorZone.CUSTOMER_ORGANIZATION,
            "org.admin." + UUID.randomUUID() + "@skyview.test",
            "John OrgAdmin");

    UUID reviewerId =
        createUser(
            organizationId,
            UserRole.CUSTOMER,
            ActorZone.CUSTOMER_ORGANIZATION,
            "reviewer." + UUID.randomUUID() + "@skyview.test",
            "Alice Reviewer");

    log.info(
        "[FOUNDATION] Step 0.2: Platform Admin activates ENTERPRISE subscription for Organization...");
    mockMvc
        .perform(
            post("/api/v1/platform/organizations/{orgId}/subscriptions", organizationId)
                .with(principal(platformAdminId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "planCode",
                            "ENTERPRISE",
                            "billingPeriodMonths",
                            12,
                            "externalReference",
                            "SUB-E2E-2026",
                            "reason",
                            "Annual Enterprise contract activation"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.planCode").value("ENTERPRISE"));

    log.info("[FOUNDATION] Step 0.3: Provisioning Inspector and Maintenance Engineer...");
    UUID inspectorId =
        createUser(
            null,
            UserRole.INSPECTOR,
            ActorZone.INSPECTOR_PARTNER,
            "inspector." + UUID.randomUUID() + "@skyview.test",
            "David Inspector");
    User inspector = users.findDetailedById(inspectorId).orElseThrow();
    inspector.updatePilotLicense(
        "VN-UAV-PILOT-2026",
        "CLASS_A",
        Instant.now().plus(365, ChronoUnit.DAYS),
        "licenses/pilot-cert.pdf");
    users.saveAndFlush(inspector);

    UUID leadEngineerId =
        createUser(
            organizationId,
            UserRole.MAINTENANCE_ENGINEER,
            ActorZone.CUSTOMER_ORGANIZATION,
            "lead.engineer." + UUID.randomUUID() + "@skyview.test",
            "Bob LeadEngineer");

    UUID reportAuthorId =
        createUser(
            organizationId,
            UserRole.MAINTENANCE_ENGINEER,
            ActorZone.CUSTOMER_ORGANIZATION,
            "report.author." + UUID.randomUUID() + "@skyview.test",
            "Charlie ReportAuthor");

    // =========================================================================
    // STEP 1: MF1 - Resource Setup, Inspection Creation & Dispatch
    // =========================================================================
    log.info("[MF1] Step 1.1: Registering Drone DJI Matrice 350 RTK...");
    Drone drone =
        drones.saveAndFlush(
            Drone.createInspectorOwned(
                inspectorId,
                "DJI-M350-" + UUID.randomUUID().toString().substring(0, 8),
                "Matrice 350 RTK",
                "DJI",
                "REG-VN-350",
                "drones/registration.pdf",
                Instant.now().plus(365, ChronoUnit.DAYS)));
    UUID droneId = drone.getId();

    log.info("[MF1] Step 1.2: Registering Asset Category and Asset with Inspection Items...");
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
                organizationId,
                category.getId(),
                "BR-E2E-" + UUID.randomUUID().toString().substring(0, 8),
                "Thu Thiem River Bridge",
                "Cable-stayed steel and concrete bridge",
                "{\"latitude\":10.7712,\"longitude\":106.7082,\"city\":\"Ho Chi Minh City\"}",
                null,
                null,
                null,
                List.of("Girder G4", "Pier 2", "Main stay cables"),
                orgAdminId));
    UUID assetId = asset.getId();

    log.info(
        "[MF2] Step 1.3: Customer requests an inspection and Manager quotes the partner assignment...");
    java.time.ZoneId customerZone = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
    Instant plannedStart =
        java.time.LocalDate.now(customerZone)
            .plusDays(1)
            .atTime(9, 0)
            .atZone(customerZone)
            .toInstant();
    Instant plannedEnd = plannedStart.plus(1, ChronoUnit.HOURS);

    mockMvc
        .perform(
            post("/api/v1/contracts/partners/inspectors")
                .with(principal(platformAdminId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("inspectorUserId", inspectorId))))
        .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/contracts/partners/drones")
                .with(principal(platformAdminId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("droneId", droneId))))
        .andExpect(status().isCreated());
    String requestResponse =
        mockMvc
            .perform(
                post("/api/v1/contracts/requests")
                    .with(principal(orgAdminId, "CUSTOMER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "assetId",
                                assetId,
                                "description",
                                "E2E bridge inspection",
                                "requestedFrom",
                                plannedStart.atZone(customerZone).toLocalDate(),
                                "requestedUntil",
                                plannedStart.atZone(customerZone).toLocalDate(),
                                "timezone",
                                "Asia/Ho_Chi_Minh",
                                "requestType",
                                "ONE_TIME"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(requestResponse).at("/data/id").stringValue());

    mockMvc
        .perform(
            post("/api/v1/contracts/requests/{requestId}/quotes", requestId)
                .with(principal(platformAdminId, "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "scopeSnapshot", "Inspect listed bridge components",
                            "firstOccurrenceStartAt", plannedStart,
                            "firstOccurrenceEndAt", plannedEnd,
                            "inspectorId", inspectorId,
                            "droneId", droneId,
                            "pricePerOccurrenceUsd", 150.00))))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/contracts/requests/{requestId}/accept", requestId)
                .with(principal(orgAdminId, "CUSTOMER"))
                .param("versionNo", "1"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            multipart("/api/v1/contracts/requests/{requestId}/signed-contract", requestId)
                .file(
                    new MockMultipartFile(
                        "file", "signed.pdf", "application/pdf", "%PDF-e2e".getBytes()))
                .with(principal(platformAdminId, "MANAGER")))
        .andExpect(status().isOk());

    ServiceRequestOccurrence occurrence =
        occurrences.findByRequestIdOrderByOccurrenceIndexAsc(requestId).getFirst();
    String assignmentResponse =
        mockMvc
            .perform(
                post(
                        "/api/v1/contracts/requests/{requestId}/occurrences/{occurrenceId}/assign",
                        requestId,
                        occurrence.getId())
                    .with(principal(platformAdminId, "MANAGER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "plannedStartAt", plannedStart,
                                "plannedEndAt", plannedEnd,
                                "inspectorId", inspectorId,
                                "droneId", droneId))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID inspectionId =
        UUID.fromString(
            objectMapper
                .readTree(assignmentResponse)
                .at("/data/occurrences/0/inspectionId")
                .stringValue());
    assertThat(occurrences.findById(occurrence.getId()).orElseThrow().getPlannedStartAt())
        .isEqualTo(plannedStart);
    assertThat(occurrences.findById(occurrence.getId()).orElseThrow().getPlannedEndAt())
        .isEqualTo(plannedEnd);
    assertThat(inspections.findById(inspectionId).orElseThrow().getPlannedStartAt())
        .isEqualTo(plannedStart);
    assertThat(inspections.findById(inspectionId).orElseThrow().getPlannedEndAt())
        .isEqualTo(plannedEnd);
    log.info("[MF2] SUCCESS: Partner inspection assigned with ID {}", inspectionId);

    // =========================================================================
    // STEP 2: MF2 - Flight Permit -> Field Session
    // =========================================================================
    log.info("[MF2] Step 2.1: Inspector uploads Flight Permit -> READY_FOR_FLIGHT...");
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/flight-permit", inspectionId)
                .with(principal(inspectorId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "fileKey", "permits/vca-2026-permit.pdf",
                            "fileName", "vca-2026-permit.pdf"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.data.flightPermitFileName").value("vca-2026-permit.pdf"));

    assertThat(inspections.findById(inspectionId).orElseThrow().getStatus())
        .isEqualTo(InspectionStatus.SCHEDULED);

    log.info("[MF2] Step 2.3: Inspector starts Field Session (status -> IN_PROGRESS)...");
    String startSessionResponse =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/field-sessions", inspectionId)
                    .with(principal(inspectorId, "INSPECTOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "preFlightChecklistNote",
                                "Drone identified; bridge accessible and telemetry nominal"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID fieldSessionId =
        UUID.fromString(objectMapper.readTree(startSessionResponse).at("/data/id").stringValue());
    assertThat(inspections.findById(inspectionId).orElseThrow().getStatus())
        .isEqualTo(InspectionStatus.IN_PROGRESS);

    log.info(
        "[MF2] Step 2.4: Inspector completes Field Session with Outcome Notes -> FIELD_COMPLETED...");
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/field-sessions/{sessionId}/completion",
                    inspectionId,
                    fieldSessionId)
                .with(principal(inspectorId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "outcomeNotes",
                            "All 36 planned high-resolution exposures captured across Girder G4 and Pier 2"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("FIELD_COMPLETED"));

    assertThat(inspections.findById(inspectionId).orElseThrow().getStatus())
        .isEqualTo(InspectionStatus.FIELD_COMPLETED);
    log.info("[MF2] SUCCESS: Inspection transitioned cleanly to FIELD_COMPLETED");

    // =========================================================================
    // STEP 3: MF3 - Evidence Intake, Findings, Report Review, Publication & Corrective Handoff
    // =========================================================================
    log.info("[MF3] Step 3.1: Uploading high-resolution inspection evidence image...");
    MockMultipartFile evidenceFile =
        new MockMultipartFile("file", "girder-g4-corrosion.png", "image/png", VALID_PNG_BYTES);

    String uploadResponse =
        mockMvc
            .perform(
                multipart("/api/v1/inspections/{id}/evidence", inspectionId)
                    .file(evidenceFile)
                    .param("fieldSessionId", fieldSessionId.toString())
                    .with(principal(inspectorId, "INSPECTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isDeleted").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID evidenceId =
        UUID.fromString(objectMapper.readTree(uploadResponse).at("/data/id").stringValue());

    log.info("[MF3] Step 3.2: Active evidence is available without a quality-decision gate.");

    log.info("[MF3] Step 3.3: Inspector records and confirms Finding...");
    String findingResponse =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/findings", inspectionId)
                    .with(principal(inspectorId, "INSPECTOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "evidenceId",
                                evidenceId.toString(),
                                "defectLabel",
                                "Severe Flange Corrosion and Section Loss",
                                "severity",
                                "HIGH",
                                "technicalNotes",
                                "Localized pitting and laminar rust with estimated 12% flange thickness reduction"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID findingId =
        UUID.fromString(objectMapper.readTree(findingResponse).at("/data/id").stringValue());

    log.info("[MF3] Step 3.4: Inspector confirms findings set -> transitions to REPORT_DRAFT...");
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings/confirm", inspectionId)
                .with(principal(inspectorId, "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("noFindingsAcknowledged", false))))
        .andExpect(status().isOk());

    log.info("[MF3] Step 3.5: Inspector drafts, verifies and submits Inspection Report...");
    var draftResult =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/report/draft/manual", inspectionId)
                    .with(principal(inspectorId, "INSPECTOR")))
            .andReturn();
    if (draftResult.getResponse().getStatus() != 200) {
      log.error(
          "Manual draft creation failed: status={}, body={}",
          draftResult.getResponse().getStatus(),
          draftResult.getResponse().getContentAsString());
    }
    assertThat(draftResult.getResponse().getStatus()).isEqualTo(200);
    String reportDraftResponse = draftResult.getResponse().getContentAsString();

    UUID reportVersionId =
        UUID.fromString(objectMapper.readTree(reportDraftResponse).at("/data/id").stringValue());

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vId}/submit",
                    inspectionId,
                    reportVersionId)
                .with(principal(inspectorId, "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

    log.info(
        "[MF3] Step 3.6: Independent Reviewer approves and publishes Report -> REPORT_PUBLISHED...");
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vId}/review",
                    inspectionId,
                    reportVersionId)
                .param("approve", "true")
                .with(principal(reviewerId, "CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPROVED"));

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vId}/publish",
                    inspectionId,
                    reportVersionId)
                .with(principal(reviewerId, "CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inspectionStatus").value("COMPLETED"));

    assertThat(inspections.findById(inspectionId).orElseThrow().getStatus())
        .isEqualTo(InspectionStatus.COMPLETED);

    log.info("[MF3] Step 3.7: Forwarding the confirmed finding to Maintenance...");
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/corrective-handoff", inspectionId)
                .with(principal(reviewerId, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("findingIds", List.of(findingId)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inspectionStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.forwardedFindingIds[0]").value(findingId.toString()));

    assertThat(inspections.findById(inspectionId).orElseThrow().getStatus())
        .isEqualTo(InspectionStatus.COMPLETED);
    log.info(
        "[MF3] SUCCESS: Report published and corrective findings handed off with status COMPLETED");

    // =========================================================================
    // STEP 4: MF4 - Repair Candidates, Work Order, Team, Tasks, Estimates, Work Logs & Closure
    // =========================================================================
    log.info("[MF4] Step 4.1: Checking Repair Candidates Intake...");
    mockMvc
        .perform(
            get("/api/v1/maintenance/repair-candidates").with(principal(orgAdminId, "CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[?(@.findingId == '%s')]", findingId).exists());

    log.info("[MF4] Step 4.2: Opening Work Order from published Finding...");
    String workOrderResponse =
        mockMvc
            .perform(
                post("/api/v1/maintenance/work-orders")
                    .with(principal(orgAdminId, "CUSTOMER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "assetId",
                                assetId.toString(),
                                "sourceReportVersionId",
                                reportVersionId.toString(),
                                "sourceFindingId",
                                findingId.toString(),
                                "budgetApproverUserId",
                                orgAdminId.toString(),
                                "priority",
                                "HIGH",
                                "correctiveScope",
                                "{\"summary\":\"Abrasive blast cleaning, primer coating and structural polyurethane application\"}",
                                "acceptanceCriteria",
                                "{\"checks\":[\"coating adhesion test\",\"dry film thickness >= 200um\"]}"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID workOrderId =
        UUID.fromString(objectMapper.readTree(workOrderResponse).at("/data/id").stringValue());

    log.info("[MF4] Step 4.3: Assigning Maintenance Team with Separation of Duties...");
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/team", workOrderId)
                .with(principal(orgAdminId, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "leadUserId", leadEngineerId.toString(),
                            "reportAuthorUserId", reportAuthorId.toString(),
                            "acceptingReviewerUserId", reviewerId.toString()))))
        .andExpect(status().isOk());

    log.info("[MF4] Step 4.4: Planning Maintenance Tasks...");
    String taskResponse =
        mockMvc
            .perform(
                post("/api/v1/maintenance/work-orders/{id}/tasks", workOrderId)
                    .with(principal(leadEngineerId, "MAINTENANCE_ENGINEER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("name", "Grit blast and apply 3-coat marine epoxy system"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID taskId = UUID.fromString(objectMapper.readTree(taskResponse).at("/data/id").stringValue());

    log.info("[MF4] Step 4.5: Submitting and Approving Itemized Cost Estimate...");
    Map<String, Object> materialEstimateLine =
        Map.of(
            "taskId",
            taskId.toString(),
            "lineKind",
            "MATERIAL",
            "description",
            "Marine grade zinc-rich primer & epoxy topcoat",
            "quantity",
            5,
            "unit",
            "liter",
            "unitRate",
            450000);

    Map<String, Object> laborEstimateLine =
        Map.of(
            "taskId",
            taskId.toString(),
            "lineKind",
            "LABOR",
            "description",
            "Certified coating applicator labor",
            "quantity",
            8,
            "unit",
            "hour",
            "unitRate",
            250000);

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions", workOrderId)
                .with(principal(leadEngineerId, "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "currency",
                            "VND",
                            "snapshot",
                            "{\"notes\":\"Approved standard rate card\"}",
                            "lines",
                            List.of(materialEstimateLine, laborEstimateLine)))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions/1/submit", workOrderId)
                .with(principal(leadEngineerId, "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/estimate-versions/1/approve", workOrderId)
                .with(principal(orgAdminId, "CUSTOMER")))
        .andExpect(status().isOk());

    log.info("[MF4] Step 4.6: Releasing and Starting Work Order...");
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/release", workOrderId)
                .with(principal(leadEngineerId, "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("READY"));

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/start", workOrderId)
                .with(principal(leadEngineerId, "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

    log.info("[MF4] Step 4.7: Submitting and Verifying Work Logs...");
    String logResponse =
        mockMvc
            .perform(
                post("/api/v1/maintenance/work-orders/{id}/work-logs", workOrderId)
                    .with(principal(leadEngineerId, "MAINTENANCE_ENGINEER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "taskId",
                                taskId.toString(),
                                "startedAt",
                                Instant.now().minusSeconds(14400).toString(),
                                "endedAt",
                                Instant.now().minusSeconds(3600).toString(),
                                "hours",
                                3,
                                "asLeftCondition",
                                "Surface cleaned to Sa 2.5 and primed",
                                "testReadings",
                                "{\"dryFilmThicknessUm\":220,\"ambientTempC\":29.5}"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID workLogId =
        UUID.fromString(objectMapper.readTree(logResponse).at("/data/id").stringValue());

    mockMvc
        .perform(
            post(
                    "/api/v1/maintenance/work-orders/{id}/work-logs/{logId}/submit",
                    workOrderId,
                    workLogId)
                .with(principal(leadEngineerId, "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post(
                    "/api/v1/maintenance/work-orders/{id}/work-logs/{logId}/verify",
                    workOrderId,
                    workLogId)
                .with(principal(leadEngineerId, "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("VERIFIED"));

    log.info("[MF4] Step 4.8: Lead Engineer declares execution complete...");
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/declare-complete", workOrderId)
                .with(principal(leadEngineerId, "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("WORK_COMPLETED"));

    log.info("[MF4] Step 4.9: Authoring, Verifying and Submitting Completion Report...");
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/reports", workOrderId)
                .with(principal(reportAuthorId, "MAINTENANCE_ENGINEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "contentSnapshot",
                            "{\"summary\":\"Girder G4 surface remediated, protective coating applied and DFT verified\",\"warrantyTerms\":\"5-year anti-corrosion guarantee\"}",
                            "approvedScopeHash",
                            "scope-hash-e2e",
                            "workLogSnapshotHash",
                            "work-log-hash-e2e",
                            "actualCostSnapshotHash",
                            "actual-cost-hash-e2e"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DRAFT"));

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/reports/1/verify", workOrderId)
                .with(principal(reportAuthorId, "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("AUTHOR_VERIFIED"));

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/reports/1/submit", workOrderId)
                .with(principal(reportAuthorId, "MAINTENANCE_ENGINEER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

    log.info("[MF4] Step 4.10: Independent Acceptance Decision (ACCEPTED)...");
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/acceptance-decisions", workOrderId)
                .with(principal(reviewerId, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "decision",
                            "ACCEPTED",
                            "technicalComments",
                            "Coating adhesion and DFT within spec; no blister or pinhole defects",
                            "acceptanceChecklist",
                            "{\"adhesionTest\":\"Pass\",\"dryFilmThickness\":\"220um\"}",
                            "testResult",
                            "{\"status\":\"PASSED\"}"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.decision").value("ACCEPTED"));

    log.info("[MF4] Step 4.11: Cost Reconciliation against Approved Baseline...");
    Map<String, Object> actualMaterialLine =
        Map.of(
            "taskId", taskId.toString(),
            "lineKind", "MATERIAL",
            "description", "Actual marine grade primer & epoxy topcoat",
            "quantity", 5,
            "unit", "liter",
            "unitRate", 440000,
            "currency", "VND");

    Map<String, Object> actualLaborLine =
        Map.of(
            "taskId", taskId.toString(),
            "lineKind", "LABOR",
            "description", "Actual coating labor",
            "quantity", 8,
            "unit", "hour",
            "unitRate", 250000,
            "currency", "VND");

    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/cost-reconciliation", workOrderId)
                .with(principal(reviewerId, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("actualLines", List.of(actualMaterialLine, actualLaborLine)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.approvedBaselineTotal").value(4250000.0))
        .andExpect(jsonPath("$.data.actualTotal").value(4200000.0))
        .andExpect(jsonPath("$.data.variance").value(-50000.0));

    log.info("[MF4] Step 4.12: Formal Work Order Closure -> CLOSED...");
    mockMvc
        .perform(
            post("/api/v1/maintenance/work-orders/{id}/close", workOrderId)
                .with(principal(reviewerId, "CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CLOSED"));

    log.info("================================================================================");
    log.info("COMPLETED CONNECTED E2E LIFECYCLE: ALL GATES VERIFIED FROM MF1 TO MF4!");
    log.info("================================================================================");
  }

  // =========================================================================
  // HELPER METHODS
  // =========================================================================

  private UUID createPlatformAdmin() {
    User admin =
        new User(
            "platform.admin." + UUID.randomUUID() + "@smartdrone.test",
            "Platform Administrator",
            null,
            UserStatus.ACTIVE,
            ActorZone.PLATFORM,
            null);
    admin.addRole(UserRole.MANAGER);
    return users.saveAndFlush(admin).getId();
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

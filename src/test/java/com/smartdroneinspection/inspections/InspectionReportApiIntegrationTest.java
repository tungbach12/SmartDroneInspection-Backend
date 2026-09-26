package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionAssignmentRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionQuotationRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionRequestRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionServiceOrderRepository;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.enums.EvidenceKind;
import com.smartdroneinspection.inspections.domain.enums.EvidenceSource;
import com.smartdroneinspection.inspections.domain.enums.UploadStatus;
import com.smartdroneinspection.inspections.events.ReportAcceptedEvent;
import com.smartdroneinspection.inspections.repository.ChecklistResponseRepository;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.ReportVersionRepository;
import com.smartdroneinspection.inspections.service.InspectionService;
import com.smartdroneinspection.inspections.spi.EvidenceObjectStore;
import com.smartdroneinspection.users.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@RecordApplicationEvents
class InspectionReportApiIntegrationTest {

  private static final byte[] ONE_PIXEL_PNG =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/o2cAAAAASUVORK5CYII=");

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ApplicationEvents applicationEvents;
  @Autowired InspectionService inspectionService;
  @Autowired InspectionReportRepository reports;
  @Autowired ReportVersionRepository versions;
  @Autowired ChecklistResponseRepository checklistResponses;
  @Autowired EvidenceRepository evidence;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired InspectionRequestRepository requests;
  @Autowired InspectionQuotationRepository quotations;
  @Autowired InspectionServiceOrderRepository serviceOrders;
  @Autowired InspectionAssignmentRepository assignments;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @MockitoBean EvidenceObjectStore objectStore;

  private final Map<String, byte[]> storedObjects = new ConcurrentHashMap<>();
  private InspectionFixture.Data fixture;
  private UUID inspectionId;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() throws Exception {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    storedObjects.clear();
    org.mockito.Mockito.when(objectStore.open(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation -> new ByteArrayInputStream(storedObjects.get(invocation.getArgument(0))));
    fixture =
        new InspectionFixture(
                categories,
                templates,
                assets,
                requests,
                quotations,
                serviceOrders,
                assignments,
                users,
                jdbcTemplate)
            .create();
    inspectionId =
        inspectionService.start(fixture.inspectorId(), fixture.assignmentId()).inspectionId();
    inspectionService.saveChecklistResponse(
        fixture.inspectorId(),
        inspectionId,
        fixture.checklistItemId(),
        new com.smartdroneinspection.inspections.api.dto.request.ChecklistResponseRequest(
            new tools.jackson.databind.ObjectMapper().readTree("{\"value\":\"PASS\"}"),
            "Surface condition checked"));
    String evidenceKey = "reports/" + UUID.randomUUID();
    storedObjects.put(evidenceKey, ONE_PIXEL_PNG);
    evidence.saveAndFlush(
        new Evidence(
            inspectionId,
            null,
            fixture.inspectorId(),
            EvidenceKind.INSPECTION,
            "north-span.png",
            "image/png",
            ONE_PIXEL_PNG.length,
            "a".repeat(64),
            evidenceKey,
            EvidenceSource.WEB_UPLOAD,
            UploadStatus.AVAILABLE));
  }

  @Test
  void returnsSecurityFailuresAsProblemDetailsRatherThanSuccessEnvelopes() throws Exception {
    mockMvc
        .perform(get("/api/v1/inspections/assignments"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value("Unauthorized"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.detail").value("Authentication is required."))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
        .andExpect(jsonPath("$.traceId").isNotEmpty())
        .andExpect(jsonPath("$.success").doesNotExist());
  }

  @Test
  void enforcesPeerReviewReleaseClientScopeAndImmutableAcceptance() throws Exception {
    MvcResult draftResult =
        mockMvc
            .perform(
                post("/api/v1/inspections/{inspectionId}/report", inspectionId)
                    .with(inspector(fixture.inspectorId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.versionNumber").value(1))
            .andReturn();
    UUID reportId = uuid(draftResult, "$.data.reportId");
    UUID firstVersionId = uuid(draftResult, "$.data.versionId");

    mockMvc
        .perform(get("/api/v1/reports").with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isEmpty());

    mockMvc
        .perform(
            put(
                    "/api/v1/reports/{reportId}/versions/{versionId}/reviewer",
                    reportId,
                    firstVersionId)
                .contentType("application/json")
                .content("{\"reviewerId\":\"" + fixture.otherInspectorId() + "\"}")
                .with(manager(fixture.managerId())))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(
                    "/api/v1/reports/{reportId}/versions/{versionId}/submit-review",
                    reportId,
                    firstVersionId)
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.versionStatus").value("AWAITING_PEER_REVIEW"));
    mockMvc
        .perform(
            post("/api/v1/reports/{reportId}/versions/{versionId}/review", reportId, firstVersionId)
                .contentType("application/json")
                .content("{\"decision\":\"APPROVED\"}")
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("REPORT_SCOPE_DENIED"));
    mockMvc
        .perform(
            post("/api/v1/reports/{reportId}/versions/{versionId}/review", reportId, firstVersionId)
                .contentType("application/json")
                .content("{\"decision\":\"APPROVED\"}")
                .with(inspector(fixture.otherInspectorId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.versionStatus").value("TECHNICALLY_APPROVED"));
    mockMvc
        .perform(
            post(
                    "/api/v1/reports/{reportId}/versions/{versionId}/release",
                    reportId,
                    firstVersionId)
                .with(manager(fixture.managerId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.versionStatus").value("RELEASED"));

    mockMvc
        .perform(get("/api/v1/reports/{reportId}", reportId).with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.review").doesNotExist());
    UUID evidenceId = evidence.findAll().getFirst().getId();
    mockMvc
        .perform(
            get(
                    "/api/v1/reports/{reportId}/versions/{versionId}/evidence/{evidenceId}/content",
                    reportId,
                    firstVersionId,
                    evidenceId)
                .with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentAsByteArray())
                    .containsExactly(ONE_PIXEL_PNG));

    mockMvc
        .perform(
            post(
                    "/api/v1/reports/{reportId}/versions/{versionId}/client-decision",
                    reportId,
                    firstVersionId)
                .contentType("application/json")
                .content(
                    "{\"decision\":\"REQUEST_REVISION\","
                        + "\"reason\":\"Clarify the surface rating\"}")
                .with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clientDecisionReason").value("Clarify the surface rating"));
    MvcResult revisionResult =
        mockMvc
            .perform(
                post("/api/v1/reports/{reportId}/versions", reportId)
                    .with(inspector(fixture.inspectorId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.versionNumber").value(2))
            .andReturn();
    UUID revisionId = uuid(revisionResult, "$.data.versionId");
    mockMvc
        .perform(
            put("/api/v1/reports/{reportId}/versions/{versionId}/reviewer", reportId, revisionId)
                .contentType("application/json")
                .content("{\"reviewerId\":\"" + fixture.otherInspectorId() + "\"}")
                .with(manager(fixture.managerId())))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(
                    "/api/v1/reports/{reportId}/versions/{versionId}/submit-review",
                    reportId,
                    revisionId)
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/reports/{reportId}/versions/{versionId}/review", reportId, revisionId)
                .contentType("application/json")
                .content("{\"decision\":\"APPROVED\"}")
                .with(inspector(fixture.otherInspectorId())))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/reports/{reportId}/versions/{versionId}/release", reportId, revisionId)
                .with(manager(fixture.managerId())))
        .andExpect(status().isOk());

    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc
          .perform(
              post(
                      "/api/v1/reports/{reportId}/versions/{versionId}/client-decision",
                      reportId,
                      revisionId)
                  .contentType("application/json")
                  .content("{\"decision\":\"ACCEPT\"}")
                  .with(client(fixture.clientId())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.versionStatus").value("ACCEPTED"));
    }
    assertThat(versions.findById(revisionId).orElseThrow().isImmutable()).isTrue();
    assertThat(applicationEvents.stream(ReportAcceptedEvent.class).count()).isEqualTo(1);
  }

  @Test
  void allowsAuthorToUpdateNarrativeAndDeniesUnauthorizedActors() throws Exception {
    MvcResult draftResult =
        mockMvc
            .perform(
                post("/api/v1/inspections/{inspectionId}/report", inspectionId)
                    .with(inspector(fixture.inspectorId())))
            .andExpect(status().isCreated())
            .andReturn();
    UUID reportId = uuid(draftResult, "$.data.reportId");
    UUID versionId = uuid(draftResult, "$.data.versionId");

    // Author can update narrative
    mockMvc
        .perform(
            put("/api/v1/reports/{reportId}/versions/{versionId}/narrative", reportId, versionId)
                .contentType("application/json")
                .content("{\"text\":\"Concrete deck in good condition.\"}")
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(
            jsonPath("$.data.contentSnapshot.aiDraftNarrative")
                .value("Concrete deck in good condition."))
        .andExpect(jsonPath("$.data.contentSnapshot.aiDraftModel").doesNotExist());

    // Other inspector is denied
    mockMvc
        .perform(
            put("/api/v1/reports/{reportId}/versions/{versionId}/narrative", reportId, versionId)
                .contentType("application/json")
                .content("{\"text\":\"Hacked narrative.\"}")
                .with(inspector(fixture.otherInspectorId())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("REPORT_SCOPE_DENIED"));

    // Blank narrative is rejected at the controller boundary
    mockMvc
        .perform(
            put("/api/v1/reports/{reportId}/versions/{versionId}/narrative", reportId, versionId)
                .contentType("application/json")
                .content("{\"text\":\"   \"}")
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void clientAcceptanceMarksInspectionStatusCompletedInDatabase() throws Exception {
    MvcResult draftResult =
        mockMvc
            .perform(
                post("/api/v1/inspections/{inspectionId}/report", inspectionId)
                    .with(inspector(fixture.inspectorId())))
            .andExpect(status().isCreated())
            .andReturn();
    UUID reportId = uuid(draftResult, "$.data.reportId");
    UUID versionId = uuid(draftResult, "$.data.versionId");

    mockMvc
        .perform(
            put("/api/v1/reports/{reportId}/versions/{versionId}/reviewer", reportId, versionId)
                .contentType("application/json")
                .content("{\"reviewerId\":\"" + fixture.otherInspectorId() + "\"}")
                .with(manager(fixture.managerId())))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(
                    "/api/v1/reports/{reportId}/versions/{versionId}/submit-review",
                    reportId,
                    versionId)
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/reports/{reportId}/versions/{versionId}/review", reportId, versionId)
                .contentType("application/json")
                .content("{\"decision\":\"APPROVED\"}")
                .with(inspector(fixture.otherInspectorId())))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/reports/{reportId}/versions/{versionId}/release", reportId, versionId)
                .with(manager(fixture.managerId())))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post(
                    "/api/v1/reports/{reportId}/versions/{versionId}/client-decision",
                    reportId,
                    versionId)
                .contentType("application/json")
                .content("{\"decision\":\"ACCEPT\"}")
                .with(client(fixture.clientId())))
        .andExpect(status().isOk());

    String inspectionStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM inspections WHERE id = ?", String.class, inspectionId);
    assertThat(inspectionStatus).isEqualTo("COMPLETED");
  }

  @Test
  void rejectsNarrativeUpdateAndAiDraftOnNonDraftVersion() throws Exception {
    MvcResult draftResult =
        mockMvc
            .perform(
                post("/api/v1/inspections/{inspectionId}/report", inspectionId)
                    .with(inspector(fixture.inspectorId())))
            .andExpect(status().isCreated())
            .andReturn();
    UUID reportId = uuid(draftResult, "$.data.reportId");
    UUID versionId = uuid(draftResult, "$.data.versionId");

    mockMvc
        .perform(
            put("/api/v1/reports/{reportId}/versions/{versionId}/reviewer", reportId, versionId)
                .contentType("application/json")
                .content("{\"reviewerId\":\"" + fixture.otherInspectorId() + "\"}")
                .with(manager(fixture.managerId())))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(
                    "/api/v1/reports/{reportId}/versions/{versionId}/submit-review",
                    reportId,
                    versionId)
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            put("/api/v1/reports/{reportId}/versions/{versionId}/narrative", reportId, versionId)
                .contentType("application/json")
                .content("{\"text\":\"Late narrative.\"}")
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REPORT_STATE_CONFLICT"));
    mockMvc
        .perform(
            post("/api/v1/reports/{reportId}/versions/{versionId}/ai-draft", reportId, versionId)
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REPORT_STATE_CONFLICT"));
  }

  @Test
  void appliesTheConfiguredNarrativeLimitAtTheApiBoundary() throws Exception {
    MvcResult draftResult =
        mockMvc
            .perform(
                post("/api/v1/inspections/{inspectionId}/report", inspectionId)
                    .with(inspector(fixture.inspectorId())))
            .andExpect(status().isCreated())
            .andReturn();
    UUID reportId = uuid(draftResult, "$.data.reportId");
    UUID versionId = uuid(draftResult, "$.data.versionId");

    mockMvc
        .perform(
            put("/api/v1/reports/{reportId}/versions/{versionId}/narrative", reportId, versionId)
                .contentType("application/json")
                .content("{\"text\":\"" + "x".repeat(10000) + "\"}")
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            put("/api/v1/reports/{reportId}/versions/{versionId}/narrative", reportId, versionId)
                .contentType("application/json")
                .content("{\"text\":\"" + "x".repeat(10001) + "\"}")
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("REPORT_DRAFT_INVALID"));
  }

  @Test
  void revisionOmmitsNarrativeFromRecomposedSnapshot() throws Exception {
    MvcResult draftResult =
        mockMvc
            .perform(
                post("/api/v1/inspections/{inspectionId}/report", inspectionId)
                    .with(inspector(fixture.inspectorId())))
            .andExpect(status().isCreated())
            .andReturn();
    UUID reportId = uuid(draftResult, "$.data.reportId");
    UUID versionId = uuid(draftResult, "$.data.versionId");

    mockMvc
        .perform(
            put("/api/v1/reports/{reportId}/versions/{versionId}/narrative", reportId, versionId)
                .contentType("application/json")
                .content("{\"text\":\"Draft narrative pinned to version 1.\"}")
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.contentSnapshot.aiDraftNarrative")
                .value("Draft narrative pinned to version 1."));

    mockMvc
        .perform(
            put("/api/v1/reports/{reportId}/versions/{versionId}/reviewer", reportId, versionId)
                .contentType("application/json")
                .content("{\"reviewerId\":\"" + fixture.otherInspectorId() + "\"}")
                .with(manager(fixture.managerId())))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(
                    "/api/v1/reports/{reportId}/versions/{versionId}/submit-review",
                    reportId,
                    versionId)
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/reports/{reportId}/versions/{versionId}/review", reportId, versionId)
                .contentType("application/json")
                .content("{\"decision\":\"APPROVED\"}")
                .with(inspector(fixture.otherInspectorId())))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/reports/{reportId}/versions/{versionId}/release", reportId, versionId)
                .with(manager(fixture.managerId())))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(
                    "/api/v1/reports/{reportId}/versions/{versionId}/client-decision",
                    reportId,
                    versionId)
                .contentType("application/json")
                .content(
                    "{\"decision\":\"REQUEST_REVISION\","
                        + "\"reason\":\"Clarify the surface rating\"}")
                .with(client(fixture.clientId())))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/reports/{reportId}/versions", reportId)
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.versionNumber").value(2))
        .andExpect(jsonPath("$.data.contentSnapshot.aiDraftNarrative").doesNotExist())
        .andExpect(jsonPath("$.data.contentSnapshot.aiDraftModel").doesNotExist());
  }

  private UUID uuid(MvcResult result, String expression) throws Exception {
    return UUID.fromString(
        com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), expression)
            .toString());
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor inspector(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_INSPECTOR"));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor manager(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_SERVICE_MANAGER"));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor client(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CLIENT"));
  }
}

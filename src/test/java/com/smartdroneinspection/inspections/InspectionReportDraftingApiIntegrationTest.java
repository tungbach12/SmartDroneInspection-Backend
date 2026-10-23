package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.AiInferencePort;
import com.smartdroneinspection.inspections.spi.ReportDraftPort;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.DraftContext;
import com.smartdroneinspection.users.repository.UserRepository;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * MF3-07/MF3-08 automated drafting over the real HTTP surface, with the model stubbed.
 *
 * <p>The seam is what an Inspector observes: drafting proposes values for the report's sections,
 * and nothing else changes. The assertions that matter here are the negative ones -- a draft must
 * not create a version as a side effect of a later call, must not touch a reviewed finding, and a
 * failed generation must leave the record alone rather than writing a placeholder.
 */
@SpringBootTest
@Import({
  TestcontainersConfiguration.class,
  InspectionReportDraftingApiIntegrationTest.DraftingStub.class
})
class InspectionReportDraftingApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;
  @Autowired InspectionRepository inspections;
  @Autowired EvidenceRepository evidence;
  @Autowired FieldSessionRepository fieldSessions;
  @Autowired FindingRepository findings;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @MockitoBean AiInferencePort inferencePort;

  private InspectionTestFixture.Data fixture;
  private MockMvc mockMvc;

  /**
   * The model provider is stubbed, the object store is not: drafting must never reach storage, and
   * asserting that against a real adapter proves the path is genuinely unwired rather than
   * trivially satisfied by a stub that ignores writes.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class DraftingStub {
    static final AtomicBoolean FAIL_NEXT = new AtomicBoolean(false);
    static final AtomicReference<DraftContext> LAST_CONTEXT = new AtomicReference<>();

    @Bean
    ReportDraftPort reportDraftPort() {
      return new ReportDraftPort() {
        @Override
        public String modelName() {
          return "stub-drafting-model";
        }

        @Override
        public SectionDraft draftSections(DraftContext context) throws IOException {
          LAST_CONTEXT.set(context);
          if (FAIL_NEXT.get()) {
            throw new IOException("stubbed generation failure");
          }
          return new SectionDraft(
              "Proposed executive summary for "
                  + context.findings().size()
                  + " confirmed findings.",
              null,
              "Visual and thermal optical survey at 15m AGL.",
              "Close inspection of the affected chord within 90 days.",
              "POOR",
              "Submerged components were not observed.");
        }
      };
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
    DraftingStub.FAIL_NEXT.set(false);
    DraftingStub.LAST_CONTEXT.set(null);
    fixture = new InspectionTestFixture(users, inspections, jdbcTemplate).create();
    jdbcTemplate.update(
        "UPDATE inspections SET status = 'FIELD_COMPLETED' WHERE id = ?", fixture.inspectionId());
    evidence.saveAndFlush(testEvidence());
  }

  @Test
  void reportDraftCannotBeGeneratedBeforeFindingsConfirmation() throws Exception {
    long versionsBefore = countVersions();
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/draft", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FINDINGS_NOT_CONFIRMED"));
    assertThat(countVersions()).isEqualTo(versionsBefore);
  }

  @Test
  void draftingProposesSectionValuesThatTheAuthorCanReadBack() throws Exception {
    UUID versionId = draft();

    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{versionId}",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.sections.executiveSummary")
                .value("Proposed executive summary for 0 confirmed findings."))
        .andExpect(jsonPath("$.data.sections.purposeScopeCriteria").doesNotExist())
        .andExpect(jsonPath("$.data.sections.overallCondition").value("POOR"))
        .andExpect(
            jsonPath("$.data.sections.omissionDisclosure")
                .value("Submerged components were not observed."));
  }

  @Test
  void draftingReceivesInspectionScopeAndCriteriaFromStoredInspection() throws Exception {
    draft();

    ReportDraftPort.DraftContext context = DraftingStub.LAST_CONTEXT.get();
    assertThat(context.inspection().objective()).isEqualTo("Visual inspection of the main span");
    assertThat(objectMapper.readTree(context.inspection().scope()))
        .isEqualTo(objectMapper.readTree("{\"method\":\"visual\"}"));
    assertThat(objectMapper.readTree(context.inspection().componentScope()))
        .isEqualTo(objectMapper.readTree("[\"main span\"]"));
    assertThat(objectMapper.readTree(context.inspection().acceptanceCriteria()))
        .isEqualTo(objectMapper.readTree("{\"criteria\":\"no visible structural damage\"}"));
    assertThat(context.checklistResponses()).isEmpty();
  }

  @Test
  void draftIncludesStoredChecklistAnswersOnlyForSessionTemplateItems() throws Exception {
    UUID templateId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO checklist_templates (id, template_key, version_number, name, status, created_by_user_id, published_at, created_at, updated_at, row_version) "
            + "VALUES (?, 'REPORT-CHECK', 1, 'Report checklist', 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
        templateId,
        fixture.inspectorId());
    jdbcTemplate.update(
        "INSERT INTO checklist_items (id, template_id, item_code, prompt, prompt_vi, response_type, required, display_order, created_at) "
            + "VALUES (?, ?, 'ACCESS', 'Was the span accessible?', 'Nhịp cầu có thể tiếp cận không?', 'PASS_FAIL', TRUE, 0, CURRENT_TIMESTAMP)",
        itemId,
        templateId);
    UUID sessionId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO field_sessions (id, inspection_id, organization_id, inspector_user_id, status, checklist_template_id, checklist_version, created_at, updated_at, row_version) "
            + "VALUES (?, ?, ?, ?, 'POSTPONED', ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
        sessionId,
        fixture.inspectionId(),
        fixture.organizationId(),
        fixture.inspectorId(),
        templateId);
    jdbcTemplate.update(
        "INSERT INTO checklist_responses (id, inspection_id, checklist_item_id, response_value, notes, completed_by_user_id, completed_at, context, template_version, item_version, responder_user_id, answer, field_session_id, created_at, updated_at) "
            + "VALUES (?, ?, ?, '\"PASS\"'::jsonb, 'Clear access', ?, CURRENT_TIMESTAMP, 'FIELD_SESSION', 1, 1, ?, '\"PASS\"'::jsonb, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        UUID.randomUUID(),
        fixture.inspectionId(),
        itemId,
        fixture.inspectorId(),
        fixture.inspectorId(),
        sessionId);

    draft("vi");

    assertThat(DraftingStub.LAST_CONTEXT.get().locale()).isEqualTo(Locale.forLanguageTag("vi"));
    assertThat(DraftingStub.LAST_CONTEXT.get().checklistResponses())
        .singleElement()
        .satisfies(
            response -> {
              assertThat(response.prompt()).isEqualTo("Nhịp cầu có thể tiếp cận không?");
              assertThat(response.responseValue()).isEqualTo("PASS");
              assertThat(response.notes()).isEqualTo("Clear access");
            });
  }

  @Test
  void draftingLoadsTheDetectorProvenanceAndSessionsByInspection() throws Exception {
    UUID evidenceId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO evidence (id, inspection_id, organization_id, uploaded_by_user_id, evidence_kind,
                              file_name, content_type, size_bytes, checksum_sha256, object_key, source,
                              upload_status, created_at)
        VALUES (?, ?, ?, ?, 'INSPECTION', 'candidate-image.jpg', 'image/jpeg', 100, ?, ?,
                'WEB_UPLOAD', 'AVAILABLE', CURRENT_TIMESTAMP)
        """,
        evidenceId,
        fixture.inspectionId(),
        fixture.organizationId(),
        fixture.inspectorId(),
        "0".repeat(64),
        "test/" + UUID.randomUUID());
    UUID candidateId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO findings
          (id, inspection_id, evidence_id, created_by_user_id, finding_code, defect_label,
           severity, bounding_box, status, inspector_confirmed_at, confirmed_by_user_id,
           model_provider, model_name, model_version, confidence,
           technical_notes, repair_required, corrective_action_required, created_at, updated_at, row_version)
        VALUES (?, ?, ?, ?, 'F-1', 'Possible crack', 'MEDIUM', '{"x":12}', 'CONFIRMED',
                CURRENT_TIMESTAMP, ?, 'provider-test', 'detector-test', 'v7', 0.87, 'Possible crack',
                FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        candidateId,
        fixture.inspectionId(),
        evidenceId,
        fixture.inspectorId(),
        fixture.inspectorId());
    UUID sessionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO field_sessions
          (id, inspection_id, organization_id, inspector_user_id, drone_id, readiness_decision_id,
           status, started_at, ended_at, checklist_template_id, checklist_version, readiness_version,
           limitations, created_at, updated_at, row_version)
        VALUES (?, ?, ?, ?, NULL, NULL, 'POSTPONED', '2026-10-12T10:00:00Z',
                '2026-10-12T10:30:00Z', NULL, NULL, NULL, '{"wind":"high"}',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        sessionId,
        fixture.inspectionId(),
        fixture.organizationId(),
        fixture.inspectorId());

    draft();

    DraftContext context = DraftingStub.LAST_CONTEXT.get();
    // The detector's provenance travels with the finding, so the drafting model is told which
    // findings came from the detector and how confident it was.
    assertThat(context.candidates()).hasSize(1);
    assertThat(context.candidates().get(0).candidateId()).isEqualTo(candidateId.toString());
    assertThat(context.candidates().get(0).status()).isEqualTo("CONFIRMED");
    assertThat(context.candidates().get(0).provider()).isEqualTo("provider-test");
    assertThat(objectMapper.readTree(context.candidates().get(0).boundingBoxJson()))
        .isEqualTo(objectMapper.readTree("{\"x\":12}"));
    assertThat(context.findings()).hasSize(1);
    assertThat(context.findings().get(0).defectLabel()).isEqualTo("Possible crack");
    assertThat(context.fieldSessions())
        .singleElement()
        .satisfies(
            session -> {
              assertThat(session.sessionId()).isEqualTo(sessionId.toString());
              assertThat(session.status()).isEqualTo("POSTPONED");
              assertThat(session.startedAt()).isEqualTo("2026-10-12T10:00:00Z");
              assertThat(session.endedAt()).isEqualTo("2026-10-12T10:30:00Z");
              assertThat(session.assignedDroneId()).isNull();
            });
  }

  @Test
  void reportVersionContractUsesSubmissionAndReturnFieldsOnly() throws Exception {
    UUID versionId = draft();

    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{versionId}",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.submittedAt").doesNotExist())
        .andExpect(jsonPath("$.data.returnReason").doesNotExist())
        .andExpect(jsonPath("$.data.llmModel").doesNotExist())
        .andExpect(jsonPath("$.data.promptVersion").doesNotExist())
        .andExpect(jsonPath("$.data.generatedAt").doesNotExist())
        .andExpect(jsonPath("$.data.evidenceSnapshotHash").doesNotExist())
        .andExpect(jsonPath("$.data.findingsSnapshotHash").doesNotExist());
  }

  @Test
  void draftingNeverAltersAnInspectorConfirmedFinding() throws Exception {
    UUID findingId = insertConfirmedFinding("Corrosion", "HIGH");

    String severityBefore = findingSeverity();
    String confirmedAtBefore = findingInspectorConfirmedAt();
    long findingsBefore = countFindings();

    draft();

    assertThat(countFindings()).isEqualTo(findingsBefore);
    assertThat(findingSeverity()).isEqualTo(severityBefore);
    assertThat(findingInspectorConfirmedAt()).isEqualTo(confirmedAtBefore);
    assertThat(findingId).isNotNull();
  }

  @Test
  void aFailedGenerationLeavesNoVersionAndReportsTheFailure() throws Exception {
    confirmFindings(true);
    DraftingStub.FAIL_NEXT.set(true);
    long before = countVersions();

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/draft", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("REPORT_DRAFT_UNAVAILABLE"));

    assertThat(countVersions()).isEqualTo(before);
  }

  @Test
  void theManualDraftRemainsAvailableAsAnEmptyStructuredVersion() throws Exception {
    confirmFindings(true);
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/draft/manual", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.sections.executiveSummary").doesNotExist())
        .andExpect(jsonPath("$.data.sections.purposeScopeCriteria").doesNotExist())
        .andExpect(jsonPath("$.data.narrative").doesNotExist())
        .andExpect(
            jsonPath("$.data.contentSnapshot")
                .value(org.hamcrest.Matchers.containsString("MANUAL")));
  }

  // ------------------------------------------------------------------ helpers

  private Evidence testEvidence() {
    return new Evidence(
        fixture.organizationId(),
        fixture.inspectionId(),
        null,
        com.smartdroneinspection.inspections.domain.enums.EvidenceKind.INSPECTION,
        "span.png",
        "image/png",
        1,
        "inspections/" + fixture.inspectionId() + "/evidence/" + UUID.randomUUID(),
        null,
        null,
        null);
  }

  private UUID draft() throws Exception {
    return draft(null);
  }

  private UUID draft(String language) throws Exception {
    confirmFindings(true);
    var request =
        post("/api/v1/inspections/{id}/report/draft", fixture.inspectionId())
            .with(principal(fixture.inspectorId(), "INSPECTOR"));
    if (language != null) request.header("Accept-Language", language);
    org.springframework.test.web.servlet.MvcResult result =
        mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    return UUID.fromString(
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asString());
  }

  private void confirmFindings(boolean noFindingsAcknowledged) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings/confirm", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noFindingsAcknowledged\":" + noFindingsAcknowledged + "}"))
        .andExpect(status().isOk());
  }

  private long countVersions() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM inspection_report_versions version "
                + "JOIN inspection_reports report ON report.id = version.inspection_report_id "
                + "WHERE report.inspection_id = ?",
            Long.class,
            fixture.inspectionId());
    return count == null ? 0 : count;
  }

  private long countFindings() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM findings WHERE inspection_id = ?",
            Long.class,
            fixture.inspectionId());
    return count == null ? 0 : count;
  }

  private String findingSeverity() {
    return jdbcTemplate.queryForObject(
        "SELECT severity FROM findings WHERE inspection_id = ? LIMIT 1",
        String.class,
        fixture.inspectionId());
  }

  private String findingInspectorConfirmedAt() {
    return jdbcTemplate.queryForObject(
        "SELECT inspector_confirmed_at::text FROM findings WHERE inspection_id = ? LIMIT 1",
        String.class,
        fixture.inspectionId());
  }

  private UUID insertConfirmedFinding(String defect, String severity) {
    jdbcTemplate.update(
        """
        INSERT INTO findings (id, inspection_id, created_by_user_id, finding_code,
                              defect_label, severity, location_description, technical_notes,
                              status, inspector_confirmed_at, confirmed_by_user_id, repair_required,
                              created_at, updated_at, row_version)
        VALUES (?, ?, ?, ?, ?, ?, 'Lower chord', 'Visible corrosion',
                'CONFIRMED', CURRENT_TIMESTAMP, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        UUID.randomUUID(),
        fixture.inspectionId(),
        fixture.inspectorId(),
        "F-" + UUID.randomUUID().toString().substring(0, 6),
        defect,
        severity,
        fixture.inspectorId());
    return jdbcTemplate.queryForObject(
        "SELECT id FROM findings WHERE inspection_id = ? ORDER BY created_at DESC LIMIT 1",
        UUID.class,
        fixture.inspectionId());
  }

  private static RequestPostProcessor principal(UUID userId, String role) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}

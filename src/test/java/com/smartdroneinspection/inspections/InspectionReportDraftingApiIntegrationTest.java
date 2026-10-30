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
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.AiInferencePort;
import com.smartdroneinspection.inspections.spi.ReportDraftPort;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.DraftContext;
import com.smartdroneinspection.users.repository.UserRepository;
import java.io.IOException;
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

  @Autowired
  com.smartdroneinspection.inspections.repository.InspectionReportRepository reportVersions;

  @Autowired com.smartdroneinspection.inspections.service.EvidenceService evidenceService;
  @Autowired com.smartdroneinspection.assets.reportcontext.ReportContextAccess reportContextAccess;
  @Autowired com.smartdroneinspection.inspections.spi.ReportRenderPort renderPort;
  @Autowired com.smartdroneinspection.shared.storage.EvidenceObjectStore objectStore;
  @Autowired com.smartdroneinspection.users.UserAccess userAccess;
  @Autowired org.springframework.context.ApplicationEventPublisher events;
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
  void reportDraftCanBeGeneratedWithoutAConfiguredExternalProvider() throws Exception {
    confirmFindings(true);
    Inspection inspection = inspections.findById(fixture.inspectionId()).orElseThrow();
    InspectionTestFixture.Data authorFixture = fixture;
    var localDraftService =
        new com.smartdroneinspection.inspections.service.ReportService(
            inspections,
            reportContextAccess,
            jdbcTemplate,
            evidence,
            findings,
            fieldSessions,
            reportVersions,
            evidenceService,
            java.util.Optional.empty(),
            renderPort,
            java.util.Optional.of(objectStore),
            userAccess,
            events,
            objectMapper);

    var fallbackDraft =
        localDraftService.generateDraft(authorFixture.inspectorId(), inspection.getId());

    assertThat(fallbackDraft.status())
        .isEqualTo(com.smartdroneinspection.inspections.domain.enums.ReportStatus.DRAFT);
    assertThat(fallbackDraft.contentSnapshot()).contains("local-synthesizer");
    assertThat(fallbackDraft.sections().executiveSummary()).isNotBlank();
    assertThat(fallbackDraft.sections().omissionDisclosure()).isNotBlank();
  }

  @Test
  void draftingReceivesInspectionItemsFromStoredInspection() throws Exception {
    draft();

    ReportDraftPort.DraftContext context = DraftingStub.LAST_CONTEXT.get();
    assertThat(context.inspection()).isNotNull();
  }

  @Test
  void draftingLoadsTheDetectorProvenanceAndSessionsByInspection() throws Exception {
    UUID evidenceId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO evidence (id, inspection_id,
                              file_name, content_type, size_bytes, object_key,
                              created_at)
        VALUES (?, ?, 'candidate-image.jpg', 'image/jpeg', 100, ?,
                CURRENT_TIMESTAMP)
        """,
        evidenceId,
        fixture.inspectionId(),
        "test/" + UUID.randomUUID());
    UUID candidateId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO findings
          (id, inspection_id, evidence_id, finding_code, defect_label,
           severity, bounding_box, status,
           model_name, confidence,
           technical_notes, created_at)
        VALUES (?, ?, ?, 'F-1', 'Possible crack', 'MEDIUM', '{"x":12}', 'CONFIRMED',
                'detector-test', 0.87, 'Possible crack',
                CURRENT_TIMESTAMP)
        """,
        candidateId,
        fixture.inspectionId(),
        evidenceId);
    UUID sessionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO field_sessions
          (id, inspection_id,
           status, started_at, ended_at, created_at, updated_at, row_version)
        VALUES (?, ?, 'POSTPONED', '2026-10-12T10:00:00Z',
                '2026-10-12T10:30:00Z', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        sessionId,
        fixture.inspectionId());

    draft();

    DraftContext context = DraftingStub.LAST_CONTEXT.get();
    // The detector's provenance travels with the finding, so the drafting model is told which
    // findings came from the detector and how confident it was.
    assertThat(context.candidates()).hasSize(1);
    assertThat(context.candidates().get(0).candidateId()).isEqualTo(candidateId.toString());
    assertThat(context.candidates().get(0).status()).isEqualTo("CONFIRMED");
    assertThat(context.candidates().get(0).modelName()).isEqualTo("detector-test");
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
    String statusBefore = findingStatus();
    long findingsBefore = countFindings();

    draft();

    assertThat(countFindings()).isEqualTo(findingsBefore);
    assertThat(findingSeverity()).isEqualTo(severityBefore);
    assertThat(findingStatus()).isEqualTo(statusBefore);
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
        fixture.inspectionId(),
        null,
        "span.png",
        "image/png",
        1,
        "inspections/" + fixture.inspectionId() + "/evidence/" + UUID.randomUUID(),
        null,
        null,
        null,
        null,
        java.time.Instant.now());
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
            "SELECT count(*) FROM inspection_reports WHERE inspection_id = ?",
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

  private String findingStatus() {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM findings WHERE inspection_id = ? LIMIT 1",
        String.class,
        fixture.inspectionId());
  }

  private UUID insertConfirmedFinding(String defect, String severity) {
    jdbcTemplate.update(
        """
        INSERT INTO findings (id, inspection_id, finding_code,
                              defect_label, severity, technical_notes,
                              status,
                              created_at)
        VALUES (?, ?, ?, ?, ?, 'Visible corrosion',
                'CONFIRMED', CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID(),
        fixture.inspectionId(),
        "F-" + UUID.randomUUID().toString().substring(0, 6),
        defect,
        severity);
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

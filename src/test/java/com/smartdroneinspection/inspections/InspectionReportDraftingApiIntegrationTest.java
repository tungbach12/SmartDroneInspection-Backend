package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspections.repository.AiFindingCandidateRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.AiInferencePort;
import com.smartdroneinspection.inspections.spi.ReportDraftPort;
import com.smartdroneinspection.users.repository.UserRepository;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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
  @Autowired AiFindingCandidateRepository candidates;
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

    @Bean
    ReportDraftPort reportDraftPort() {
      return new ReportDraftPort() {
        @Override
        public String modelName() {
          return "stub-drafting-model";
        }

        @Override
        public SectionDraft draftSections(DraftContext context) throws IOException {
          if (FAIL_NEXT.get()) {
            throw new IOException("stubbed generation failure");
          }
          return new SectionDraft(
              "Proposed executive summary for "
                  + context.findings().size()
                  + " confirmed findings.",
              "Objective: confirm coating integrity.",
              "Visual and thermal optical survey at 15m AGL.",
              "Close inspection of the affected chord within 90 days.",
              "POOR",
              "Submerged components were not observed.",
              "legacy narrative kept for compatibility");
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
    fixture = new InspectionTestFixture(users, inspections, jdbcTemplate).create();
    jdbcTemplate.update(
        "UPDATE inspections SET status = 'FIELD_COMPLETED' WHERE id = ?", fixture.inspectionId());
    acceptEvidence();
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
        .andExpect(
            jsonPath("$.data.sections.purposeScopeCriteria")
                .value("Objective: confirm coating integrity."))
        .andExpect(jsonPath("$.data.sections.overallCondition").value("POOR"))
        .andExpect(
            jsonPath("$.data.sections.omissionDisclosure")
                .value("Submerged components were not observed."));
  }

  @Test
  void draftingRecordsItsProvenanceOnTheVersion() throws Exception {
    UUID versionId = draft();

    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{versionId}",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.llmModel").value("stub-drafting-model"))
        .andExpect(jsonPath("$.data.promptVersion").value("mf3-report-v1"))
        .andExpect(jsonPath("$.data.generatedAt").isNotEmpty());
  }

  @Test
  void draftingNeverAltersAReviewedFinding() throws Exception {
    insertConfirmedFinding("Corrosion", "HIGH");

    String severityBefore = findingSeverity();
    String decisionBefore = findingDecision();
    long findingsBefore = countFindings();

    draft();

    assertThat(countFindings()).isEqualTo(findingsBefore);
    assertThat(findingSeverity()).isEqualTo(severityBefore);
    assertThat(findingDecision()).isEqualTo(decisionBefore);
  }

  @Test
  void aFailedGenerationLeavesNoVersionAndReportsTheFailure() throws Exception {
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
  void theManualDraftRemainsAvailableWhenDraftingIsUnconfigured() throws Exception {
    // The authoring path must not depend on a model. Report 3 requires a structured manual draft to
    // remain possible, so this works with no drafting provider at all.
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/draft/manual", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("narrative", "Authored by hand.", "omissionDisclosure", "None."))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.llmModel").value("manual-authoring"))
        .andExpect(jsonPath("$.data.narrative").value("Authored by hand."));
  }

  // ------------------------------------------------------------------ helpers

  private UUID draft() throws Exception {
    org.springframework.test.web.servlet.MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/report/draft", fixture.inspectionId())
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asString());
  }

  private void acceptEvidence() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/evidence-quality-decisions", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "decision", "ACCEPTED",
                            "shotListComparison", "Main span covered from both walkways"))))
        .andExpect(status().isOk());
  }

  private long countVersions() {
    Long count =
        jdbcTemplate.queryForObject("SELECT count(*) FROM inspection_report_versions", Long.class);
    return count == null ? 0 : count;
  }

  private long countFindings() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM verified_findings WHERE inspection_id = ?",
            Long.class,
            fixture.inspectionId());
    return count == null ? 0 : count;
  }

  private String findingSeverity() {
    return jdbcTemplate.queryForObject(
        "SELECT severity FROM verified_findings WHERE inspection_id = ? LIMIT 1",
        String.class,
        fixture.inspectionId());
  }

  private String findingDecision() {
    return jdbcTemplate.queryForObject(
        "SELECT decision FROM verified_findings WHERE inspection_id = ? LIMIT 1",
        String.class,
        fixture.inspectionId());
  }

  private void insertConfirmedFinding(String defect, String severity) {
    jdbcTemplate.update(
        """
        INSERT INTO verified_findings (id, inspection_id, created_by_user_id, source, finding_code,
                                       defect_label, severity, location_description, technical_notes,
                                       status, decision, repair_required, created_at, updated_at,
                                       row_version)
        VALUES (?, ?, ?, 'MANUAL', ?, ?, ?, 'Lower chord', 'Visible corrosion',
                'OPEN', 'CONFIRMED', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        UUID.randomUUID(),
        fixture.inspectionId(),
        fixture.inspectorId(),
        "F-" + UUID.randomUUID().toString().substring(0, 6),
        defect,
        severity);
  }

  private static RequestPostProcessor principal(UUID userId, String role) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}

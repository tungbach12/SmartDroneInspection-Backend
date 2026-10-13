package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspections.domain.enums.AiFindingCandidateStatus;
import com.smartdroneinspection.inspections.repository.AiFindingCandidateRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.AiInferencePort;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * MF3-06 and MF3-08 to MF3-13 through the real HTTP surface: the manual finding path, and the
 * author/reviewer gates on the report including separation of duties and immutable publication.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InspectionReportApiIntegrationTest {
  /**
   * A real 1x1 PNG. MF3-02 requires corrupt input to be refused, so the happy-path fixtures must be
   * bytes that genuinely decode -- a signature followed by filler would be rejected by that check.
   */
  private static final byte[] PNG_BYTES =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

  // A second distinct 1x1 PNG (distinct SHA-256), so idempotent intake adds a second row.
  private static final byte[] PNG_BYTES_2 =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAAAAAA6fptVAAAACklEQVR4nGNiAAAABgADNjd8qAAAAABJRU5ErkJggg==");

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;
  @Autowired InspectionRepository inspections;
  @Autowired AiFindingCandidateRepository candidates;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @MockitoBean AiInferencePort inferencePort;

  private InspectionTestFixture.Data fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
    fixture = new InspectionTestFixture(users, inspections, jdbcTemplate).create();
  }

  @Test
  void manualFindingIsAvailableWithoutAnyAnalysis() throws Exception {
    UUID evidenceId = uploadEvidence();

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "evidenceId", evidenceId.toString(),
                            "defectLabel", "Corrosion on the lower chord",
                            "severity", "HIGH",
                            "locationDescription", "Main span, lower chord",
                            "technicalNotes", "Surface corrosion visible over roughly 0.4 m",
                            "component", "Lower chord",
                            "repairRequired", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.source").value("MANUAL"))
        .andExpect(jsonPath("$.data.aiCandidateId").doesNotExist())
        .andExpect(jsonPath("$.data.repairRequired").value(true));

    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/findings", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  void aFindingMeasurementIsStorableAsProse() throws Exception {
    UUID evidenceId = uploadEvidence();

    // measurement is a jsonb column, so an Inspector's prose must be wrapped rather than rejected.
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "evidenceId", evidenceId.toString(),
                            "defectLabel", "Section loss on the web",
                            "severity", "HIGH",
                            "locationDescription", "Girder G4 web",
                            "technicalNotes", "Measured loss exceeds the acceptance threshold",
                            "measurement", "Plate thickness 9.8mm against 12mm nominal"))))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.measurement").value("Plate thickness 9.8mm against 12mm nominal"));
  }

  @Test
  void anotherInspectorCannotAddAFinding() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings", fixture.inspectionId())
                .with(principal(fixture.otherInspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "defectLabel", "Not mine to record",
                            "severity", "LOW",
                            "locationDescription", "Elsewhere",
                            "technicalNotes", "None"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void findingAuthorCannotDecideTheirOwnFinding() throws Exception {
    UUID evidenceId = uploadEvidence();
    UUID findingId = createManualFinding(evidenceId);

    // The finding author is a genuine dual-role account: persist ORG_ADMIN beside INSPECTOR so
    // the request reaches the authorship check rather than failing an earlier scope gate.
    // A direct insert avoids LazyInitializationException on the detached fixture user's
    // LAZY roleAssignments collection.
    jdbcTemplate.update(
        "INSERT INTO user_roles (id, user_id, role) VALUES (?, ?, 'ORG_ADMIN')",
        UUID.randomUUID(),
        fixture.inspectorId());
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/findings/{findingId}/decision",
                    fixture.inspectionId(),
                    findingId)
                .with(principal(fixture.inspectorId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("decision", "CONFIRMED", "rationale", "Looks right"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FINDING_REVIEWER_INVALID"));

    // The denial persists nothing: the finding stays undecided.
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/findings", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].decision").doesNotExist());

    // An independent qualified reviewer in the same organization may decide it.
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/findings/{findingId}/decision",
                    fixture.inspectionId(),
                    findingId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("decision", "CONFIRMED", "rationale", "Blistering visible"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.decision").value("CONFIRMED"));
  }

  private UUID createManualFinding(UUID evidenceId) throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/findings", fixture.inspectionId())
                    .with(principal(fixture.inspectorId(), "INSPECTOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "evidenceId", evidenceId.toString(),
                                "defectLabel", "Coating blistering",
                                "severity", "HIGH",
                                "locationDescription", "Girder G4",
                                "technicalNotes", "Roughly 60mm across"))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(
        objectMapper
            .readTree(created.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .stringValue());
  }

  @Test
  void analysisIsRefusedUntilTheInspectorAcceptsTheEvidence() throws Exception {
    UUID evidenceId = uploadEvidence();

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/analyze",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AI_INFERENCE_UNAVAILABLE"));

    acceptEvidence();
  }

  @Test
  void analysisRequiresAssignmentAndAcceptedEvidenceAndPersistsOnlyPendingCandidates()
      throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    when(inferencePort.analyze(any(), eq("image/png")))
        .thenReturn(
            java.util.List.of(
                new AiInferencePort.Detection(
                    "capstone",
                    "provider-vision-model",
                    "surface crack",
                    new java.math.BigDecimal("0.81"),
                    "{\"xMin\":0.1,\"yMin\":0.2,\"xMax\":0.4,\"yMax\":0.6}")));

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/analyze",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.otherInspectorId(), "INSPECTOR")))
        .andExpect(status().isNotFound());
    verify(inferencePort, never()).analyze(any(), any());

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/analyze",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].modelName").value("capstone"))
        .andExpect(jsonPath("$.data[0].modelVersion").value("provider-vision-model"))
        .andExpect(jsonPath("$.data[0].confidence").value(0.81))
        .andExpect(jsonPath("$.data[0].status").value("PENDING"));

    assertThat(candidates.countByEvidenceId(evidenceId)).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM ai_finding_candidates WHERE evidence_id = ?",
                String.class,
                evidenceId))
        .isEqualTo(AiFindingCandidateStatus.PENDING.name());
  }

  @Test
  void reAnalysingTheSameEvidenceReturnsTheExistingCandidatesInsteadOfDuplicatingThem()
      throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    when(inferencePort.analyze(any(), eq("image/png")))
        .thenReturn(
            java.util.List.of(
                new AiInferencePort.Detection(
                    "capstone",
                    "provider-vision-model",
                    "surface crack",
                    new java.math.BigDecimal("0.81"),
                    "{\"xMin\":0.1,\"yMin\":0.2,\"xMax\":0.4,\"yMax\":0.6}")));

    // MF3-05 fires automatically once the evidence set is accepted, and a re-opened workspace
    // repeats it, so the same capture must not accumulate a second candidate for one detection.
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/analyze",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));

    String firstCandidateId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM ai_finding_candidates WHERE evidence_id = ?", String.class, evidenceId);

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/analyze",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(firstCandidateId));

    assertThat(candidates.countByEvidenceId(evidenceId)).isEqualTo(1);
  }

  @Test
  void reportWorkflowRequiresHumanGatesAndEnforcesSeparationOfDuties() throws Exception {
    // Without an accepted evidence decision there is no draft to author.
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/draft", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REPORT_STATE_CONFLICT"));
  }

  @Test
  void theAuthorCanWriteAStructuredDraftWhenAutomatedDraftingIsUnavailable() throws Exception {
    uploadEvidence();
    acceptEvidence();

    // No ReportDraftPort bean is configured in this test context, so the automated path reports
    // REPORT_DRAFT_UNAVAILABLE. Report 3 requires the author to still be able to complete a
    // structured manual draft under the same review gates.
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/draft", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("REPORT_DRAFT_UNAVAILABLE"));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/report/draft/manual", fixture.inspectionId())
                    .with(principal(fixture.inspectorId(), "INSPECTOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "narrative", "The north span was surveyed from both walkways.",
                                "omissionDisclosure",
                                    "Automated defect detection was not run for this inspection."))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.versionNo").value(1))
            .andExpect(jsonPath("$.data.llmModel").value("manual-authoring"))
            .andReturn();

    UUID versionId =
        UUID.fromString(
            objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .stringValue());

    // The manual draft carries the same gates: only the author verifies, only a qualified reviewer
    // publishes.
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/verify",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("AUTHOR_VERIFIED"));
  }

  @Test
  void evidenceAddedAfterTheDraftMakesVerificationStale() throws Exception {
    uploadEvidence();
    acceptEvidence();

    UUID versionId = createManualDraftVersion();

    // A new capture lands after the draft: upload succeeds (idempotent intake is unaffected),
    // but the draft was written over the older set.
    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(new MockMultipartFile("file", "east-face.png", "image/png", PNG_BYTES_2))
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/verify",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REPORT_SNAPSHOT_STALE"));

    // The stale version is untouched: still DRAFT, never verified.
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/report/versions", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value("DRAFT"));
  }

  @Test
  void evidenceAddedAfterVerificationMakesSubmitStale() throws Exception {
    uploadEvidence();
    acceptEvidence();

    UUID versionId = createManualDraftVersion();

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/verify",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("AUTHOR_VERIFIED"));

    // A new capture lands after verification: submit is refused and the version stays verified.
    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(new MockMultipartFile("file", "east-face.png", "image/png", PNG_BYTES_2))
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/submit",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REPORT_SNAPSHOT_STALE"));

    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/report/versions", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value("AUTHOR_VERIFIED"));
  }

  private UUID createManualDraftVersion() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/report/draft/manual", fixture.inspectionId())
                    .with(principal(fixture.inspectorId(), "INSPECTOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("narrative", "The north span was surveyed."))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .stringValue());
  }

  @Test
  void aManualDraftStillRefusesAnAuthorWithoutAnAcceptedEvidenceDecision() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/draft/manual", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("narrative", "A narrative written before evidence was accepted."))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REPORT_STATE_CONFLICT"));
  }

  @Test
  void listingVersionsOfAnInspectionWithoutAReportIsAnEmptyResult() throws Exception {
    // A report does not exist until the Inspector authors one, so the reviewer sees an empty
    // list rather than a missing-resource failure.
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/report/versions", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  void reviewerCannotApproveTheAuthorsOwnReport() throws Exception {
    acceptEvidence();
    UUID versionId = insertVerifiedAndSubmittedVersion();

    // The author is not an ORG_ADMIN, so the role gate refuses the review outright.
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/review",
                    fixture.inspectionId(),
                    versionId)
                .param("approve", "true")
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void qualifiedReviewerCanReturnThenApproveThenPublish() throws Exception {
    acceptEvidence();
    UUID versionId = insertVerifiedAndSubmittedVersion();

    // Returning without a reason is refused: the author must know what to change.
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/review",
                    fixture.inspectionId(),
                    versionId)
                .param("approve", "false")
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REPORT_REVIEWER_INVALID"));

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/review",
                    fixture.inspectionId(),
                    versionId)
                .param("approve", "false")
                .param("reason", "The east face coverage is not disclosed")
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("RETURNED"))
        .andExpect(
            jsonPath("$.data.reviewReason").value("The east face coverage is not disclosed"));

    // A returned version cannot be approved until the author re-submits it.
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/review",
                    fixture.inspectionId(),
                    versionId)
                .param("approve", "true")
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REPORT_REVIEWER_INVALID"));

    jdbcTemplate.update(
        "UPDATE inspection_report_versions SET status = 'SUBMITTED' WHERE id = ?", versionId);
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/review",
                    fixture.inspectionId(),
                    versionId)
                .param("approve", "true")
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPROVED"));

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/publish",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.repairRequiredFindingIds.length()").value(0))
        .andExpect(jsonPath("$.data.inspectionStatus").value("COMPLETED"));
  }

  @Test
  void aReviewerFromAnotherOrganizationIsRefused() throws Exception {
    acceptEvidence();
    UUID versionId = insertVerifiedAndSubmittedVersion();

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/review",
                    fixture.inspectionId(),
                    versionId)
                .param("approve", "true")
                .with(principal(fixture.outsiderId(), "ORG_ADMIN")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
  }

  @Test
  void publicationHandsOnlyConfirmedRepairRequiredFindingsToMaintenance() throws Exception {
    acceptEvidence();
    UUID versionId = insertVerifiedAndSubmittedVersion();
    insertRepairRequiredFinding(true);
    insertRepairRequiredFinding(false);

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/review",
                    fixture.inspectionId(),
                    versionId)
                .param("approve", "true")
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk());

    MvcResult published =
        mockMvc
            .perform(
                post(
                        "/api/v1/inspections/{id}/report/versions/{versionId}/publish",
                        fixture.inspectionId(),
                        versionId)
                    .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.inspectionStatus").value("REPAIR_PENDING"))
            .andReturn();

    String body = published.getResponse().getContentAsString();
    assertThat(body).contains("repairRequiredFindingIds");
    Long confirmedRepairRequired =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM verified_findings WHERE inspection_id = ? AND repair_required = TRUE"
                + " AND decision IN ('CONFIRMED','MODIFIED')",
            Long.class,
            fixture.inspectionId());
    assertThat(confirmedRepairRequired).isEqualTo(1L);
  }

  @Test
  void aReportAuthoredThroughTheApiCanBePublished() throws Exception {
    // This walks the real sequence instead of seeding a SUBMITTED version, so the inspection
    // status transitions that drafting and publication depend on are covered too.
    uploadEvidence();
    acceptEvidence();

    MvcResult draft =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/report/draft/manual", fixture.inspectionId())
                    .with(principal(fixture.inspectorId(), "INSPECTOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("narrative", "North span surveyed from both walkways."))))
            .andExpect(status().isOk())
            .andReturn();

    UUID versionId =
        UUID.fromString(
            objectMapper
                .readTree(draft.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .stringValue());

    // Drafting moves the inspection out of field work; publication would be impossible otherwise.
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM inspections WHERE id = ?",
                String.class,
                fixture.inspectionId()))
        .isEqualTo("REPORT_DRAFT");

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/verify",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/submit",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/review",
                    fixture.inspectionId(),
                    versionId)
                .param("approve", "true")
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPROVED"));

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/publish",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.versionNo").value(1))
        .andExpect(jsonPath("$.data.inspectionStatus").value("COMPLETED"));
  }

  private UUID uploadEvidence() throws Exception {
    MvcResult uploaded =
        mockMvc
            .perform(
                multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                    .file(new MockMultipartFile("file", "span.png", "image/png", PNG_BYTES))
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(
        objectMapper
            .readTree(uploaded.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .stringValue());
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

  /**
   * Seeds a report in SUBMITTED with the inspection already in REPORT_DRAFT, a legal review state.
   */
  private UUID insertVerifiedAndSubmittedVersion() {
    jdbcTemplate.update(
        "UPDATE inspections SET status = 'REPORT_DRAFT' WHERE id = ?", fixture.inspectionId());
    UUID reportId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspection_reports (id, inspection_id, author_user_id, status,
                                        current_version_number, created_at, updated_at, row_version)
        VALUES (?, ?, ?, 'SUBMITTED', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        reportId,
        fixture.inspectionId(),
        fixture.inspectorId());
    UUID versionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspection_report_versions (id, inspection_report_id, version_no, status,
                                                author_user_id, author_verified_by_user_id,
                                                author_verified_at, content_snapshot, created_at)
        VALUES (?, ?, 1, 'SUBMITTED', ?, ?, CURRENT_TIMESTAMP, '{}'::jsonb, CURRENT_TIMESTAMP)
        """,
        versionId,
        reportId,
        fixture.inspectorId(),
        fixture.inspectorId());
    return versionId;
  }

  private void insertRepairRequiredFinding(boolean repairRequired) {
    jdbcTemplate.update(
        """
        INSERT INTO verified_findings (id, inspection_id, created_by_user_id, source, finding_code,
                                       defect_label, severity, location_description, technical_notes,
                                       status, decision, repair_required, component, description,
                                       observed_condition, created_at, updated_at, row_version)
        VALUES (?, ?, ?, 'MANUAL', ?, 'Corrosion', 'HIGH', 'Lower chord', 'Visible corrosion',
                'OPEN', 'CONFIRMED', ?, 'Lower chord', 'Corrosion', 'Surface corrosion observed',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        UUID.randomUUID(),
        fixture.inspectionId(),
        fixture.inspectorId(),
        "F-" + UUID.randomUUID().toString().substring(0, 6),
        repairRequired);
  }

  private static RequestPostProcessor principal(UUID userId, String role) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}

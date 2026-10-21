package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspections.domain.enums.FindingStatus;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportVersionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.service.EvidenceQualityService;
import com.smartdroneinspection.inspections.spi.AiInferencePort;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.Base64;
import java.util.List;
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
  @Autowired InspectionReportRepository reports;
  @Autowired InspectionReportVersionRepository reportVersions;
  @Autowired FindingRepository findings;
  @Autowired EvidenceQualityService qualityService;
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
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.modelName").doesNotExist())
        .andExpect(jsonPath("$.data.repairRequired").value(true));

    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/findings", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  void manualFindingCanUseItsLinkedImageInsteadOfDuplicateLocationText() throws Exception {
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
                            "defectLabel", "Coating crack",
                            "severity", "MEDIUM",
                            "technicalNotes", "Visible in the attached image"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.evidenceId").value(evidenceId.toString()))
        .andExpect(jsonPath("$.data.locationDescription").doesNotExist());
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
    UUID evidenceId = uploadEvidence();
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings", fixture.inspectionId())
                .with(principal(fixture.otherInspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "evidenceId", evidenceId.toString(),
                            "defectLabel", "Not mine to record",
                            "severity", "LOW",
                            "technicalNotes", "None"))))
        .andExpect(status().isNotFound());
  }

  private UUID createRepairRequiredManualFinding(UUID evidenceId) throws Exception {
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
                                "defectLabel", "Cracking",
                                "severity", "HIGH",
                                "technicalNotes", "Visible crack",
                                "repairRequired", true))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(
        objectMapper
            .readTree(created.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .stringValue());
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
  void emptyAnalysisResultIsPersistedAsCompletedAndDoesNotRerunAutomatically() throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    when(inferencePort.analyze(any(), eq("image/png"))).thenReturn(List.of());

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/analyze",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));

    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/evidence/analysis-status", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].evidenceId").value(evidenceId.toString()))
        .andExpect(jsonPath("$.data[0].detectionCount").value(0))
        .andExpect(jsonPath("$.data[0].completedAt").isNotEmpty());

    verify(inferencePort).analyze(any(), eq("image/png"));
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/analyze",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
    verify(inferencePort).analyze(any(), eq("image/png"));
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
                    "compatible-vision",
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
        .andExpect(jsonPath("$.data[0].modelProvider").value("compatible-vision"))
        .andExpect(jsonPath("$.data[0].modelName").value("capstone"))
        .andExpect(jsonPath("$.data[0].modelVersion").value("provider-vision-model"))
        .andExpect(jsonPath("$.data[0].confidence").value(0.81))
        .andExpect(jsonPath("$.data[0].status").value("PENDING"));

    assertThat(findings.findByEvidenceIdOrderByCreatedAtAsc(evidenceId)).hasSize(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM findings WHERE evidence_id = ?", String.class, evidenceId))
        .isEqualTo("PENDING");
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
                    "compatible-vision",
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

    String firstFindingId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM findings WHERE evidence_id = ?", String.class, evidenceId);

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/analyze",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(firstFindingId));

    assertThat(findings.findByEvidenceIdOrderByCreatedAtAsc(evidenceId)).hasSize(1);
  }

  @Test
  void anExplicitReRunReplacesUnreviewedFindingsAndKeepsTheReviewedOnes() throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    when(inferencePort.analyze(any(), eq("image/png")))
        .thenReturn(
            java.util.List.of(
                new AiInferencePort.Detection(
                    "compatible-vision",
                    "capstone",
                    "vision-v1",
                    "surface crack",
                    new java.math.BigDecimal("0.81"),
                    "{\"xMin\":0.1,\"yMin\":0.2,\"xMax\":0.4,\"yMax\":0.6}")));

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/analyze",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
    UUID suggested =
        UUID.fromString(
            jdbcTemplate.queryForObject(
                "SELECT id FROM findings WHERE evidence_id = ?", String.class, evidenceId));

    // A finding the Inspector already decided carries that decision, so a changed detector must
    // not erase it.
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/findings/{findingId}/confirmation",
                    fixture.inspectionId(),
                    suggested)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());

    // A pass without the explicit re-run still answers with the existing set.
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/analyze",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(suggested.toString()));

    // Re-running after the detector changed replaces the unreviewed suggestion and keeps the
    // reviewed one, so the Inspector can re-detect without losing a decision.
    when(inferencePort.analyze(any(), eq("image/png")))
        .thenReturn(
            java.util.List.of(
                new AiInferencePort.Detection(
                    "yolo",
                    "yolo11s",
                    "mbdd2025-v1",
                    "corrosion",
                    new java.math.BigDecimal("0.62"),
                    "{\"xMin\":0.2,\"yMin\":0.3,\"xMax\":0.5,\"yMax\":0.7}")));
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/analyze",
                    fixture.inspectionId(),
                    evidenceId)
                .param("replace", "true")
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2));

    assertThat(findings.findByEvidenceIdOrderByCreatedAtAsc(evidenceId)).hasSize(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM findings"
                    + " WHERE evidence_id = ? AND status = 'PENDING' AND model_name = 'yolo11s'",
                Integer.class,
                evidenceId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM findings" + " WHERE evidence_id = ? AND status = 'CONFIRMED'",
                Integer.class,
                evidenceId))
        .isEqualTo(1);
  }

  @Test
  void rejectingAReviewedCandidateRemovesItsUndecidedOfficialFinding() throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    when(inferencePort.analyze(any(), eq("image/png")))
        .thenReturn(
            java.util.List.of(
                new AiInferencePort.Detection(
                    "compatible-vision",
                    "capstone",
                    "vision-v1",
                    "surface crack",
                    new java.math.BigDecimal("0.81"),
                    "{\"xMin\":0.1,\"yMin\":0.2,\"xMax\":0.4,\"yMax\":0.6}")));
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/analyze",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk());
    UUID findingId =
        UUID.fromString(
            jdbcTemplate.queryForObject(
                "SELECT id FROM findings WHERE evidence_id = ?", String.class, evidenceId));
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/findings/{findingId}/confirmation",
                    fixture.inspectionId(),
                    findingId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
            {"defectLabel":"Surface crack","severity":"HIGH",
             "locationDescription":"Girder G4","technicalNotes":"Visible crack"}
            """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM findings WHERE id = ? AND status = 'CONFIRMED'",
                Long.class,
                findingId))
        .isEqualTo(1L);
    // Confirming a finding the Inspector already confirmed is refused, because the content would
    // change underneath a decision that was already made.
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/findings/{findingId}/confirmation",
                    fixture.inspectionId(),
                    findingId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defectLabel":"Surface crack","severity":"HIGH",
                     "locationDescription":"Girder G4","technicalNotes":"Visible crack"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FINDING_ALREADY_CONFIRMED"));
  }

  @Test
  void explicitFindingsGateBlocksPendingAndNeverCreatesAReportVersion() throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    when(inferencePort.analyze(any(), eq("image/png")))
        .thenReturn(
            java.util.List.of(
                new AiInferencePort.Detection(
                    "compatible-vision",
                    "capstone",
                    "vision-v1",
                    "surface crack",
                    new java.math.BigDecimal("0.81"),
                    "{}")));
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/analyze",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings/confirm", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noFindingsAcknowledged\":false}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PENDING_FINDINGS_EXIST"));

    UUID findingId =
        UUID.fromString(
            jdbcTemplate.queryForObject(
                "SELECT id FROM findings WHERE evidence_id = ?", String.class, evidenceId));
    // A suggestion the Inspector does not want is removed from the working set rather than
    // decided about, so the gate opens once nothing is left undecided.
    mockMvc
        .perform(
            delete(
                    "/api/v1/inspections/{id}/findings/{findingId}",
                    fixture.inspectionId(),
                    findingId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Not a defect\"}"))
        .andExpect(status().isOk());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM findings WHERE id = ?", Long.class, findingId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM findings WHERE id = ? AND deleted_at IS NOT NULL",
                Long.class,
                findingId))
        .isZero();
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/findings/removed", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings/confirm", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noFindingsAcknowledged\":true}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/report/versions", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void cleanInspectionRequiresExplicitNoFindingsAcknowledgment() throws Exception {
    uploadEvidence();
    acceptEvidence();
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings/confirm", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noFindingsAcknowledged\":false}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NO_FINDINGS_ACKNOWLEDGMENT_REQUIRED"));
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
    ensureFindingsConfirmed();

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

    // A new capture requires an explicit return to Findings and a fresh confirmation.
    reopenFindings();
    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(new MockMultipartFile("file", "east-face.png", "image/png", PNG_BYTES_2))
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk());
    acceptEvidence();
    ensureFindingsConfirmed();

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

    // A new capture requires explicit reopen and reconfirmation; its old draft remains stale.
    reopenFindings();
    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(new MockMultipartFile("file", "east-face.png", "image/png", PNG_BYTES_2))
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk());
    acceptEvidence();
    ensureFindingsConfirmed();

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

  private void reopenFindings() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings/reopen", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk());
  }

  private void ensureFindingsConfirmed() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings/confirm", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noFindingsAcknowledged\":true}"))
        .andExpect(status().isOk());
  }

  private void postReportReturn(UUID versionId, String rationale) throws Exception {
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/review",
                    fixture.inspectionId(),
                    versionId)
                .param("approve", "false")
                .param("reasonType", "TEXT_REVISION")
                .param("reason", rationale)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("RETURNED"));
  }

  private void verifyAndSubmitVersion(UUID versionId) throws Exception {
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
  }

  private UUID createSubmittedManualVersion() throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    createManualFinding(evidenceId);
    ensureFindingsConfirmed();
    UUID versionId = createManualDraftVersion();
    verifyAndSubmitVersion(versionId);
    return versionId;
  }

  private UUID createManualDraftVersion() throws Exception {
    ensureFindingsConfirmed();
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/report/draft/manual", fixture.inspectionId())
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
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
    UUID versionId = createSubmittedManualVersion();

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
  void reportApprovalDoesNotRequireSeparateFindingVerdicts() throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    UUID findingId = createManualFinding(evidenceId);
    ensureFindingsConfirmed();
    UUID versionId = createManualDraftVersion();
    verifyAndSubmitVersion(versionId);

    // The reviewer approves the report as a whole, so the finding carries no separate verdict of
    // its own: the Inspector's confirmation is the only decision on it.
    assertThat(
            findings
                .findByIdAndInspectionId(findingId, fixture.inspectionId())
                .orElseThrow()
                .getStatus())
        .isEqualTo(FindingStatus.CONFIRMED);

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
  }

  @Test
  void findingsRevisionReturnReopensInspectorConfirmationButTextReturnDoesNot() throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    UUID findingId = createManualFinding(evidenceId);
    ensureFindingsConfirmed();
    UUID versionId = createManualDraftVersion();
    verifyAndSubmitVersion(versionId);

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/review",
                    fixture.inspectionId(),
                    versionId)
                .param("approve", "false")
                .param("reasonType", "TEXT_REVISION")
                .param("reason", "Clarify the report narrative")
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk());
    assertThat(
            findings
                .findByIdAndInspectionId(findingId, fixture.inspectionId())
                .orElseThrow()
                .getInspectorConfirmedAt())
        .isNotNull();

    // A second submission can be returned for a findings/evidence revision.
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
                .param("approve", "false")
                .param("reasonType", "FINDINGS_REVISION")
                .param("reason", "Revisit the source evidence")
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk());
    assertThat(
            findings
                .findByIdAndInspectionId(findingId, fixture.inspectionId())
                .orElseThrow()
                .getInspectorConfirmedAt())
        .isNull();
    assertThat(inspections.findById(fixture.inspectionId()).orElseThrow().getStatus())
        .isEqualTo(
            com.smartdroneinspection.inspections.domain.enums.InspectionStatus.FIELD_COMPLETED);
  }

  @Test
  void qualifiedReviewerCanReturnThenApprovePublishAndRecordNoRepair() throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    ensureFindingsConfirmed();
    UUID versionId = createManualDraftVersion();
    verifyAndSubmitVersion(versionId);

    // Returning without a reason is refused: the author must know what to change.
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/review",
                    fixture.inspectionId(),
                    versionId)
                .param("approve", "false")
                .param("reasonType", "TEXT_REVISION")
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REPORT_RETURN_REASON_REQUIRED"));

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/review",
                    fixture.inspectionId(),
                    versionId)
                .param("approve", "false")
                .param("reasonType", "TEXT_REVISION")
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

    MvcResult returned =
        mockMvc
            .perform(
                get(
                        "/api/v1/inspections/{id}/report/versions/{versionId}",
                        fixture.inspectionId(),
                        versionId)
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(
            objectMapper
                .readTree(returned.getResponse().getContentAsString())
                .path("data")
                .path("contentSnapshot")
                .asString())
        .contains("\"analysisMethod\": \"MANUAL\"");
    assertThat(
            objectMapper
                    .readTree(returned.getResponse().getContentAsString())
                    .path("data")
                    .path("sections")
                    .path("executiveSummary")
                    .isMissingNode()
                || objectMapper
                    .readTree(returned.getResponse().getContentAsString())
                    .path("data")
                    .path("sections")
                    .path("executiveSummary")
                    .isNull())
        .isTrue();
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
        .andExpect(jsonPath("$.data.repairRequiredFindingIds").isEmpty())
        .andExpect(jsonPath("$.data.inspectionStatus").value("REPORT_PUBLISHED"));

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/no-corrective-work", fixture.inspectionId())
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("rationale", "No findings in the observed scope"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inspectionStatus").value("COMPLETED"));
  }

  @Test
  void publishedFindingNoRepairOutcomeIsDurableAndHandoffClosesTheInspection() throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    UUID findingId = createManualFinding(evidenceId);
    ensureFindingsConfirmed();
    UUID versionId = createManualDraftVersion();
    verifyAndSubmitVersion(versionId);

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
        .andExpect(jsonPath("$.data.inspectionStatus").value("REPORT_PUBLISHED"))
        .andExpect(jsonPath("$.data.repairRequiredFindingIds").isEmpty());

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/no-corrective-work", fixture.inspectionId())
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("rationale", "Within scope"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CORRECTIVE_ACTION_REVIEW_REQUIRED"));

    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/findings/{findingId}/corrective-action",
                    fixture.inspectionId(),
                    findingId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "correctiveActionRequired",
                            false,
                            "rationale",
                            "No repair is needed within the observed span"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.correctiveActionRequired").value(false))
        .andExpect(
            jsonPath("$.data.correctiveActionRationale")
                .value("No repair is needed within the observed span"));

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/corrective-handoff", fixture.inspectionId())
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inspectionStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.repairRequiredFindingIds").isEmpty());
  }

  @Test
  void publishingClearsACorrectiveOutcomeRecordedBeforeThePublication() throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    UUID findingId = createManualFinding(evidenceId);
    ensureFindingsConfirmed();
    UUID versionId = createManualDraftVersion();
    verifyAndSubmitVersion(versionId);

    // The V30 cutover backfilled the pre-cutover reviewer disposition onto findings of reports that
    // had not been published yet, so a recorded outcome can predate the version it is attributed
    // to.
    jdbcTemplate.update(
        """
        UPDATE findings
           SET corrective_action_required = FALSE,
               corrective_action_reviewed_by_user_id = ?,
               corrective_action_reviewed_at = ?,
               corrective_action_rationale = 'Backfilled disposition'
         WHERE id = ?
        """,
        fixture.reviewerId(),
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(259200)),
        findingId);

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/review",
                    fixture.inspectionId(),
                    versionId)
                .param("approve", "true")
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/publish",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inspectionStatus").value("REPORT_PUBLISHED"));

    // MF3-12: the outcome belongs to the publication that precedes it, so the stale one is cleared
    // and the handoff refuses until the reviewer records a fresh outcome for this version.
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/corrective-handoff", fixture.inspectionId())
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CORRECTIVE_ACTION_REVIEW_INCOMPLETE"));

    Integer reviewed =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM findings WHERE id = ?"
                + " AND corrective_action_reviewed_by_user_id IS NOT NULL",
            Integer.class,
            findingId);
    Boolean required =
        jdbcTemplate.queryForObject(
            "SELECT corrective_action_required FROM findings WHERE id = ?",
            Boolean.class,
            findingId);
    assertThat(reviewed).isZero();
    assertThat(required).isTrue();
  }

  @Test
  void publishedConfirmedFindingRequiresCorrectiveOutcomeBeforeHandoff() throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    UUID findingId = createRepairRequiredManualFinding(evidenceId);
    ensureFindingsConfirmed();
    UUID versionId = createManualDraftVersion();
    verifyAndSubmitVersion(versionId);

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
        .andExpect(jsonPath("$.data.inspectionStatus").value("REPORT_PUBLISHED"));

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/corrective-handoff", fixture.inspectionId())
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CORRECTIVE_ACTION_REVIEW_INCOMPLETE"));

    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/findings/{findingId}/corrective-action",
                    fixture.inspectionId(),
                    findingId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "correctiveActionRequired",
                            true,
                            "priority",
                            "HIGH",
                            "dueAt",
                            java.time.Instant.now().plusSeconds(86400).toString(),
                            "rationale",
                            "Corrosion requires repair"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.correctiveActionRequired").value(true))
        .andExpect(jsonPath("$.data.correctiveActionPriority").value("HIGH"))
        .andExpect(jsonPath("$.data.correctiveActionDueAt").exists())
        .andExpect(
            jsonPath("$.data.correctiveActionReviewedByUserId")
                .value(fixture.reviewerId().toString()));

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/corrective-handoff", fixture.inspectionId())
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inspectionStatus").value("REPAIR_PENDING"))
        .andExpect(jsonPath("$.data.repairRequiredFindingIds[0]").value(findingId.toString()));
  }

  @Test
  void inspectorCanWithdrawAnUnreviewedSubmissionAndKeepItsEvidenceAndFindings() throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    UUID findingId = createManualFinding(evidenceId);
    ensureFindingsConfirmed();
    UUID versionId = createManualDraftVersion();
    verifyAndSubmitVersion(versionId);

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/withdraw",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("reason", "Correct the finding details"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("RETURNED"))
        .andExpect(jsonPath("$.data.withdrawnByUserId").value(fixture.inspectorId().toString()))
        .andExpect(jsonPath("$.data.withdrawnAt").isNotEmpty())
        .andExpect(jsonPath("$.data.withdrawalReason").value("Correct the finding details"));

    assertThat(inspections.findById(fixture.inspectionId()).orElseThrow().getStatus())
        .isEqualTo(
            com.smartdroneinspection.inspections.domain.enums.InspectionStatus.FIELD_COMPLETED);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evidence WHERE inspection_id = ?",
                Integer.class,
                fixture.inspectionId()))
        .isEqualTo(1);
    assertThat(findings.findByIdAndInspectionId(findingId, fixture.inspectionId())).isPresent();
    assertThat(
            reportVersions
                .findByInspectionReportIdAndVersionNo(
                    reports.findByInspectionId(fixture.inspectionId()).orElseThrow().getId(), 1)
                .orElseThrow()
                .getStatus())
        .isEqualTo(ReportStatus.RETURNED);
  }

  @Test
  void inspectorCanCorrectAndSoftDeleteAnUndecidedFinding() throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    UUID findingId = createManualFinding(evidenceId);

    mockMvc
        .perform(
            put("/api/v1/inspections/{id}/findings/{findingId}", fixture.inspectionId(), findingId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "evidenceId", evidenceId,
                            "defectLabel", "Corrected label",
                            "severity", "MEDIUM",
                            "technicalNotes", "Updated observation"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.defectLabel").value("Corrected label"));

    mockMvc
        .perform(
            delete(
                    "/api/v1/inspections/{id}/findings/{findingId}",
                    fixture.inspectionId(),
                    findingId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("reason", "False alarm after close-up review"))))
        .andExpect(status().isOk());

    assertThat(findings.findByIdAndInspectionId(findingId, fixture.inspectionId())).isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM findings WHERE id = ? AND deleted_by_user_id = ? AND deletion_reason = ? AND deleted_at IS NOT NULL",
                Integer.class,
                findingId,
                fixture.inspectorId(),
                "False alarm after close-up review"))
        .isEqualTo(1);
  }

  @Test
  void retiringEvidenceKeepsTheOriginalCaptureForAuditButRefusesAnalysis() throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/retire",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("reason", "Replaced with a sharper close-up"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.retiredAt").isNotEmpty())
        .andExpect(jsonPath("$.data.retirementReason").value("Replaced with a sharper close-up"));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evidence WHERE id = ?", Integer.class, evidenceId))
        .isEqualTo(1);

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/analyze",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVIDENCE_RETIRED"));
  }

  @Test
  void inspectorCanBulkRetireCapturesWithASingleSharedReason() throws Exception {
    UUID evidenceId1 = uploadEvidence();
    UUID evidenceId2 =
        UUID.fromString(
            objectMapper
                .readTree(
                    mockMvc
                        .perform(
                            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                                .file(
                                    new MockMultipartFile(
                                        "file", "span2.png", "image/png", PNG_BYTES_2))
                                .with(principal(fixture.inspectorId(), "INSPECTOR")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("data")
                .path("id")
                .stringValue());

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/evidence/bulk-retire", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("reason", "Imported wrong SD card from another site"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(
            jsonPath("$.data[0].retirementReason")
                .value("Imported wrong SD card from another site"))
        .andExpect(
            jsonPath("$.data[1].retirementReason")
                .value("Imported wrong SD card from another site"));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evidence WHERE inspection_id = ? AND retired_at IS NOT NULL",
                Integer.class,
                fixture.inspectionId()))
        .isEqualTo(2);

    List<com.smartdroneinspection.inspections.domain.Evidence> active =
        ((com.smartdroneinspection.inspections.repository.EvidenceRepository)
                webApplicationContext.getBean(
                    com.smartdroneinspection.inspections.repository.EvidenceRepository.class))
            .findByInspectionIdAndRetiredAtIsNullOrderByCreatedAtAsc(fixture.inspectionId());
    assertThat(active).isEmpty();
  }

  @Test
  void aReviewerFromAnotherOrganizationIsRefused() throws Exception {
    UUID versionId = createSubmittedManualVersion();

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
  void reportApprovalIsReportLevelAndCorrectiveOutcomesRemainPerFindingAfterPublication()
      throws Exception {
    UUID evidenceId = uploadEvidence();
    acceptEvidence();
    UUID repairFindingId = createRepairRequiredManualFinding(evidenceId);
    UUID noRepairFindingId = createManualFinding(evidenceId);
    ensureFindingsConfirmed();
    UUID versionId = createManualDraftVersion();
    verifyAndSubmitVersion(versionId);

    // No Organization Admin finding decision is required to approve the submitted report.
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

    MvcResult published =
        mockMvc
            .perform(
                post(
                        "/api/v1/inspections/{id}/report/versions/{versionId}/publish",
                        fixture.inspectionId(),
                        versionId)
                    .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.inspectionStatus").value("REPORT_PUBLISHED"))
            .andReturn();

    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/findings/{findingId}/corrective-action",
                    fixture.inspectionId(),
                    repairFindingId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "correctiveActionRequired", true,
                            "priority", "HIGH",
                            "rationale", "Repair required"))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/findings/{findingId}/corrective-action",
                    fixture.inspectionId(),
                    noRepairFindingId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "correctiveActionRequired",
                            false,
                            "rationale",
                            "No repair is required within this observed scope"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/corrective-handoff", fixture.inspectionId())
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inspectionStatus").value("REPAIR_PENDING"))
        .andExpect(
            jsonPath("$.data.repairRequiredFindingIds[0]").value(repairFindingId.toString()));
    assertThat(published.getResponse().getContentAsString()).contains("repairRequiredFindingIds");
  }

  @Test
  void aReportAuthoredThroughTheApiCanBePublished() throws Exception {
    // This walks the real sequence instead of seeding a SUBMITTED version, so the inspection
    // status transitions that drafting and publication depend on are covered too.
    uploadEvidence();
    acceptEvidence();
    ensureFindingsConfirmed();

    MvcResult draft =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/report/draft/manual", fixture.inspectionId())
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
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
        .andExpect(jsonPath("$.data.inspectionStatus").value("REPORT_PUBLISHED"));
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/report/no-corrective-work", fixture.inspectionId())
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "rationale",
                            "No corrective work is required within the inspected north span"))))
        .andExpect(status().isOk())
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

  private UUID insertRepairRequiredFinding(boolean repairRequired) {
    UUID findingId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO findings (id, inspection_id, created_by_user_id, finding_code,
                              defect_label, severity, location_description, technical_notes,
                              status, inspector_confirmed_at, confirmed_by_user_id,
                              repair_required, corrective_action_required, component, description,
                              observed_condition, created_at, updated_at, row_version)
        VALUES (?, ?, ?, ?, 'Corrosion', 'HIGH', 'Lower chord', 'Visible corrosion',
                'CONFIRMED', CURRENT_TIMESTAMP, ?, ?, TRUE, 'Lower chord', 'Corrosion',
                'Surface corrosion observed', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        findingId,
        fixture.inspectionId(),
        fixture.inspectorId(),
        "F-" + UUID.randomUUID().toString().substring(0, 6),
        fixture.inspectorId(),
        repairRequired);
    return findingId;
  }

  private static RequestPostProcessor principal(UUID userId, String role) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}

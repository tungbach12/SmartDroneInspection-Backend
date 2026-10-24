package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.AiInferencePort;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * MF3-08 section authoring and MF3-11 publication of the report deliverable, over the real HTTP
 * surface.
 *
 * <p>The seam is what a user of the platform observes: an author saves the Report 3 §3.7.2
 * sections, and publication produces a stored, checksummed PDF that can be downloaded. Nothing here
 * asserts on internal helper names or template markup.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InspectionReportSectionsApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;
  @Autowired InspectionRepository inspections;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired EvidenceObjectStore objectStore;
  @MockitoBean AiInferencePort inferencePort;

  private InspectionTestFixture.Data fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() throws Exception {
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
    fixture = new InspectionTestFixture(users, inspections, jdbcTemplate).create();
    ensureActiveEvidence();
    confirmFindings(true);
  }

  private void ensureActiveEvidence() {
    jdbcTemplate.update(
        "UPDATE inspections SET status = 'FIELD_COMPLETED' WHERE id = ?", fixture.inspectionId());
    jdbcTemplate.update(
        """
        INSERT INTO evidence (id, organization_id, inspection_id, evidence_kind,
                              file_name, content_type, size_bytes, object_key,
                              upload_status, created_at)
        VALUES (?, ?, ?, 'INSPECTION', 'seed.png', 'image/png', 1, ?,
                'AVAILABLE', CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID(),
        fixture.organizationId(),
        fixture.inspectionId(),
        "seed/" + UUID.randomUUID());
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

  // ------------------------------------------------------------------ MF3-08 sections

  @Test
  void setupConfirmsTheFindingsSetThroughTheWorkflowApi() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/workflow-state", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("REPORT_DRAFT"))
        .andExpect(jsonPath("$.data.findingsConfirmedAt").isNotEmpty())
        .andExpect(jsonPath("$.data.findingsSnapshotHash").isNotEmpty());
  }

  @Test
  void theAuthorCanSaveAndReadBackEveryAuthoredSection() throws Exception {
    UUID versionId = insertDraftVersion();

    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/sections",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "executiveSummary",
                            "The north span was surveyed from both walkways.",
                            "inspectorCoverageAssessment",
                            "COATING captured at web-east; no direct evidence for the eastern abutment.",
                            "methodEquipmentConditions",
                            "Visual and thermal optical survey at 15m AGL in overcast dry conditions.",
                            "conclusionsRecommendations",
                            "Schedule a hands-on close inspection of the corroded chord.",
                            "overallCondition",
                            "POOR",
                            "omissionDisclosure",
                            "Submerged components were not observed."))))
        .andExpect(status().isOk());

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
                .value("The north span was surveyed from both walkways."))
        .andExpect(
            jsonPath("$.data.sections.inspectorCoverageAssessment")
                .value(
                    "COATING captured at web-east; no direct evidence for the eastern abutment."))
        .andExpect(jsonPath("$.data.sections.purposeScopeCriteria").doesNotExist())
        .andExpect(jsonPath("$.data.sections.overallCondition").value("POOR"))
        .andExpect(
            jsonPath("$.data.sections.omissionDisclosure")
                .value("Submerged components were not observed."))
        .andExpect(jsonPath("$.data.reportContext.organizationName").exists())
        .andExpect(jsonPath("$.data.reportContext.assetName").value("Main span"))
        .andExpect(jsonPath("$.data.reportContext.assetLocation").value("District 1"))
        .andExpect(jsonPath("$.data.reportContext.fieldSessions").isArray());
  }

  @Test
  void savingSectionsDoesNotCreateANewReportVersion() throws Exception {
    UUID versionId = insertDraftVersion();
    long before = countVersions();

    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/sections",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("executiveSummary", "Revised."))))
        .andExpect(status().isOk());

    assertThat(countVersions()).isEqualTo(before);
  }

  @Test
  void savingSectionsPreservesTheVersionProvenanceAndGates() throws Exception {
    UUID versionId = insertDraftVersion();

    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/sections",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("executiveSummary", "Revised."))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.versionNo").value(1))
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.submittedAt").doesNotExist())
        .andExpect(jsonPath("$.data.llmModel").doesNotExist());
  }

  @Test
  void sectionsAreRefusedOnceTheVersionHasLeftAnEditableState() throws Exception {
    UUID versionId = insertDraftVersion();
    jdbcTemplate.update(
        "UPDATE inspection_reports SET status = 'APPROVED' WHERE id = ?", versionId);

    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/sections",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("executiveSummary", "Too late."))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REPORT_STATE_CONFLICT"));
  }

  @Test
  void aReviewerWhoIsNotTheAuthorCannotRewriteTheDraft() throws Exception {
    UUID versionId = insertDraftVersion();

    // Section authoring is the author's work. A qualified reviewer may read a draft but cannot
    // rewrite it, and the route refuses the ORG_ADMIN role before the use-case scope check runs.
    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/sections",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("executiveSummary", "Rewritten by a reviewer."))))
        .andExpect(status().isForbidden());
  }

  @Test
  void anotherInspectorInTheSameOrganizationCannotRewriteTheDraft() throws Exception {
    UUID versionId = insertDraftVersion();

    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/sections",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.otherInspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("executiveSummary", "Rewritten by another inspector."))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("REPORT_SCOPE_DENIED"));
  }

  @Test
  void anOverLongSectionIsRefusedRatherThanTruncated() throws Exception {
    UUID versionId = insertDraftVersion();

    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/sections",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("executiveSummary", "x".repeat(10_001)))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anAbsentSectionReadsAsNotSuppliedRatherThanEmpty() throws Exception {
    UUID versionId = insertDraftVersion();

    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{versionId}",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sections.executiveSummary").doesNotExist())
        .andExpect(jsonPath("$.data.sections.overallCondition").doesNotExist());
  }

  @Test
  void aSnapshotWithoutTheNewSectionKeysStillReads() throws Exception {
    UUID versionId = UUID.randomUUID();
    // A snapshot written before the section keys existed: valid JSON, none of the new fields. It
    // must
    // still read, with the absent sections reported as not supplied rather than failing the
    // version.
    jdbcTemplate.update(
        """
        INSERT INTO inspection_reports (id, inspection_id, author_user_id, version_no, status,
                                        content_snapshot, created_at)
        VALUES (?, ?, ?, 1, 'DRAFT', '{"model":"manual-authoring","narrative":"Older draft."}'::jsonb,
                CURRENT_TIMESTAMP)
        """,
        versionId,
        fixture.inspectionId(),
        fixture.inspectorId());

    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{versionId}",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.versionNo").value(1))
        .andExpect(jsonPath("$.data.sections.executiveSummary").doesNotExist());
  }

  // ------------------------------------------------------------------ MF3-03 preview

  @Test
  void previewRendersAPdfWithoutStoringAnArtefact() throws Exception {
    UUID versionId = insertDraftVersion();

    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/preview",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF));

    // A preview is the author's working view: it renders but stores nothing against the version.
    assertThat(renderedKey(versionId)).isNull();
    assertThat(objectExists(reportObjectKey(versionId))).isFalse();
  }

  @Test
  void aNonAuthorCannotPreview() throws Exception {
    UUID versionId = insertDraftVersion();

    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/preview",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isForbidden());
  }

  // ------------------------------------------------------------------ MF3-11 deliverable

  @Test
  void publishingStoresTheDocumentAndRecordsAChecksumThatMatchesTheStoredBytes() throws Exception {
    UUID versionId = approvedVersion();
    publish(versionId);

    String objectKey = renderedKey(versionId);
    assertThat(objectKey).isNotBlank();
    byte[] stored = storedBytes(objectKey);
    assertThat(stored).isNotNull();
    assertThat(new String(stored, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
        .isEqualTo("%PDF-");
    assertThat(renderedChecksum(versionId)).isEqualTo(sha256(stored));
  }

  @Test
  void thePublishedDocumentCanBeDownloadedWithTheReportReferenceInTheFileName() throws Exception {
    UUID versionId = approvedVersion();
    publish(versionId);

    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/document",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(
            header()
                .string("Content-Disposition", org.hamcrest.Matchers.containsString("SDI-RPT-")))
        .andExpect(
            header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")));
  }

  @Test
  void downloadingAVersionWithNoStoredDocumentIsNotFoundRatherThanAServerError() throws Exception {
    UUID versionId = insertDraftVersion();

    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/document",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPORT_ARTEFACT_NOT_FOUND"));
  }

  @Test
  void aRepeatPublishIsRefusedAndLeavesTheFirstArtefactIntact() throws Exception {
    UUID versionId = approvedVersion();
    publish(versionId);
    byte[] first = storedBytes(renderedKey(versionId));
    String firstChecksum = renderedChecksum(versionId);

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/publish",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isConflict());

    assertThat(storedBytes(renderedKey(versionId))).isEqualTo(first);
    assertThat(renderedChecksum(versionId)).isEqualTo(firstChecksum);
  }

  @Test
  void publishingAFurtherVersionDoesNotOverwriteTheEarlierArtefact() throws Exception {
    UUID firstVersion = approvedVersion();
    publish(firstVersion);
    String firstKey = renderedKey(firstVersion);
    byte[] firstBytes = storedBytes(firstKey);

    UUID secondVersion = approvedVersion();
    publish(secondVersion);

    assertThat(renderedKey(secondVersion)).isNotEqualTo(firstKey);
    assertThat(storedBytes(firstKey)).isEqualTo(firstBytes);

    // The prior published version is now superseded
    String firstStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM inspection_reports WHERE id = ?", String.class, firstVersion);
    assertThat(firstStatus).isEqualTo("SUPERSEDED");
  }

  @Test
  void previewRendersConfirmedFindingsAndAttachedEvidence() throws Exception {
    UUID evidenceId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO evidence (id, organization_id, inspection_id,
                              evidence_kind, file_name, content_type, size_bytes,
                              object_key, location_description, upload_status, created_at)
        VALUES (?, ?, ?, 'INSPECTION', 'flange-crack.png', 'image/png', 67,
                'test-key', 'North girder flange', 'AVAILABLE', CURRENT_TIMESTAMP)
        """,
        evidenceId,
        fixture.organizationId(),
        fixture.inspectionId());

    UUID findingId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO findings (id, inspection_id, evidence_id, finding_code,
                              defect_label, severity, technical_notes,
                              status, inspector_confirmed_at,
                              created_at, updated_at)
        VALUES (?, ?, ?, 'F-001', 'Fatigue Crack', 'HIGH',
                '50mm surface crack', 'CONFIRMED', CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        findingId,
        fixture.inspectionId(),
        evidenceId);

    confirmFindings(false);

    UUID versionId = insertDraftVersion();

    MvcResult result =
        mockMvc
            .perform(
                get(
                        "/api/v1/inspections/{id}/report/versions/{versionId}/preview",
                        fixture.inspectionId(),
                        versionId)
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn();

    byte[] pdfBytes = result.getResponse().getContentAsByteArray();
    assertThat(pdfBytes).isNotEmpty();
    assertThat(new String(pdfBytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
        .isEqualTo("%PDF-");
    String reportText = extractText(pdfBytes).replaceAll("\\s+", " ");
    assertThat(reportText).contains("North girder flange");
  }

  @Test
  void previewRendersAnInspectorConfirmedFindingThatHasNoReviewerDecisionYet() throws Exception {
    // MF3-06: the Inspector's confirmation is a human decision, and the Findings UI states that a
    // confirmed candidate is "included in the report draft as a verified finding". The reviewer
    // verdict in MF3-09 refines that finding at the submitted report; it does not decide whether
    // the proposed defect appears in the draft at all. A draft that silently omits it disagrees
    // with both the reviewed set and the narrative drafted over the same findings.
    UUID evidenceId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO evidence (id, organization_id, inspection_id,
                              evidence_kind, file_name, content_type, size_bytes,
                              object_key, upload_status, created_at)
        VALUES (?, ?, ?, 'INSPECTION', 'gusset-plate.jpg', 'image/jpeg', 67,
                'gusset-test-key', 'AVAILABLE', CURRENT_TIMESTAMP)
        """,
        evidenceId,
        fixture.organizationId(),
        fixture.inspectionId());

    UUID candidateId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO findings (id, inspection_id, evidence_id, finding_code,
                              defect_label, severity, bounding_box, status, model_name, model_version,
                              confidence, technical_notes,
                              inspector_confirmed_at, created_at, updated_at)
        VALUES (?, ?, ?, 'F-001', 'GussetPlateSectionLoss', 'MEDIUM', '{}', 'CONFIRMED',
                'gemini-test', 'v1', 0.98, 'Confirmed by the assigned Inspector',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        candidateId,
        fixture.inspectionId(),
        evidenceId);

    confirmFindings(false);
    UUID versionId = insertDraftVersion();

    MvcResult result =
        mockMvc
            .perform(
                get(
                        "/api/v1/inspections/{id}/report/versions/{versionId}/preview",
                        fixture.inspectionId(),
                        versionId)
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andReturn();

    String reportText =
        extractText(result.getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
    assertThat(reportText)
        .as("an Inspector-confirmed finding must appear in the draft before any reviewer verdict")
        .contains("GussetPlateSectionLoss");
  }

  @Test
  void previewOmitsARejectedAiCandidate() throws Exception {
    UUID evidenceId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO evidence (id, organization_id, inspection_id,
                              evidence_kind, file_name, content_type, size_bytes,
                              object_key, upload_status, created_at)
        VALUES (?, ?, ?, 'INSPECTION', 'rejected-candidate.png', 'image/png', 64,
                'rejected-test-key', 'AVAILABLE', CURRENT_TIMESTAMP)
        """,
        evidenceId,
        fixture.organizationId(),
        fixture.inspectionId());
    UUID candidateId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO findings (id, inspection_id, evidence_id, finding_code,
                              defect_label, severity, bounding_box, status, model_name, model_version,
                              confidence, technical_notes,
                              deleted_at,
                              created_at, updated_at)
        VALUES (?, ?, ?, 'F-1', 'spurious-corroded-seam', 'MEDIUM', '{}', 'PENDING',
                'test-model', 'v1', 0.9, 'False positive', CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        candidateId,
        fixture.inspectionId(),
        evidenceId);
    confirmFindings(true);
    UUID versionId = insertDraftVersion();
    MvcResult result =
        mockMvc
            .perform(
                get(
                        "/api/v1/inspections/{id}/report/versions/{versionId}/preview",
                        fixture.inspectionId(),
                        versionId)
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andReturn();
    String reportText =
        extractText(result.getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
    assertThat(reportText).doesNotContain("spurious-corroded-seam");
  }

  @Test
  void aCrossOrganizationDownloadIsRefused() throws Exception {
    UUID versionId = approvedVersion();
    publish(versionId);

    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/document",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.outsiderId(), "ORG_ADMIN")))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void thePublishedDocumentCarriesSelectableTextAndTheAuthoredSections() throws Exception {
    UUID droneId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO drones (id, organization_id, serial_number, manufacturer, model, serviceability, created_at, updated_at, row_version) "
            + "VALUES (?, ?, 'DRONE-REPORT-01', 'SurveyCo', 'S2', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
        droneId,
        fixture.organizationId());
    jdbcTemplate.update(
        "UPDATE inspections SET drone_id = ? WHERE id = ?", droneId, fixture.inspectionId());
    UUID prepId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO inspection_preparations (id, inspection_id, inspector_user_id, preparation_version, shot_list, safety_observations, status, submitted_at, created_at, updated_at, row_version) "
            + "VALUES (?, ?, ?, 1, '[{\"code\":\"NORTH\",\"angle\":\"east face\"}]', 'No standing water', 'READY', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
        prepId,
        fixture.inspectionId(),
        fixture.inspectorId());
    jdbcTemplate.update(
        "INSERT INTO inspection_readiness_decisions (id, inspection_id, preparation_id, preparation_version, decision, reviewed_by_user_id, decided_at, reason, source_hash) "
            + "VALUES (?, ?, ?, 1, 'APPROVED', ?, CURRENT_TIMESTAMP, 'Ready for field session', ?)",
        UUID.randomUUID(),
        fixture.inspectionId(),
        prepId,
        fixture.reviewerId(),
        "a".repeat(64));
    UUID sessionId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO field_sessions (id, inspection_id, organization_id, inspector_user_id, drone_id, readiness_decision_id, status, started_at, pre_flight_checklist_note, created_at, updated_at, row_version) "
            + "VALUES (?, ?, ?, ?, ?, (SELECT id FROM inspection_readiness_decisions WHERE preparation_id = ?), 'IN_PROGRESS', CURRENT_TIMESTAMP, 'Bridge access checked; Drone identified', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
        sessionId,
        fixture.inspectionId(),
        fixture.organizationId(),
        fixture.inspectorId(),
        droneId,
        prepId);
    UUID versionId = approvedVersion();
    publish(versionId);

    byte[] document = downloadDocument(versionId);
    String text = extractText(document);

    // Selectable text is the point: the report is not an image of a page. The authored section and
    // the approval record are both readable, which is what a recipient of the report needs.
    assertThat(text).contains("Infrastructure Asset Inspection Report");
    assertThat(text).contains("Recorded condition assessment.");
    assertThat(text).contains("Executive summary");
    assertThat(text).contains("Approval and revisions");
    assertThat(text).contains("Not supplied");
    assertThat(text).contains(fixtureOrganizationName());
    assertThat(text).contains("Main span");
    assertThat(text).contains("District 1");
    assertThat(text).contains("SurveyCo");
    assertThat(text).contains("DRONE-REPORT-01");
    assertThat(text).contains("Planned shot list");
    assertThat(text).contains("NORTH");
    String normalizedText = text.replaceAll("\\s+", " ");
    assertThat(normalizedText).contains("Recorded field sessions");
    assertThat(normalizedText).contains("Bridge access checked; Drone identified");
    assertThat(normalizedText).contains("not item-level checklist verification or proof of flight");
    assertThat(normalizedText)
        .doesNotContain("Checklist responses, session logs and source documents are held");
  }

  // ------------------------------------------------------------------ helpers

  private String fixtureOrganizationName() {
    return jdbcTemplate.queryForObject(
        "SELECT display_name FROM organizations WHERE id = ?",
        String.class,
        fixture.organizationId());
  }

  private byte[] downloadDocument(UUID versionId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get(
                        "/api/v1/inspections/{id}/report/versions/{versionId}/document",
                        fixture.inspectionId(),
                        versionId)
                    .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
            .andExpect(status().isOk())
            .andReturn();
    return result.getResponse().getContentAsByteArray();
  }

  /**
   * Reads the delivered document's text back out of the PDF.
   *
   * <p>The assertion this supports is that the report carries selectable text rather than an image
   * of a page, so the content stream is what gets inspected. The stream may be compressed, so an
   * uncompressed readable stream is required for this extraction to work.
   */
  private static String extractText(byte[] document) {
    try (org.apache.pdfbox.pdmodel.PDDocument loaded =
        org.apache.pdfbox.pdmodel.PDDocument.load(document)) {
      org.apache.pdfbox.text.PDFTextStripper stripper =
          new org.apache.pdfbox.text.PDFTextStripper();
      return stripper.getText(loaded);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void publish(UUID versionId) throws Exception {
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/publish",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
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

  /** The same object key the service derives, so the assertion checks the real storage layout. */
  private static String reportObjectKey(UUID versionId) {
    return "reports/" + versionId + ".pdf";
  }

  /** Reads the stored bytes back through the production object store. */
  private byte[] storedBytes(String objectKey) throws IOException {
    try (InputStream input = objectStore.open(objectKey)) {
      return input.readAllBytes();
    }
  }

  private boolean objectExists(String objectKey) {
    try (InputStream input = objectStore.open(objectKey)) {
      input.readAllBytes();
      return true;
    } catch (IOException expectedAbsent) {
      return false;
    }
  }

  private String renderedKey(UUID versionId) {
    return jdbcTemplate.queryForObject(
        "SELECT rendered_object_key FROM inspection_reports WHERE id = ?", String.class, versionId);
  }

  private String renderedChecksum(UUID versionId) {
    return jdbcTemplate.queryForObject(
        "SELECT rendered_checksum_sha256 FROM inspection_reports WHERE id = ?",
        String.class,
        versionId);
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private UUID insertDraftVersion() throws Exception {
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

  private UUID approvedVersion() throws Exception {
    UUID versionId = insertDraftVersion();
    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/sections",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("executiveSummary", "Recorded condition assessment."))))
        .andExpect(status().isOk());
    authorVerifyAndSubmit(versionId);
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/review",
                    fixture.inspectionId(),
                    versionId)
                .param("approve", "true")
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isOk());
    return versionId;
  }

  private void authorVerifyAndSubmit(UUID versionId) throws Exception {
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

  private static RequestPostProcessor principal(UUID userId, String role) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}

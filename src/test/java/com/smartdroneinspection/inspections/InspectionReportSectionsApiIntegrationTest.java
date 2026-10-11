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
import com.smartdroneinspection.inspections.repository.AiFindingCandidateRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.AiInferencePort;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
@Import({
  TestcontainersConfiguration.class,
  InspectionReportSectionsApiIntegrationTest.SectionsStore.class
})
class InspectionReportSectionsApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;
  @Autowired InspectionRepository inspections;
  @Autowired AiFindingCandidateRepository candidates;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @MockitoBean AiInferencePort inferencePort;

  private InspectionTestFixture.Data fixture;
  private MockMvc mockMvc;

  @TestConfiguration(proxyBeanMethods = false)
  static class SectionsStore {
    static final Map<String, byte[]> OBJECTS = new ConcurrentHashMap<>();

    @Bean
    EvidenceObjectStore evidenceObjectStore() {
      return new EvidenceObjectStore() {
        @Override
        public void put(String objectKey, String contentType, long sizeBytes, InputStream input)
            throws IOException {
          OBJECTS.put(objectKey, input.readAllBytes());
        }

        @Override
        public InputStream open(String objectKey) throws IOException {
          byte[] content = OBJECTS.get(objectKey);
          if (content == null) {
            throw new IOException("missing object " + objectKey);
          }
          return new ByteArrayInputStream(content);
        }

        @Override
        public void delete(String objectKey) {
          OBJECTS.remove(objectKey);
        }
      };
    }
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
    SectionsStore.OBJECTS.clear();
    fixture = new InspectionTestFixture(users, inspections, jdbcTemplate).create();
    jdbcTemplate.update(
        "UPDATE inspections SET status = 'REPORT_DRAFT' WHERE id = ?", fixture.inspectionId());
  }

  // ------------------------------------------------------------------ MF3-08 sections

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
                            "purposeScopeCriteria",
                            "Objective: confirm coating integrity. Excluded: the eastern abutment.",
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
        .andExpect(jsonPath("$.data.sections.overallCondition").value("POOR"))
        .andExpect(
            jsonPath("$.data.sections.omissionDisclosure")
                .value("Submerged components were not observed."));
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
        .andExpect(jsonPath("$.data.llmModel").value("manual-authoring"));
  }

  @Test
  void sectionsAreRefusedOnceTheVersionHasLeftAnEditableState() throws Exception {
    UUID versionId = insertDraftVersion();
    jdbcTemplate.update(
        "UPDATE inspection_report_versions SET status = 'APPROVED' WHERE id = ?", versionId);

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
    UUID reportId = insertReport();
    UUID versionId = UUID.randomUUID();
    // A snapshot written before the section keys existed: valid JSON, none of the new fields. It
    // must
    // still read, with the absent sections reported as not supplied rather than failing the
    // version.
    jdbcTemplate.update(
        """
        INSERT INTO inspection_report_versions (id, inspection_report_id, version_no, status,
                                                author_user_id, content_snapshot, created_at)
        VALUES (?, ?, 1, 'DRAFT', ?, '{"model":"manual-authoring","narrative":"Older draft."}'::jsonb,
                CURRENT_TIMESTAMP)
        """,
        versionId,
        reportId,
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
        .andExpect(jsonPath("$.data.narrative").value("Older draft."))
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

    assertThat(SectionsStore.OBJECTS).isEmpty();
    assertThat(renderedKey(versionId)).isNull();
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
    byte[] stored = SectionsStore.OBJECTS.get(objectKey);
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
    byte[] first = SectionsStore.OBJECTS.get(renderedKey(versionId));
    String firstChecksum = renderedChecksum(versionId);

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/publish",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
        .andExpect(status().isConflict());

    assertThat(SectionsStore.OBJECTS.get(renderedKey(versionId))).isEqualTo(first);
    assertThat(renderedChecksum(versionId)).isEqualTo(firstChecksum);
  }

  @Test
  void publishingAFurtherVersionDoesNotOverwriteTheEarlierArtefact() throws Exception {
    UUID firstVersion = approvedVersion();
    publish(firstVersion);
    String firstKey = renderedKey(firstVersion);
    byte[] firstBytes = SectionsStore.OBJECTS.get(firstKey);

    UUID secondVersion = approvedVersion();
    jdbcTemplate.update(
        "UPDATE inspections SET status = 'REPORT_DRAFT' WHERE id = ?", fixture.inspectionId());
    publish(secondVersion);

    assertThat(renderedKey(secondVersion)).isNotEqualTo(firstKey);
    assertThat(SectionsStore.OBJECTS.get(firstKey)).isEqualTo(firstBytes);
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
  }

  // ------------------------------------------------------------------ helpers

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
            "SELECT count(*) FROM inspection_report_versions WHERE inspection_report_id = ?",
            Long.class,
            reportId());
    return count == null ? 0 : count;
  }

  private UUID reportId() {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM inspection_reports WHERE inspection_id = ?",
        UUID.class,
        fixture.inspectionId());
  }

  private String renderedKey(UUID versionId) {
    return jdbcTemplate.queryForObject(
        "SELECT rendered_object_key FROM inspection_report_versions WHERE id = ?",
        String.class,
        versionId);
  }

  private String renderedChecksum(UUID versionId) {
    return jdbcTemplate.queryForObject(
        "SELECT rendered_checksum_sha256 FROM inspection_report_versions WHERE id = ?",
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

  private UUID insertReport() {
    UUID reportId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspection_reports (id, inspection_id, author_user_id, status,
                                        current_version_number, created_at, updated_at, row_version)
        VALUES (?, ?, ?, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        ON CONFLICT (inspection_id) DO NOTHING
        """,
        reportId,
        fixture.inspectionId(),
        fixture.inspectorId());
    return reportId();
  }

  private UUID insertDraftVersion() {
    UUID reportId = insertReport();
    UUID versionId = UUID.randomUUID();
    Integer nextNo =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(version_no), 0) + 1 FROM inspection_report_versions"
                + " WHERE inspection_report_id = ?",
            Integer.class,
            reportId);
    jdbcTemplate.update(
        """
        INSERT INTO inspection_report_versions (id, inspection_report_id, version_no, status,
                                                author_user_id, llm_model, prompt_version,
                                                content_snapshot, created_at)
        VALUES (?, ?, ?, 'DRAFT', ?, 'manual-authoring', 'mf3-report-v1', '{}'::jsonb,
                CURRENT_TIMESTAMP)
        """,
        versionId,
        reportId,
        nextNo,
        fixture.inspectorId());
    return versionId;
  }

  private UUID approvedVersion() {
    UUID reportId = insertReport();
    UUID versionId = UUID.randomUUID();
    Integer nextNo =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(version_no), 0) + 1 FROM inspection_report_versions"
                + " WHERE inspection_report_id = ?",
            Integer.class,
            reportId);
    jdbcTemplate.update(
        """
        INSERT INTO inspection_report_versions (id, inspection_report_id, version_no, status,
                                                author_user_id, author_verified_by_user_id,
                                                author_verified_at, reviewer_user_id, reviewed_at,
                                                llm_model, prompt_version, content_snapshot, created_at)
        VALUES (?, ?, ?, 'APPROVED', ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP,
                'manual-authoring', 'mf3-report-v1',
                '{"executiveSummary":"Recorded condition assessment."}'::jsonb, CURRENT_TIMESTAMP)
        """,
        versionId,
        reportId,
        nextNo,
        fixture.inspectorId(),
        fixture.inspectorId(),
        fixture.reviewerId());
    return versionId;
  }

  private static RequestPostProcessor principal(UUID userId, String role) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}

package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * MF3-01 to MF3-04 through the real HTTP surface: intake, technical validation, and scoped upload.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InspectionEvidenceApiIntegrationTest {

  /**
   * A real 1x1 PNG. MF3-02 requires corrupt input to be refused, so the happy-path fixtures must be
   * bytes that genuinely decode -- a signature followed by filler would be rejected by that check.
   */
  private static final long MAX_EVIDENCE_SIZE_BYTES = 10L * 1024 * 1024;

  private static final byte[] PNG_BYTES =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
  private static final byte[] PNG_BYTES_2 =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAAAAAA6fptVAAAACklEQVR4nGNiAAAABgADNjd8qAAAAABJRU5ErkJggg==");

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;
  @Autowired InspectionRepository inspections;
  @Autowired EvidenceRepository evidence;
  @Autowired FindingRepository findings;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

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
  void assignedInspectorUploadsEvidenceAndItIsListed() throws Exception {
    UUID evidenceId = upload(fixture.inspectorId(), "INSPECTOR", "span.png", PNG_BYTES);

    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(evidenceId.toString()))
        .andExpect(jsonPath("$.data[0].fileName").value("span.png"))
        .andExpect(jsonPath("$.data[0].isDeleted").value(false));
  }

  @Test
  void repeatedUploadOfIdenticalBytesCreatesIndependentEvidenceRows() throws Exception {
    UUID first = upload(fixture.inspectorId(), "INSPECTOR", "span.png", PNG_BYTES);
    uploadResponse(fixture.inspectorId(), "INSPECTOR", "span.png", PNG_BYTES_2);

    MvcResult duplicate =
        uploadResponse(fixture.inspectorId(), "INSPECTOR", "renamed.png", PNG_BYTES);
    UUID second =
        UUID.fromString(
            objectMapper
                .readTree(duplicate.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .stringValue());

    assertThat(second).isNotEqualTo(first);
    Long storedRows =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM evidence WHERE inspection_id = ?",
            Long.class,
            fixture.inspectionId());
    assertThat(storedRows).isEqualTo(2L);
  }

  @Test
  void reuploadingAfterRetiringCaptureSucceedsAsNewEvidence() throws Exception {
    UUID evidenceId = upload(fixture.inspectorId(), "INSPECTOR", "span.png", PNG_BYTES);
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/retire",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Replacing the capture\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(new MockMultipartFile("file", "renamed.png", "image/png", PNG_BYTES))
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fileName").value("renamed.png"));
  }

  @Test
  void sharedEvidenceLocationIsReturnedOnTheEvidenceWithoutDuplicatingItOnFindings()
      throws Exception {
    UUID evidenceId = upload(fixture.inspectorId(), "INSPECTOR", "span.png", PNG_BYTES);
    UUID firstFinding = createManualFinding(evidenceId);
    UUID secondFinding = createManualFinding(evidenceId);

    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/location",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.reviewerId(), "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locationDescription\":\"Girder G4 east face\"}"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/location",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locationDescription\":\"Girder G4 east face\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.locationDescription").value("Girder G4 east face"));

    assertThat(
            evidence
                .findByIdAndInspectionId(evidenceId, fixture.inspectionId())
                .orElseThrow()
                .getLocationDescription())
        .isEqualTo("Girder G4 east face");
    assertThat(findings.findByIdAndInspectionId(firstFinding, fixture.inspectionId())).isPresent();
    assertThat(findings.findByIdAndInspectionId(secondFinding, fixture.inspectionId())).isPresent();
  }

  @Test
  void unsupportedTypeMultipartOversizeAndEmptyUploadsAreRejected() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()))
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("EVIDENCE_INVALID"));

    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("EVIDENCE_INVALID"));

    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(
                    new MockMultipartFile(
                        "file",
                        "oversize.png",
                        "image/png",
                        new byte[Math.toIntExact(MAX_EVIDENCE_SIZE_BYTES + 1)]))
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        // MockMvc parses multipart requests in memory, so the Spring multipart size guard cannot
        // fire here; on a real servlet container it may answer first with PAYLOAD_TOO_LARGE. This
        // test therefore observes the service guard's own limit and error code.
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.status").value(413))
        .andExpect(jsonPath("$.code").value("EVIDENCE_TOO_LARGE"));
  }

  @Test
  void missingGpsIsAcceptedButAHalfSuppliedCoordinateIsRejected() throws Exception {
    upload(fixture.inspectorId(), "INSPECTOR", "no-gps.png", PNG_BYTES);
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].latitude").doesNotExist())
        .andExpect(jsonPath("$.data[0].longitude").doesNotExist());

    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(new MockMultipartFile("file", "lat-only.png", "image/png", PNG_BYTES))
                .param("latitude", "10.5")
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("EVIDENCE_INVALID"));
  }

  @Test
  void anotherOrganizationAndAnotherInspectorCannotReachTheEvidence() throws Exception {
    UUID evidenceId = upload(fixture.inspectorId(), "INSPECTOR", "span.png", PNG_BYTES);

    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .with(principal(fixture.outsiderId(), "CUSTOMER")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INSPECTION_NOT_FOUND"));

    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(new MockMultipartFile("file", "intrusion.png", "image/png", "x".getBytes()))
                .with(principal(fixture.otherInspectorId(), "INSPECTOR")))
        .andExpect(status().isNotFound());

    // A qualified reviewer in the same organization may read the evidence behind the report.
    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/content",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.reviewerId(), "CUSTOMER")))
        .andExpect(status().isOk());
  }

  @Test
  void evidenceContentIsStreamedThroughTheBackend() throws Exception {
    UUID evidenceId = upload(fixture.inspectorId(), "INSPECTOR", "span.png", PNG_BYTES);

    MvcResult result =
        mockMvc
            .perform(
                get(
                        "/api/v1/inspections/{id}/evidence/{evidenceId}/content",
                        fixture.inspectionId(),
                        evidenceId)
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(PNG_BYTES);
  }

  @Test
  void evidenceUploadCanBeAssociatedWithACompletedSession() throws Exception {
    UUID sessionId = insertCompletedFieldSession();
    MvcResult response =
        uploadResponse(
            fixture.inspectorId(), "INSPECTOR", "completed-session.png", PNG_BYTES, sessionId);
    assertThat(
            objectMapper
                .readTree(response.getResponse().getContentAsString())
                .at("/data/fieldSessionId")
                .asString())
        .isEqualTo(sessionId.toString());
  }

  @Test
  void evidenceLocationCanBeEditedInReportDraftButNotWhileSubmitted() throws Exception {
    UUID evidenceId = upload(fixture.inspectorId(), "INSPECTOR", "span.png", PNG_BYTES);
    jdbcTemplate.update(
        "UPDATE inspections SET status = 'REPORT_DRAFT' WHERE id = ?", fixture.inspectionId());
    jdbcTemplate.update(
        "UPDATE inspections SET findings_confirmed_at = CURRENT_TIMESTAMP, findings_snapshot_hash = ? WHERE id = ?",
        "confirmed-hash",
        fixture.inspectionId());

    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/location",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locationDescription\":\"Updated in draft\"}"))
        .andExpect(status().isOk());

    jdbcTemplate.update(
        "INSERT INTO inspection_reports (id, inspection_id, version_no, status, content_snapshot, created_at, submitted_at) "
            + "VALUES (?, ?, 1, 'SUBMITTED', '{}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        UUID.randomUUID(),
        fixture.inspectionId());
    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/location",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locationDescription\":\"Must remain locked\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVIDENCE_LOCKED"));
  }

  @Test
  void evidenceUploadIsRejectedAfterReportPublication() throws Exception {
    UUID evidenceId = upload(fixture.inspectorId(), "INSPECTOR", "span.png", PNG_BYTES);
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings/confirm", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("noFindingsAcknowledged", true))))
        .andExpect(status().isOk());

    UUID versionId = createSubmittedManualVersion();
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/review",
                    fixture.inspectionId(),
                    versionId)
                .param("approve", "true")
                .with(principal(fixture.reviewerId(), "CUSTOMER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{versionId}/publish",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.reviewerId(), "CUSTOMER")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(new MockMultipartFile("file", "later.png", "image/png", PNG_BYTES))
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSPECTION_STATE_CONFLICT"));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evidence WHERE id = ?", Long.class, evidenceId))
        .isEqualTo(1L);
  }

  private UUID createSubmittedManualVersion() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/report/draft/manual", fixture.inspectionId())
                    .with(principal(fixture.inspectorId(), "INSPECTOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "narrative", "Surveyed the span", "omissionDisclosure", "None"))))
            .andExpect(status().isOk())
            .andReturn();
    UUID versionId =
        UUID.fromString(
            objectMapper
                .readTree(created.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .stringValue());
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
    return versionId;
  }

  private UUID createManualFinding(UUID evidenceId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/findings", fixture.inspectionId())
                    .with(principal(fixture.inspectorId(), "INSPECTOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "evidenceId", evidenceId,
                                "defectLabel", "Surface crack",
                                "severity", "MEDIUM",
                                "technicalNotes", "Visible surface crack"))))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .stringValue());
  }

  private UUID upload(UUID actorId, String role, String fileName, byte[] content) throws Exception {
    MvcResult result = uploadResponse(actorId, role, fileName, content);
    return UUID.fromString(
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .stringValue());
  }

  /** A completed field session for this inspection, which an additional session must follow. */
  private UUID insertCompletedFieldSession() {
    UUID sessionId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO field_sessions (id, inspection_id, "
            + "status, started_at, ended_at, created_at, updated_at, row_version) "
            + "VALUES (?, ?, 'FIELD_COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, "
            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
        sessionId,
        fixture.inspectionId());
    return sessionId;
  }

  private MvcResult uploadResponse(UUID actorId, String role, String fileName, byte[] content)
      throws Exception {
    return uploadResponse(actorId, role, fileName, content, null);
  }

  private MvcResult uploadResponse(
      UUID actorId, String role, String fileName, byte[] content, UUID fieldSessionId)
      throws Exception {
    var request =
        multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
            .file(new MockMultipartFile("file", fileName, "image/png", content))
            .with(principal(actorId, role));
    if (fieldSessionId != null) request.param("fieldSessionId", fieldSessionId.toString());
    return mockMvc.perform(request).andExpect(status().isOk()).andReturn();
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor principal(
      UUID userId, String role) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}

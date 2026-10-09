package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * MF3-01 to MF3-04 through the real HTTP surface: intake, technical validation, retry-safe upload,
 * and the Inspector's own adequacy decision.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, InspectionEvidenceApiIntegrationTest.StubStore.class})
class InspectionEvidenceApiIntegrationTest {

  private static final byte[] PNG_BYTES = {
    (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3
  };

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;
  @Autowired InspectionRepository inspections;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  /**
   * Storage is stubbed at the shared port: the bytes contract is covered by the MinIO adapter test.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class StubStore {
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
    StubStore.OBJECTS.clear();
  }

  @Test
  void assignedInspectorUploadsEvidenceAndItIsListedWithServerChecksum() throws Exception {
    UUID evidenceId = upload(fixture.inspectorId(), "INSPECTOR", "span.png", PNG_BYTES);

    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(evidenceId.toString()))
        .andExpect(jsonPath("$.data[0].fileName").value("span.png"))
        .andExpect(jsonPath("$.data[0].checksumSha256").value(sha256(PNG_BYTES)))
        .andExpect(jsonPath("$.data[0].uploadStatus").value("AVAILABLE"));
  }

  @Test
  void repeatedUploadOfIdenticalBytesIsIdempotent() throws Exception {
    UUID first = upload(fixture.inspectorId(), "INSPECTOR", "span.png", PNG_BYTES);
    UUID second = upload(fixture.inspectorId(), "INSPECTOR", "span-renamed.png", PNG_BYTES);

    assertThat(second).isEqualTo(first);
    Long storedRows =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM evidence WHERE inspection_id = ?",
            Long.class,
            fixture.inspectionId());
    assertThat(storedRows).isEqualTo(1L);
  }

  @Test
  void unsupportedTypeAndOversizeAndEmptyUploadsAreRejected() throws Exception {
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
  }

  @Test
  void missingGpsIsAcceptedButAHalfSuppliedCoordinateIsRejected() throws Exception {
    upload(fixture.inspectorId(), "INSPECTOR", "no-gps.png", PNG_BYTES);

    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(
                    new MockMultipartFile("file", "lat-only.png", "image/png", "other".getBytes()))
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
                .with(principal(fixture.outsiderId(), "ORG_ADMIN")))
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
                .with(principal(fixture.reviewerId(), "ORG_ADMIN")))
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
  void inspectorAcceptsTheEvidenceSetAfterReviewingIt() throws Exception {
    upload(fixture.inspectorId(), "INSPECTOR", "span.png", PNG_BYTES);

    // The Inspector writes prose, not JSON. The decision must still be storable and readable,
    // because shot_list_comparison is a jsonb column.
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
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.decision").value("ACCEPTED"))
        .andExpect(jsonPath("$.data.decidedByUserId").value(fixture.inspectorId().toString()))
        .andExpect(
            jsonPath("$.data.shotListComparison").value("Main span covered from both walkways"));
  }

  @Test
  void aShotListComparisonContainingQuotesSurvivesTheRoundTrip() throws Exception {
    upload(fixture.inspectorId(), "INSPECTOR", "span.png", PNG_BYTES);

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/evidence-quality-decisions", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "decision", "ACCEPTED",
                            "shotListComparison", "Inspector noted the \"east abutment\" twice"))))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.shotListComparison")
                .value("Inspector noted the \"east abutment\" twice"));
  }

  @Test
  void aLimitedDecisionMustStateItsLimitation() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/evidence-quality-decisions", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("decision", "LIMITED"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("EVIDENCE_QUALITY_INVALID"));

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/evidence-quality-decisions", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "decision", "LIMITED",
                            "limitationReason", "The east face was not observed"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.decision").value("LIMITED"));
  }

  @Test
  void onlyTheAssignedInspectorMayDecideEvidenceQuality() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/evidence-quality-decisions", fixture.inspectionId())
                .with(principal(fixture.otherInspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("decision", "ACCEPTED"))))
        .andExpect(status().isNotFound());
  }

  private UUID upload(UUID actorId, String role, String fileName, byte[] content) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                    .file(new MockMultipartFile("file", fileName, "image/png", content))
                    .with(principal(actorId, role)))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .stringValue());
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

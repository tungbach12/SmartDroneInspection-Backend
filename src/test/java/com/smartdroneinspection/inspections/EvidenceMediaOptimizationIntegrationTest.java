package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.repository.UserRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end HTTP integration coverage for image normalization/thumbnails, resumable 2 GiB-capped
 * video uploads, protected media-ticket Range playback, and video-safe PDF report rendering.
 */
@SpringBootTest(
    properties = {"app.evidence.chunk-size-bytes=5242880", "app.evidence.processing.enabled=false"})
@Import(TestcontainersConfiguration.class)
class EvidenceMediaOptimizationIntegrationTest {

  private static final int CHUNK_SIZE = 5 * 1024 * 1024;

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;
  @Autowired InspectionRepository inspections;
  @Autowired EvidenceRepository evidenceRepository;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @MockitoSpyBean EvidenceObjectStore objectStore;

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
  void oversizedImageIsNormalizedWithOriginalSizeAndThumbnailVariant() throws Exception {
    BufferedImage large = new BufferedImage(2600, 1300, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = large.createGraphics();
    try {
      g.setColor(new Color(20, 90, 180));
      g.fillRect(0, 0, 2600, 1300);
    } finally {
      g.dispose();
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(large, "jpeg", out);
    byte[] sourceBytes = out.toByteArray();

    MvcResult uploadResult =
        mockMvc
            .perform(
                multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                    .file(new MockMultipartFile("file", "tower.jpg", "image/jpeg", sourceBytes))
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.processingStatus").value("READY"))
            .andExpect(jsonPath("$.data.hasThumbnail").value(true))
            .andExpect(jsonPath("$.data.mediaWidth").value(2048))
            .andExpect(jsonPath("$.data.mediaHeight").value(1024))
            .andExpect(jsonPath("$.data.originalSizeBytes").value(sourceBytes.length))
            .andReturn();

    JsonNode data =
        objectMapper.readTree(uploadResult.getResponse().getContentAsString()).at("/data");
    UUID evidenceId = UUID.fromString(data.get("id").asString());

    MvcResult primaryResult =
        mockMvc
            .perform(
                get(
                        "/api/v1/inspections/{id}/evidence/{eid}/content",
                        fixture.inspectionId(),
                        evidenceId)
                    .with(principal(fixture.reviewerId(), "CUSTOMER")))
            .andExpect(status().isOk())
            .andReturn();
    BufferedImage primaryImg =
        ImageIO.read(new ByteArrayInputStream(primaryResult.getResponse().getContentAsByteArray()));
    assertThat(primaryImg.getWidth()).isEqualTo(2048);
    assertThat(primaryImg.getHeight()).isEqualTo(1024);

    MvcResult thumbResult =
        mockMvc
            .perform(
                get(
                        "/api/v1/inspections/{id}/evidence/{eid}/content",
                        fixture.inspectionId(),
                        evidenceId)
                    .param("variant", "thumb")
                    .with(principal(fixture.reviewerId(), "CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
            .andReturn();
    BufferedImage thumbImg =
        ImageIO.read(new ByteArrayInputStream(thumbResult.getResponse().getContentAsByteArray()));
    assertThat(thumbImg.getWidth()).isEqualTo(480);
    assertThat(thumbImg.getHeight()).isEqualTo(240);
  }

  @Test
  void resumableUploadLifecycleSupportsResumeCancelValidationAndScopeChecks() throws Exception {
    long tooLarge = (2L * 1024L * 1024L * 1024L) + 1L;
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/evidence/uploads", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "fileName", "too-big.mp4",
                            "contentType", "video/mp4",
                            "totalSizeBytes", tooLarge))))
        .andExpect(status().isContentTooLarge())
        .andExpect(jsonPath("$.code").value("EVIDENCE_TOO_LARGE"));

    long totalSize = CHUNK_SIZE + 1024L;
    MvcResult beginResult =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/evidence/uploads", fixture.inspectionId())
                    .with(principal(fixture.inspectorId(), "INSPECTOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "fileName", "flight-pass.mp4",
                                "contentType", "video/mp4",
                                "totalSizeBytes", totalSize))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("OPEN"))
            .andExpect(jsonPath("$.data.totalParts").value(2))
            .andReturn();

    UUID sessionId =
        UUID.fromString(
            objectMapper
                .readTree(beginResult.getResponse().getContentAsString())
                .at("/data/sessionId")
                .asString());

    // Unassigned inspector cannot access or upload to this session.
    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/evidence/uploads/{sid}",
                    fixture.inspectionId(),
                    sessionId)
                .with(principal(fixture.otherInspectorId(), "INSPECTOR")))
        .andExpect(status().isNotFound());

    byte[] part1 = new byte[CHUNK_SIZE];
    Arrays.fill(part1, (byte) 'A');
    byte[] part2 = new byte[1024];
    Arrays.fill(part2, (byte) 'B');

    // Wrong size part rejected.
    mockMvc
        .perform(
            putMultipart(
                    "/api/v1/inspections/{id}/evidence/uploads/{sid}/parts/1",
                    fixture.inspectionId(),
                    sessionId)
                .file(new MockMultipartFile("file", "chunk1", "application/octet-stream", part2))
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UPLOAD_PART_SIZE_MISMATCH"));

    // Upload part 1, verify status, attempt premature complete -> 409.
    mockMvc
        .perform(
            putMultipart(
                    "/api/v1/inspections/{id}/evidence/uploads/{sid}/parts/1",
                    fixture.inspectionId(),
                    sessionId)
                .file(new MockMultipartFile("file", "chunk1", "application/octet-stream", part1))
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.uploadedPartNumbers[0]").value(1));

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/uploads/{sid}/complete",
                    fixture.inspectionId(),
                    sessionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("UPLOAD_PARTS_INCOMPLETE"));

    // Idempotent retry of part 1 + upload part 2 + complete + idempotent second complete.
    mockMvc
        .perform(
            putMultipart(
                    "/api/v1/inspections/{id}/evidence/uploads/{sid}/parts/1",
                    fixture.inspectionId(),
                    sessionId)
                .file(new MockMultipartFile("file", "chunk1", "application/octet-stream", part1))
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            putMultipart(
                    "/api/v1/inspections/{id}/evidence/uploads/{sid}/parts/2",
                    fixture.inspectionId(),
                    sessionId)
                .file(new MockMultipartFile("file", "chunk2", "application/octet-stream", part2))
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.uploadedPartNumbers.length()").value(2));

    MvcResult completed =
        mockMvc
            .perform(
                post(
                        "/api/v1/inspections/{id}/evidence/uploads/{sid}/complete",
                        fixture.inspectionId(),
                        sessionId)
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PROCESSING"))
            .andExpect(jsonPath("$.data.evidenceId").isNotEmpty())
            .andReturn();

    UUID evidenceId =
        UUID.fromString(
            objectMapper
                .readTree(completed.getResponse().getContentAsString())
                .at("/data/evidenceId")
                .asString());

    // Idempotent re-complete stays safe.
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/uploads/{sid}/complete",
                    fixture.inspectionId(),
                    sessionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PROCESSING"))
        .andExpect(jsonPath("$.data.evidenceId").value(evidenceId.toString()));

    // Not-ready video cannot be streamed or issued a ticket.
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{eid}/media-ticket",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVIDENCE_NOT_READY"));

    // Re-uploading the same filename (even after retiring the original video) allocates (1), (2)
    // and never blocks the upload session.
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{eid}/retire",
                    fixture.inspectionId(),
                    evidenceId)
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Retire original before re-upload\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/evidence/uploads", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "fileName", "flight-pass.mp4",
                            "contentType", "video/mp4",
                            "totalSizeBytes", totalSize))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fileName").value("flight-pass (1).mp4"));

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/evidence/uploads", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "fileName", "flight-pass.mp4",
                            "contentType", "video/mp4",
                            "totalSizeBytes", totalSize))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fileName").value("flight-pass (2).mp4"));

    // Separate session can be cancelled cleanly.
    MvcResult cancelBegin =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/evidence/uploads", fixture.inspectionId())
                    .with(principal(fixture.inspectorId(), "INSPECTOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "fileName", "cancelled.mp4",
                                "contentType", "video/mp4",
                                "totalSizeBytes", totalSize))))
            .andExpect(status().isOk())
            .andReturn();
    UUID cancelId =
        UUID.fromString(
            objectMapper
                .readTree(cancelBegin.getResponse().getContentAsString())
                .at("/data/sessionId")
                .asString());

    mockMvc
        .perform(
            delete(
                    "/api/v1/inspections/{id}/evidence/uploads/{sid}",
                    fixture.inspectionId(),
                    cancelId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"));
  }

  @Test
  void protectedTicketRangeStreamingRetryAndVideoSafeReportsWorkEndToEnd() throws Exception {
    byte[] payload = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes(StandardCharsets.US_ASCII);
    String processedKey = "inspections/" + fixture.inspectionId() + "/synthetic-ready/video.mp4";
    String posterKey = "inspections/" + fixture.inspectionId() + "/synthetic-ready/poster.jpg";
    objectStore.put(processedKey, "video/mp4", payload.length, new ByteArrayInputStream(payload));
    objectStore.put(posterKey, "image/jpeg", 4, new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));

    Evidence video =
        new Evidence(
            fixture.inspectionId(),
            null,
            "bridge-overview.mp4",
            "video/mp4",
            payload.length,
            processedKey,
            null,
            null,
            null,
            null,
            Instant.now());
    video.markVideoReady(
        processedKey, payload.length, posterKey, 4L, 1920, 1080, 14.2, Instant.now());
    Evidence savedVideo = evidenceRepository.saveAndFlush(video);

    MvcResult ticketRes =
        mockMvc
            .perform(
                post(
                        "/api/v1/inspections/{id}/evidence/{eid}/media-ticket",
                        fixture.inspectionId(),
                        savedVideo.getId())
                    .with(principal(fixture.reviewerId(), "CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ticket").isNotEmpty())
            .andExpect(jsonPath("$.data.streamUrl").isNotEmpty())
            .andReturn();

    String ticket =
        objectMapper
            .readTree(ticketRes.getResponse().getContentAsString())
            .at("/data/ticket")
            .asString();

    // 200 full stream without Bearer header, using scoped ticket.
    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/evidence/{eid}/stream",
                    fixture.inspectionId(),
                    savedVideo.getId())
                .param("ticket", ticket))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
        .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, payload.length))
        .andExpect(content().bytes(payload));

    // HEAD returns headers and empty body.
    MvcResult headRes =
        mockMvc
            .perform(
                head(
                        "/api/v1/inspections/{id}/evidence/{eid}/stream",
                        fixture.inspectionId(),
                        savedVideo.getId())
                    .param("ticket", ticket))
            .andExpect(status().isOk())
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, payload.length))
            .andReturn();
    assertThat(headRes.getResponse().getContentAsByteArray()).isEmpty();

    // 206 Partial Content byte range.
    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/evidence/{eid}/stream",
                    fixture.inspectionId(),
                    savedVideo.getId())
                .param("ticket", ticket)
                .header(HttpHeaders.RANGE, "bytes=10-15"))
        .andExpect(status().isPartialContent())
        .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 10-15/" + payload.length))
        .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 6))
        .andExpect(content().bytes("ABCDEF".getBytes(StandardCharsets.US_ASCII)));

    // 416 Unsatisfiable range.
    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/evidence/{eid}/stream",
                    fixture.inspectionId(),
                    savedVideo.getId())
                .param("ticket", ticket)
                .header(HttpHeaders.RANGE, "bytes=9999-"))
        .andExpect(status().isRequestedRangeNotSatisfiable())
        .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */" + payload.length));

    // Tampered ticket or wrong inspection fails 401.
    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/evidence/{eid}/stream",
                    UUID.randomUUID(),
                    savedVideo.getId())
                .param("ticket", ticket))
        .andExpect(status().isUnauthorized());

    // Failed video can be retried while inspection evidence is editable (when raw exists).
    Evidence failedVideo =
        new Evidence(
            fixture.inspectionId(),
            null,
            "retry-me.mp4",
            "video/mp4",
            100L,
            "inspections/" + fixture.inspectionId() + "/raw/retry.mp4",
            null,
            null,
            null,
            null,
            Instant.now());
    failedVideo.markVideoPending("inspections/" + fixture.inspectionId() + "/raw/retry.mp4", 100L);
    failedVideo.markProcessingFailed("transient ffmpeg failure");
    Evidence savedFailed = evidenceRepository.saveAndFlush(failedVideo);

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{eid}/retry-processing",
                    fixture.inspectionId(),
                    savedFailed.getId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.processingStatus").value("PENDING"));

    // Clear prior streaming invocations so we assert ReportService PDF rendering never reads video
    // bytes.
    org.mockito.Mockito.clearInvocations(objectStore);

    // Report PDF preview skips reading video bytes into memory.
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings/confirm", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noFindingsAcknowledged\":true}"))
        .andExpect(status().isOk());

    MvcResult draftRes =
        mockMvc
            .perform(
                post("/api/v1/inspections/{id}/report/draft/manual", fixture.inspectionId())
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andReturn();
    UUID versionId =
        UUID.fromString(
            objectMapper
                .readTree(draftRes.getResponse().getContentAsString())
                .at("/data/id")
                .asString());

    MvcResult pdfRes =
        mockMvc
            .perform(
                get(
                        "/api/v1/inspections/{id}/report/versions/{vid}/preview",
                        fixture.inspectionId(),
                        versionId)
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn();
    assertThat(
            new String(
                pdfRes.getResponse().getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII))
        .isEqualTo("%PDF-");
    verify(objectStore, never()).open(processedKey);
  }

  private static MockMultipartHttpServletRequestBuilder putMultipart(
      String urlTemplate, Object... uriVars) {
    MockMultipartHttpServletRequestBuilder builder = multipart(urlTemplate, uriVars);
    builder.with(
        request -> {
          request.setMethod("PUT");
          return request;
        });
    return builder;
  }

  private static org.springframework.security.test.web.servlet.request
          .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
      principal(UUID userId, String role) {
    return jwt()
        .jwt(builder -> builder.subject(userId.toString()).claim("roles", List.of(role)))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}

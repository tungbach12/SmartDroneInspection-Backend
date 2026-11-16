package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.inspections.api.dto.request.PromoteVideoKeyframesRequest;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.service.VideoProcessingWorker;
import com.smartdroneinspection.inspections.spi.AiInferencePort;
import com.smartdroneinspection.users.repository.UserRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end verification for video-to-frame traceability ({@code sourceVideoEvidenceId}, {@code
 * sourceTimestampSeconds}) and the two-step YOLO11 video scan -> inspector keyframe promotion flow.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VideoEvidenceTraceAndScanIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;
  @Autowired InspectionRepository inspections;
  @Autowired EvidenceRepository evidenceRepository;
  @Autowired FindingRepository findingRepository;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  @MockitoBean AiInferencePort inferencePort;
  @MockitoBean VideoProcessingWorker videoProcessingWorker;

  @TempDir Path tempDir;

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
  void uploadWithVideoTraceabilityValidatesAndPersistsSourceLink() throws Exception {
    byte[] videoBytes = createSyntheticMp4Bytes(tempDir.resolve("trace-source.mp4"));
    byte[] pngBytes = createSyntheticPngBytes();

    UUID videoId = uploadMedia("flight-orbit.mp4", "video/mp4", videoBytes, null, null);
    UUID stillId = uploadMedia("standalone-still.png", "image/png", pngBytes, null, null);

    // Negative timestamp rejected
    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(new MockMultipartFile("file", "bad-ts.png", "image/png", pngBytes))
                .param("sourceVideoEvidenceId", videoId.toString())
                .param("sourceTimestampSeconds", "-1.0")
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isBadRequest());

    // Timestamp without sourceVideoEvidenceId rejected
    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(new MockMultipartFile("file", "missing-video.png", "image/png", pngBytes))
                .param("sourceTimestampSeconds", "2.5")
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isBadRequest());

    // Non-video source evidence rejected
    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(new MockMultipartFile("file", "non-video.png", "image/png", pngBytes))
                .param("sourceVideoEvidenceId", stillId.toString())
                .param("sourceTimestampSeconds", "1.0")
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isBadRequest());

    // Valid video source linkage persists and is returned on upload + list
    mockMvc
        .perform(
            multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .file(new MockMultipartFile("file", "frame-01.png", "image/png", pngBytes))
                .param("sourceVideoEvidenceId", videoId.toString())
                .param("sourceTimestampSeconds", "1.5")
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sourceVideoEvidenceId").value(videoId.toString()))
        .andExpect(jsonPath("$.data.sourceTimestampSeconds").value(1.5));

    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data[?(@.fileName=='frame-01.png')].sourceVideoEvidenceId")
                .value(videoId.toString()));
  }

  @Test
  void twoStepVideoScanDoesNotMutateUntilInspectorPromotesKeyframeIntoEvidenceAndFindings()
      throws Exception {
    byte[] videoBytes = createSyntheticMp4Bytes(tempDir.resolve("orbit-scan.mp4"));
    UUID videoId = uploadMedia("commercial_tower_orbit.mp4", "video/mp4", videoBytes, null, null);

    String bboxJson = "{\"xMin\":0.15,\"yMin\":0.25,\"xMax\":0.55,\"yMax\":0.65}";
    AiInferencePort.Detection abscission =
        new AiInferencePort.Detection(
            "YOLO11", "yolo11s", "mbdd2025-v1", "abscission", BigDecimal.valueOf(0.87), bboxJson);
    when(inferencePort.analyze(any(byte[].class), anyString(), anyBoolean()))
        .thenReturn(List.of(), List.of(abscission), List.of());

    // Step 1: scan video -> non-mutating timeline markers with preview images
    MvcResult scanResult =
        mockMvc
            .perform(
                post(
                        "/api/v1/inspections/{id}/evidence/{evidenceId}/video-scan",
                        fixture.inspectionId(),
                        videoId)
                    .param("intervalSeconds", "1.0")
                    .param("maxFrames", "5")
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.videoEvidenceId").value(videoId.toString()))
            .andExpect(jsonPath("$.data.scannedFrames").isNumber())
            .andExpect(jsonPath("$.data.markers[0].primaryLabel").value("abscission"))
            .andExpect(jsonPath("$.data.markers[0].previewBase64").isNotEmpty())
            .andReturn();

    JsonNode scanData =
        objectMapper.readTree(scanResult.getResponse().getContentAsString()).path("data");
    assertThat(scanData.path("scannedFrames").asInt()).isGreaterThanOrEqualTo(2);
    double detectedTimestamp = scanData.path("markers").get(0).path("timestampSeconds").asDouble();

    // Scanning must NOT persist child evidence or findings yet
    assertThat(
            evidenceRepository.findByInspectionIdAndIsDeletedFalseOrderByCreatedAtAsc(
                fixture.inspectionId()))
        .hasSize(1);
    assertThat(findingRepository.findByInspectionIdOrderByCreatedAtAsc(fixture.inspectionId()))
        .isEmpty();

    // Step 2: inspector promotes selected marker (leaving imageBase64 null to verify FFmpeg
    // keyframe extraction + linked Evidence + AI Finding creation)
    PromoteVideoKeyframesRequest promoteRequest =
        new PromoteVideoKeyframesRequest(
            List.of(
                new PromoteVideoKeyframesRequest.KeyframeSelection(
                    detectedTimestamp,
                    "orbit-east-spalling.jpg",
                    "East facade parapet",
                    null,
                    List.of(
                        new PromoteVideoKeyframesRequest.DetectionPayload(
                            "yolo11s", "abscission", BigDecimal.valueOf(0.87), bboxJson)))));

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/evidence/{evidenceId}/video-keyframes",
                    fixture.inspectionId(),
                    videoId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(promoteRequest))
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.extractedEvidenceCount").value(1))
        .andExpect(jsonPath("$.data.createdFindingsCount").value(1))
        .andExpect(
            jsonPath("$.data.keyframes[0].evidence.sourceVideoEvidenceId")
                .value(videoId.toString()))
        .andExpect(
            jsonPath("$.data.keyframes[0].evidence.sourceTimestampSeconds")
                .value(detectedTimestamp));

    List<Evidence> allActiveEvidence =
        evidenceRepository.findByInspectionIdAndIsDeletedFalseOrderByCreatedAtAsc(
            fixture.inspectionId());
    assertThat(allActiveEvidence).hasSize(2);
    Evidence childFrame =
        allActiveEvidence.stream()
            .filter(item -> !item.getId().equals(videoId))
            .findFirst()
            .orElseThrow();
    assertThat(childFrame.getSourceVideoEvidenceId()).isEqualTo(videoId);
    assertThat(childFrame.getSourceTimestampSeconds()).isEqualTo(detectedTimestamp);

    var createdFindings =
        findingRepository.findByInspectionIdOrderByCreatedAtAsc(fixture.inspectionId());
    assertThat(createdFindings).hasSize(1);
    assertThat(createdFindings.getFirst().getEvidenceId()).isEqualTo(childFrame.getId());
    assertThat(createdFindings.getFirst().getDefectLabel()).isEqualTo("abscission");
  }

  private UUID uploadMedia(
      String fileName,
      String contentType,
      byte[] content,
      UUID sourceVideoEvidenceId,
      Double sourceTimestampSeconds)
      throws Exception {
    var request =
        multipart("/api/v1/inspections/{id}/evidence", fixture.inspectionId())
            .file(new MockMultipartFile("file", fileName, contentType, content))
            .with(principal(fixture.inspectorId(), "INSPECTOR"));
    if (sourceVideoEvidenceId != null) {
      request.param("sourceVideoEvidenceId", sourceVideoEvidenceId.toString());
    }
    if (sourceTimestampSeconds != null) {
      request.param("sourceTimestampSeconds", String.valueOf(sourceTimestampSeconds));
    }
    MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    return UUID.fromString(
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .stringValue());
  }

  private static byte[] createSyntheticPngBytes() throws Exception {
    BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.DARK_GRAY);
    g.fillRect(0, 0, 32, 32);
    g.dispose();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(image, "png", baos);
    return baos.toByteArray();
  }

  private static byte[] createSyntheticMp4Bytes(Path outFile) throws Exception {
    Process generate =
        new ProcessBuilder(
                List.of(
                    "ffmpeg",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-y",
                    "-f",
                    "lavfi",
                    "-i",
                    "testsrc=duration=3:size=320x240:rate=10",
                    "-c:v",
                    "libx264",
                    "-preset",
                    "ultrafast",
                    "-pix_fmt",
                    "yuv420p",
                    outFile.toAbsolutePath().toString()))
            .start();
    assertThat(generate.waitFor()).isZero();
    return Files.readAllBytes(outFile);
  }

  private static RequestPostProcessor principal(UUID userId, String role) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}

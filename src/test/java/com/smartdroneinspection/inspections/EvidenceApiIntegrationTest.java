package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionAssignmentRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionQuotationRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionRequestRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionServiceOrderRepository;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.service.InspectionService;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

// S3Mock provides the S3 endpoint for API tests; it does not emulate MinIO-specific behavior.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EvidenceApiIntegrationTest {

  private static final String STORAGE_ACCESS_KEY = "wf3-test-access";
  private static final String STORAGE_SECRET_KEY = "wf3-test-secret-key-123";
  private static final GenericContainer<?> S3_MOCK =
      new GenericContainer<>(DockerImageName.parse("adobe/s3mock:5.2.3"))
          .withExposedPorts(9090)
          .waitingFor(Wait.forHttp("/favicon.ico").forPort(9090).forStatusCode(200));

  private static final byte[] ONE_PIXEL_PNG =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/o2cAAAAASUVORK5CYII=");

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired InspectionService inspections;
  @Autowired EvidenceRepository evidence;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired InspectionRequestRepository requests;
  @Autowired InspectionQuotationRepository quotations;
  @Autowired InspectionServiceOrderRepository serviceOrders;
  @Autowired InspectionAssignmentRepository assignments;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  private InspectionFixture.Data fixture;
  private UUID inspectionId;
  private MockMvc mockMvc;

  @DynamicPropertySource
  static void s3MockProperties(DynamicPropertyRegistry registry) {
    S3_MOCK.start();
    registry.add("app.storage.minio.enabled", () -> true);
    registry.add(
        "app.storage.minio.endpoint",
        () -> "http://" + S3_MOCK.getHost() + ":" + S3_MOCK.getMappedPort(9090));
    registry.add("app.storage.minio.bucket", () -> "evidence-api-test");
    registry.add("app.storage.minio.access-key", () -> STORAGE_ACCESS_KEY);
    registry.add("app.storage.minio.secret-key", () -> STORAGE_SECRET_KEY);
  }

  @AfterAll
  static void stopS3Mock() {
    S3_MOCK.stop();
  }

  @BeforeEach
  void setUp() throws Exception {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    fixture =
        new InspectionFixture(
                categories,
                templates,
                assets,
                requests,
                quotations,
                serviceOrders,
                assignments,
                users,
                jdbcTemplate)
            .create();
    inspectionId = inspections.start(fixture.inspectorId(), fixture.assignmentId()).inspectionId();
  }

  @Test
  void persistsMetadataAndStreamsTheStoredBytesOnlyToTheAcceptedAssignee() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/inspections/{inspectionId}/evidence", inspectionId)
                .file(new MockMultipartFile("file", "bridge.png", "image/png", ONE_PIXEL_PNG))
                .param("source", "WEB_UPLOAD")
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Success"))
        .andExpect(jsonPath("$.data.checksumSha256").isNotEmpty())
        .andExpect(jsonPath("$.data.uploadStatus").value("AVAILABLE"))
        .andExpect(jsonPath("$.data.objectKey").doesNotExist());

    var savedEvidence = evidence.findAll().getFirst();
    assertThat(savedEvidence.getInspectionId()).isEqualTo(inspectionId);
    assertThat(savedEvidence.getUploadStatus().name()).isEqualTo("AVAILABLE");

    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{inspectionId}/evidence/{evidenceId}/content",
                    inspectionId,
                    savedEvidence.getId())
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentAsByteArray())
                    .containsExactly(ONE_PIXEL_PNG));

    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{inspectionId}/evidence/{evidenceId}/content",
                    inspectionId,
                    savedEvidence.getId())
                .with(inspector(fixture.otherOrganizationInspectorId())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSPECTION_SCOPE_DENIED"));
  }

  @Test
  void mapsCorruptUploadToStableProblemDetailWithoutPersistingIt() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/inspections/{inspectionId}/evidence", inspectionId)
                .file(new MockMultipartFile("file", "bridge.png", "image/png", "bad".getBytes()))
                .param("source", "WEB_UPLOAD")
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("EVIDENCE_INVALID"));

    assertThat(evidence.findAll()).isEmpty();
  }

  @Test
  void returnsChecklistAndSavedResponseOnlyToTheAcceptedAssignee() throws Exception {
    mockMvc
        .perform(
            put(
                    "/api/v1/inspections/{inspectionId}/checklist-responses/{checklistItemId}",
                    inspectionId,
                    fixture.checklistItemId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"responseValue\":{\"value\":\"PASS\"},\"notes\":\"Surface checked\"}")
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/inspections/{inspectionId}/checklist", inspectionId)
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].itemId").value(fixture.checklistItemId().toString()))
        .andExpect(jsonPath("$.data[0].itemCode").value("SURFACE-CONDITION"))
        .andExpect(jsonPath("$.data[0].responseValue.value").value("PASS"))
        .andExpect(jsonPath("$.data[0].notes").value("Surface checked"))
        .andExpect(jsonPath("$.data[0].completedAt").isNotEmpty());

    mockMvc
        .perform(
            get("/api/v1/inspections/{inspectionId}/checklist", inspectionId)
                .with(inspector(fixture.otherInspectorId())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSPECTION_SCOPE_DENIED"));

    mockMvc
        .perform(
            get("/api/v1/inspections/{inspectionId}/checklist", inspectionId)
                .with(inspector(fixture.otherOrganizationInspectorId())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSPECTION_SCOPE_DENIED"));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor inspector(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_INSPECTOR"));
  }
}

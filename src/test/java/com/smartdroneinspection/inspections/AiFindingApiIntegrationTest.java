package com.smartdroneinspection.inspections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.smartdroneinspection.inspections.repository.VerifiedFindingRepository;
import com.smartdroneinspection.inspections.service.InspectionService;
import com.smartdroneinspection.inspections.spi.AiInferencePort;
import com.smartdroneinspection.inspections.spi.EvidenceObjectStore;
import com.smartdroneinspection.users.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AiFindingApiIntegrationTest {

  private static final byte[] ONE_PIXEL_PNG =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/o2cAAAAASUVORK5CYII=");

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired InspectionService inspections;
  @Autowired EvidenceRepository evidence;
  @Autowired VerifiedFindingRepository findings;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired InspectionRequestRepository requests;
  @Autowired InspectionQuotationRepository quotations;
  @Autowired InspectionServiceOrderRepository serviceOrders;
  @Autowired InspectionAssignmentRepository assignments;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @MockitoBean EvidenceObjectStore objectStore;
  @MockitoBean AiInferencePort inference;

  private final Map<String, byte[]> storedObjects = new ConcurrentHashMap<>();
  private InspectionFixture.Data fixture;
  private UUID inspectionId;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() throws Exception {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    storedObjects.clear();
    doAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              InputStream input = invocation.getArgument(3);
              storedObjects.put(key, input.readAllBytes());
              return null;
            })
        .when(objectStore)
        .put(anyString(), anyString(), anyLong(), any());
    when(objectStore.open(anyString()))
        .thenAnswer(
            invocation -> new ByteArrayInputStream(storedObjects.get(invocation.getArgument(0))));
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
  void keepsAiOutputAdvisoryUntilAssignedInspectorConfirmsIt() throws Exception {
    when(inference.analyze(any(), anyString()))
        .thenReturn(
            java.util.List.of(
                new AiInferencePort.Detection(
                    "yolo-crack",
                    "v3",
                    "surface-crack",
                    new BigDecimal("0.92"),
                    "{\"x\":0.1,\"y\":0.2,\"width\":0.3,\"height\":0.4}")));

    mockMvc
        .perform(
            multipart("/api/v1/inspections/{inspectionId}/evidence", inspectionId)
                .file(new MockMultipartFile("file", "bridge.png", "image/png", ONE_PIXEL_PNG))
                .param("source", "WEB_UPLOAD")
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isCreated());
    UUID evidenceId = evidence.findAll().getFirst().getId();

    String candidateResponse =
        mockMvc
            .perform(
                post(
                        "/api/v1/inspections/{inspectionId}/evidence/{evidenceId}/analyze",
                        inspectionId,
                        evidenceId)
                    .with(inspector(fixture.inspectorId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].status").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID candidateId =
        UUID.fromString(
            com.jayway.jsonpath.JsonPath.read(candidateResponse, "$.data[0].id").toString());

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{inspectionId}/finding-candidates/{candidateId}/review",
                    inspectionId,
                    candidateId)
                .contentType("application/json")
                .content(
                    "{\"decision\":\"CONFIRM\",\"severity\":\"HIGH\","
                        + "\"locationDescription\":\"North span\","
                        + "\"technicalNotes\":\"Verified on site\"}")
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

    org.assertj.core.api.Assertions.assertThat(findings.findAll())
        .singleElement()
        .satisfies(
            finding ->
                org.assertj.core.api.Assertions.assertThat(finding.getAiCandidateId())
                    .isEqualTo(candidateId));
  }

  @Test
  void rejectsOutOfScopeReviewAndAiFailureWithoutChangingStoredEvidence() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/inspections/{inspectionId}/evidence", inspectionId)
                .file(new MockMultipartFile("file", "bridge.png", "image/png", ONE_PIXEL_PNG))
                .param("source", "WEB_UPLOAD")
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isCreated());
    UUID evidenceId = evidence.findAll().getFirst().getId();
    when(inference.analyze(any(), anyString())).thenThrow(new IOException("offline"));
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{inspectionId}/evidence/{evidenceId}/analyze",
                    inspectionId,
                    evidenceId)
                .with(inspector(fixture.inspectorId())))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AI_INFERENCE_UNAVAILABLE"));
    org.assertj.core.api.Assertions.assertThat(evidence.findAll()).hasSize(1);
    org.assertj.core.api.Assertions.assertThat(findings.findAll()).isEmpty();

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{inspectionId}/finding-candidates/{candidateId}/review",
                    inspectionId,
                    UUID.randomUUID())
                .contentType("application/json")
                .content("{\"decision\":\"REJECT\",\"rejectionReason\":\"False positive\"}")
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

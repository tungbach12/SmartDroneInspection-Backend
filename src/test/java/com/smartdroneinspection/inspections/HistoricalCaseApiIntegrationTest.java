package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.service.HistoricalCaseService;
import com.smartdroneinspection.inspections.spi.HistoricalCaseAdvisorPort;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * MF3 historical case recommendations through the real HTTP, service, JDBC and pgvector path.
 *
 * <p>Anonymized benchmarks are seeded by Flyway; private cases are produced by report publication.
 * Every assertion uses the fixture's own inspection, so a cross-tenant leak would be visible.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HistoricalCaseApiIntegrationTest {

  private static final byte[] PNG_BYTES =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired UserRepository users;
  @Autowired InspectionRepository inspections;

  @MockitoBean HistoricalCaseAdvisorPort advisorPort;

  private InspectionTestFixture.Data fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    // Seeded benchmarks have no embedding by default; keep that state deterministic across suites.
    jdbcTemplate.update(
        "UPDATE historical_finding_cases SET embedding = NULL WHERE is_anonymized_benchmark = TRUE");
    when(advisorPort.embed(anyString())).thenReturn(Optional.empty());
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
    fixture = new InspectionTestFixture(users, inspections, jdbcTemplate).create();
  }

  @Test
  void seededBenchmarksAreReturnedInHybridMode() throws Exception {
    String body =
        mockMvc
            .perform(
                get("/api/v1/inspections/{id}/knowledge-recommendations", fixture.inspectionId())
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.retrievalMode").value("HYBRID_MATCH"))
            .andExpect(jsonPath("$.data.cases.length()").value(4))
            .andExpect(
                jsonPath("$.data.guidelines.length()").value(org.hamcrest.Matchers.greaterThan(0)))
            .andExpect(jsonPath("$.data.cases[0].assetCategory").isString())
            .andExpect(jsonPath("$.data.cases[0].outcome").isString())
            .andExpect(jsonPath("$.data.cases[0].sameAsset").value(false))
            .andExpect(jsonPath("$.data.cases[0].sameOrganization").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode data = objectMapper.readTree(body).path("data");
    assertThat(data.path("cases")).hasSize(4);
    for (JsonNode item : data.path("cases")) {
      double similarity = item.path("similarity").asDouble();
      assertThat(similarity).isBetween(0.0d, 1.0d);
      assertThat(item.path("publishedAt").isNull()).isFalse();
    }
    assertThat(data.path("guidelines").get(0).path("standardCode").asString()).isNotBlank();
    assertThat(data.path("guidelines").get(0).path("similarity").asDouble()).isBetween(0.0d, 1.0d);
  }

  @Test
  void pgVectorRankingIsUsedWhenQueryAndStoredEmbeddingsExist() throws Exception {
    UUID findingId = createConfirmedFinding("Facade crack", "HIGH");
    float[] vector = new float[1536];
    vector[0] = 1.0f;
    vector[1] = 0.5f;
    UUID bestCaseId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM historical_finding_cases WHERE is_anonymized_benchmark = TRUE"
                + " ORDER BY defect_label LIMIT 1",
            UUID.class);
    jdbcTemplate.update(
        "UPDATE historical_finding_cases SET embedding = CAST(? AS vector) WHERE id = ?",
        HistoricalCaseService.toPgVectorLiteral(vector),
        bestCaseId);
    when(advisorPort.embed(anyString())).thenReturn(Optional.of(vector));

    mockMvc
        .perform(
            get(
                    "/api/v1/inspections/{id}/findings/{fid}/recommendations",
                    fixture.inspectionId(),
                    findingId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.retrievalMode").value("PGVECTOR_RAG"))
        .andExpect(jsonPath("$.data.cases.length()").value(1))
        .andExpect(jsonPath("$.data.cases[0].id").value(bestCaseId.toString()))
        .andExpect(
            jsonPath("$.data.cases[0].similarity").value(org.hamcrest.Matchers.greaterThan(0.5)))
        .andExpect(jsonPath("$.data.cases[0].assetCategory").isString())
        .andExpect(jsonPath("$.data.cases[0].outcome").isString())
        .andExpect(jsonPath("$.data.cases[0].sameAsset").value(false))
        .andExpect(jsonPath("$.data.cases[0].sameOrganization").value(false));
  }

  @Test
  void privateCasesOfAnotherOrganizationAreNeverReturned() throws Exception {
    UUID privateCaseId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO historical_finding_cases
          (id, source_finding_id, source_inspection_id, organization_id, asset_id,
           asset_category_code, asset_label, defect_label, severity, technical_notes,
           report_outcome, is_anonymized_benchmark)
        VALUES (?, ?, ?, ?, ?, 'HIGH_RISE_OFFICE', 'Other tenant asset', 'Facade crack',
                'HIGH', 'Other tenant private notes', 'PUBLISHED', FALSE)
        """,
        privateCaseId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID());

    String body =
        mockMvc
            .perform(
                get(
                        "/api/v1/inspections/{id}/knowledge-recommendations?limit=10",
                        fixture.inspectionId())
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(objectMapper.readTree(body).path("data").path("cases").toString())
        .doesNotContain(privateCaseId.toString());
  }

  @Test
  void publishingIndexesConfirmedFindingsAndExcludesThemFromSelfRecommendations() throws Exception {
    uploadEvidence();
    UUID findingId = createConfirmedFinding("Facade crack", "HIGH");
    confirmFindings();
    UUID versionId = createDraftVersion();
    verifyAndSubmit(versionId);

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vid}/review",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.managerId(), "MANAGER")))
        .andExpect(status().isOk());

    UUID indexedCaseId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM historical_finding_cases WHERE source_finding_id = ?",
            UUID.class,
            findingId);
    assertThat(indexedCaseId).isNotNull();
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "SELECT organization_id FROM historical_finding_cases WHERE id = ?",
            UUID.class,
            indexedCaseId);
    assertThat(organizationId).isEqualTo(fixture.reviewerId());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT is_anonymized_benchmark FROM historical_finding_cases WHERE id = ?",
                Boolean.class,
                indexedCaseId))
        .isFalse();

    String body =
        mockMvc
            .perform(
                get(
                        "/api/v1/inspections/{id}/knowledge-recommendations?limit=10",
                        fixture.inspectionId())
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(objectMapper.readTree(body).path("data").path("cases").toString())
        .doesNotContain(indexedCaseId.toString());
  }

  @Test
  void aiSummaryCarriesKnowledgeReferenceObjects() throws Exception {
    uploadEvidence();
    createConfirmedFinding("Facade crack", "HIGH");
    confirmFindings();
    UUID versionId = createDraftVersion();

    String body =
        mockMvc
            .perform(
                get(
                        "/api/v1/inspections/{id}/report/versions/{vid}/ai-summary",
                        fixture.inspectionId(),
                        versionId)
                    .with(principal(fixture.inspectorId(), "INSPECTOR")))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.data.knowledgeReferences.length()")
                    .value(org.hamcrest.Matchers.greaterThan(0)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode references = objectMapper.readTree(body).path("data").path("knowledgeReferences");
    Set<String> kinds = new java.util.HashSet<>();
    for (JsonNode reference : references) {
      kinds.add(reference.path("kind").asString());
      assertThat(reference.path("codeOrLabel").asString()).isNotBlank();
      assertThat(reference.path("summary").asString()).isNotBlank();
      assertThat(reference.path("similarity").asDouble()).isBetween(0.0d, 1.0d);
    }
    assertThat(kinds).contains("GUIDELINE", "CASE");
  }

  @Test
  void crossOrganizationAndUnassignedActorsAreDenied() throws Exception {
    for (RequestPostProcessor denied :
        List.of(
            principal(fixture.outsiderId(), "CUSTOMER"),
            principal(fixture.otherInspectorId(), "INSPECTOR"))) {
      mockMvc
          .perform(
              get("/api/v1/inspections/{id}/knowledge-recommendations", fixture.inspectionId())
                  .with(denied))
          .andExpect(status().isNotFound());
    }
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

  private UUID createConfirmedFinding(String label, String severity) {
    UUID findingId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO findings (id, inspection_id, finding_code, defect_label, severity,
                              technical_notes, status, created_at)
        VALUES (?, ?, ?, ?, ?, ?, 'CONFIRMED', CURRENT_TIMESTAMP)
        """,
        findingId,
        fixture.inspectionId(),
        "F-" + findingId.toString().substring(0, 6),
        label,
        severity,
        "Visible during the field session");
    return findingId;
  }

  private void confirmFindings() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/findings/confirm", fixture.inspectionId())
                .with(principal(fixture.inspectorId(), "INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noFindingsAcknowledged\":true}"))
        .andExpect(status().isOk());
  }

  private UUID createDraftVersion() throws Exception {
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

  private void verifyAndSubmit(UUID versionId) throws Exception {
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vid}/verify",
                    fixture.inspectionId(),
                    versionId)
                .with(principal(fixture.inspectorId(), "INSPECTOR")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{id}/report/versions/{vid}/submit",
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

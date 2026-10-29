package com.smartdroneinspection.assets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AssetApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired AssetCategoryRepository categories;
  @Autowired AssetRepository assets;
  @Autowired UserRepository users;
  @Autowired com.smartdroneinspection.assets.repository.DroneRepository drones;

  @Autowired JdbcTemplate jdbcTemplate;

  private AssetTestFixture.Data fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
    var fixtureHelper = new AssetTestFixture(categories, assets, users, jdbcTemplate);
    fixture = fixtureHelper.create();
    fixtureHelper.grantEntitlement(fixture.organizationId());
    fixtureHelper.grantEntitlement(fixture.otherOrganizationId());
  }

  @Test
  void orgAdminCreatesActiveAssetWithoutReviewWorkflow() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"BR-01\",\"name\":\"North bridge\",\"description\":\"Main span\","
                        + "\"categoryId\":\""
                        + fixture.categoryId()
                        + "\",\"locationText\":\"District 1\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));

    String persistedLocation =
        jdbcTemplate.queryForObject(
            "SELECT location->>'label' FROM assets WHERE organization_id = ? AND asset_code = ?",
            String.class,
            fixture.organizationId(),
            "BR-01");
    org.assertj.core.api.Assertions.assertThat(persistedLocation).isEqualTo("District 1");
  }

  @Test
  void duplicateCodeInSameOrganizationConflictsAndOtherOrganizationSeesNothing() throws Exception {
    String body =
        "{\"code\":\"BR-DUP\",\"name\":\"Bridge\",\"categoryId\":\""
            + fixture.categoryId()
            + "\",\"locationText\":\"District 1\"}";
    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
    mockMvc
        .perform(get("/api/v1/assets").with(client(fixture.otherClientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(0));
  }

  @Test
  void searchFiltersAssetsWithinTheAuthenticatedOrganization() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"BR-SOUTH\",\"name\":\"South bridge\",\"categoryId\":\""
                        + fixture.categoryId()
                        + "\",\"locationText\":\"District 3\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/assets?search=south").with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(1))
        .andExpect(jsonPath("$.data.items[0].code").value("BR-SOUTH"));
  }

  @Test
  void crossOrganizationReadAndUpdateAreDenied() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/assets")
                    .with(client(fixture.clientId()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"code\":\"BR-X\",\"name\":\"Bridge\",\"categoryId\":\""
                            + fixture.categoryId()
                            + "\",\"locationText\":\"District 1\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    UUID assetId =
        UUID.fromString(
            com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.id"));
    mockMvc
        .perform(get("/api/v1/assets/{id}", assetId).with(client(fixture.otherClientId())))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            put("/api/v1/assets/{id}", assetId)
                .with(client(fixture.otherClientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hijacked\",\"locationText\":\"Elsewhere\"}"))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            put("/api/v1/assets/{id}", assetId)
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\",\"locationText\":\"District 2\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Renamed"));

    var activeAsset = assets.findById(assetId).orElseThrow();
    activeAsset.retire();
    assets.saveAndFlush(activeAsset);
    mockMvc
        .perform(
            put("/api/v1/assets/{id}", assetId)
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cannot rename\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE"));
  }

  @Test
  void organizationAdminCanCreateAndListAssetsWithinItsOrganization() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(manager(fixture.managerId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"ORG-ADMIN-ASSET\",\"name\":\"Own tenant asset\",\"categoryId\":\""
                        + fixture.categoryId()
                        + "\",\"locationText\":\"X\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/assets").with(manager(fixture.managerId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(1));
  }

  @Test
  void createRejectsUnknownAndInactiveCategories() throws Exception {
    String unknownCategoryBody =
        "{\"code\":\"BR-UNKNOWN\",\"name\":\"Bridge\",\"categoryId\":\""
            + UUID.randomUUID()
            + "\",\"locationText\":\"District 1\"}";
    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(unknownCategoryBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    var inactiveCategory =
        categories.saveAndFlush(
            new com.smartdroneinspection.assets.domain.AssetCategory(
                "inactive-" + UUID.randomUUID(), "Inactive", "Inactive category", false));
    String inactiveCategoryBody =
        "{\"code\":\"BR-INACTIVE\",\"name\":\"Bridge\",\"categoryId\":\""
            + inactiveCategory.getId()
            + "\",\"locationText\":\"District 1\"}";
    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(inactiveCategoryBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void orgAdminCannotCreateAssetWhenSubscriptionIsInactive() throws Exception {
    jdbcTemplate.update(
        "UPDATE subscriptions SET status = 'EXPIRED' WHERE organization_id = ?",
        fixture.organizationId());

    String body =
        "{\"code\":\"BR-NOSUB\",\"name\":\"Bridge\",\"categoryId\":\""
            + fixture.categoryId()
            + "\",\"locationText\":\"District 1\"}";

    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"));
  }

  @Test
  void orgAdminCreatesAssetWithInspectionItems() throws Exception {
    String body =
        "{\"code\":\"BR-ITEMS-01\",\"name\":\"Paired Bridge\",\"description\":\"Main span\","
            + "\"categoryId\":\""
            + fixture.categoryId()
            + "\",\"locationText\":\"District 1\","
            + "\"inspectionItems\":[\"Mố cầu M1\",\"Trụ cầu P1\"]}";

    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.inspectionItems[0]").value("Mố cầu M1"))
        .andExpect(jsonPath("$.data.inspectionItems[1]").value("Trụ cầu P1"));

    var created =
        assets.findByOrganizationIdAndCode(fixture.organizationId(), "BR-ITEMS-01").orElseThrow();
    org.assertj.core.api.Assertions.assertThat(created.getInspectionItems())
        .containsExactly("Mố cầu M1", "Trụ cầu P1");
  }

  @Test
  void orgAdminUpdatesAssetWithInspectionItems() throws Exception {
    String createBody =
        "{\"code\":\"BR-UPDATE-01\",\"name\":\"Bridge\",\"categoryId\":\""
            + fixture.categoryId()
            + "\",\"locationText\":\"District 1\","
            + "\"inspectionItems\":[\"Mố cầu M1\"]}";

    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
        .andExpect(status().isCreated());

    var asset =
        assets.findByOrganizationIdAndCode(fixture.organizationId(), "BR-UPDATE-01").orElseThrow();

    String updateBody =
        "{\"name\":\"Updated Bridge\",\"inspectionItems\":[\"Trụ cầu P1\",\"Gối cầu\"]}";

    mockMvc
        .perform(
            put("/api/v1/assets/" + asset.getId())
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Updated Bridge"))
        .andExpect(jsonPath("$.data.inspectionItems[0]").value("Trụ cầu P1"))
        .andExpect(jsonPath("$.data.inspectionItems[1]").value("Gối cầu"));

    var updated = assets.findById(asset.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(updated.getInspectionItems())
        .containsExactly("Trụ cầu P1", "Gối cầu");
  }

  @Test
  void orgAdminCanRetireAsset() throws Exception {
    var asset =
        assets.saveAndFlush(
            new com.smartdroneinspection.assets.domain.Asset(
                fixture.organizationId(),
                fixture.categoryId(),
                "BR-RETIRE-01",
                "Retire Bridge",
                null,
                "{\"label\":\"District 1\"}",
                null,
                null,
                null,
                fixture.clientId()));

    mockMvc
        .perform(
            post("/api/v1/assets/{assetId}/retire", asset.getId()).with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("RETIRED"));

    var reloaded = assets.findById(asset.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(reloaded.getStatus())
        .isEqualTo(com.smartdroneinspection.assets.domain.enums.AssetStatus.RETIRED);

    // Retiring again should fail with 409 INVALID_STATE
    mockMvc
        .perform(
            post("/api/v1/assets/{assetId}/retire", asset.getId()).with(client(fixture.clientId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE"));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor client(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_CUSTOMER"));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor manager(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_CUSTOMER"));
  }
}

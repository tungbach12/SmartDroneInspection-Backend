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
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
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
  @Autowired InspectionRepository inspections;

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
    var fixtureHelper = new AssetTestFixture(categories, assets, users);
    fixture = fixtureHelper.create();
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
            "SELECT location->>'label' FROM assets WHERE owner_customer_id = ? AND asset_code = ?",
            String.class,
            fixture.clientId(),
            "BR-01");
    org.assertj.core.api.Assertions.assertThat(persistedLocation).isEqualTo("District 1");
  }

  @Test
  void createAssetInNoFlyZone_rejectedWithBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"ASSET-NFZ\",\"name\":\"Airport asset\",\"description\":\"Near runway\","
                        + "\"categoryId\":\""
                        + fixture.categoryId()
                        + "\",\"locationText\":\"Sân bay Tân Sơn Nhất\","
                        + "\"latitude\":10.8188,\"longitude\":106.6519}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NO_FLY_ZONE_RESTRICTION"));
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
                .content(
                    "{\"name\":\"Hijacked\",\"locationText\":\"Elsewhere\","
                        + "\"clearCoordinates\":false,\"clearStructureHeight\":false,"
                        + "\"clearFootprintArea\":false,\"clearDescription\":false}"))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            put("/api/v1/assets/{id}", assetId)
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Renamed\",\"locationText\":\"District 2\","
                        + "\"clearCoordinates\":false,\"clearStructureHeight\":false,"
                        + "\"clearFootprintArea\":false,\"clearDescription\":false}"))
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
                .content(
                    "{\"name\":\"Cannot rename\","
                        + "\"clearCoordinates\":false,\"clearStructureHeight\":false,"
                        + "\"clearFootprintArea\":false,\"clearDescription\":false}"))
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
        assets.findByOwnerCustomerIdAndCode(fixture.clientId(), "BR-ITEMS-01").orElseThrow();
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
        assets.findByOwnerCustomerIdAndCode(fixture.clientId(), "BR-UPDATE-01").orElseThrow();

    String updateBody =
        "{\"name\":\"Updated Bridge\",\"inspectionItems\":[\"Trụ cầu P1\",\"Gối cầu\"],"
            + "\"clearCoordinates\":false,\"clearStructureHeight\":false,"
            + "\"clearFootprintArea\":false,\"clearDescription\":false}";

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
  void exposesCheckpointLockOnceAnInspectionExists() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/assets")
                    .with(client(fixture.clientId()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"code\":\"BR-LOCK-01\",\"name\":\"Locked Bridge\",\"categoryId\":\""
                            + fixture.categoryId()
                            + "\",\"locationText\":\"District 1\","
                            + "\"inspectionItems\":[\"Deck\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.checkpointsLocked").value(false))
            .andReturn();
    UUID assetId =
        UUID.fromString(
            com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.id"));

    inspections.saveAndFlush(
        new Inspection(fixture.clientId(), assetId, Instant.now().plusSeconds(3600)));

    mockMvc
        .perform(get("/api/v1/assets/{id}", assetId).with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.checkpointsLocked").value(true));
    mockMvc
        .perform(get("/api/v1/assets").with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].checkpointsLocked").value(true));
    mockMvc
        .perform(
            put("/api/v1/assets/{id}/inspection-items", assetId)
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inspectionItems\":[\"Deck\",\"Roof\"]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSPECTION_HISTORY_EXISTS"));
  }

  @Test
  void orgAdminCanRetireAsset() throws Exception {
    var asset =
        assets.saveAndFlush(
            ownedAsset(fixture.categoryId(), "BR-RETIRE-01", "Retire Bridge", fixture.clientId()));

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

  @Test
  void paginatesAndFiltersAssetsByStatusAndSearchForCustomerAndManager() throws Exception {
    var first = ownedAsset(fixture.categoryId(), "PAG-01", "Alpha Tower", fixture.clientId());
    first.setInspectionItems(java.util.List.of("Roof"));
    assets.saveAndFlush(first);
    var second = ownedAsset(fixture.categoryId(), "PAG-02", "Beta Tower", fixture.clientId());
    assets.saveAndFlush(second);
    var retired = ownedAsset(fixture.categoryId(), "PAG-03", "Gamma Retired", fixture.clientId());
    retired.retire();
    assets.saveAndFlush(retired);
    var otherOrg =
        ownedAsset(fixture.categoryId(), "PAG-04", "Delta Other Org", fixture.otherClientId());
    assets.saveAndFlush(otherOrg);

    mockMvc
        .perform(
            get("/api/v1/assets")
                .param("page", "1")
                .param("pageSize", "1")
                .param("status", "ACTIVE")
                .with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.pageSize").value(1))
        .andExpect(jsonPath("$.data.totalCount").value(2))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.allCount").value(3))
        .andExpect(jsonPath("$.data.activeCount").value(2))
        .andExpect(jsonPath("$.data.checkpointsConfiguredCount").value(1))
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].code").value("PAG-01"));

    mockMvc
        .perform(
            get("/api/v1/assets")
                .param("page", "1")
                .param("pageSize", "10")
                .param("status", "RETIRED")
                .param("search", "Gamma")
                .with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(1))
        .andExpect(jsonPath("$.data.items[0].code").value("PAG-03"));

    com.smartdroneinspection.users.domain.User platformManager =
        new com.smartdroneinspection.users.domain.User(
            "platform-mgr-" + UUID.randomUUID() + "@example.test",
            "Platform Manager",
            "{noop}password",
            com.smartdroneinspection.users.domain.enums.UserStatus.ACTIVE);
    platformManager.addRole(com.smartdroneinspection.users.domain.enums.UserRole.MANAGER);
    UUID platformManagerId = users.saveAndFlush(platformManager).getId();

    mockMvc
        .perform(
            get("/api/v1/assets")
                .param("page", "1")
                .param("pageSize", "2")
                .param("status", "ACTIVE")
                .with(
                    jwt()
                        .jwt(token -> token.subject(platformManagerId.toString()))
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_MANAGER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.items.length()").value(2));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor client(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_CUSTOMER"));
  }

  private com.smartdroneinspection.assets.domain.Asset ownedAsset(
      UUID categoryId, String code, String name, UUID ownerCustomerId) {
    com.smartdroneinspection.assets.domain.Asset asset =
        new com.smartdroneinspection.assets.domain.Asset(
            ownerCustomerId,
            categoryId,
            code,
            name,
            null,
            "{\"label\":\"District 1\"}",
            null,
            null,
            null,
            ownerCustomerId);
    asset.markCustomerOwned(ownerCustomerId);
    return asset;
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor manager(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_CUSTOMER"));
  }
}

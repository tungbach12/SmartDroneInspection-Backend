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
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
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
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired UserRepository users;
  @Autowired com.smartdroneinspection.assets.repository.DroneRepository drones;

  @Autowired
  com.smartdroneinspection.assets.repository.AssetPairAssignmentRepository assetPairAssignments;

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
    var fixtureHelper = new AssetTestFixture(categories, templates, assets, users, jdbcTemplate);
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
  void orgAdminCreatesAssetWithDefaultInspectorAndDronePair() throws Exception {
    var inspector =
        users.saveAndFlush(
            new com.smartdroneinspection.users.domain.User(
                "inspector-" + UUID.randomUUID() + "@example.test",
                "Inspector Alice",
                null,
                com.smartdroneinspection.users.domain.enums.UserStatus.ACTIVE,
                com.smartdroneinspection.users.domain.enums.ActorZone.CUSTOMER_ORGANIZATION,
                fixture.organizationId()));
    inspector.addRole(com.smartdroneinspection.users.domain.enums.UserRole.INSPECTOR);
    users.saveAndFlush(inspector);

    var drone =
        drones.saveAndFlush(
            new com.smartdroneinspection.assets.domain.Drone(
                fixture.organizationId(), "DJI-M300-" + UUID.randomUUID()));

    String body =
        "{\"code\":\"BR-PAIR-01\",\"name\":\"Paired Bridge\",\"description\":\"Main span\","
            + "\"categoryId\":\""
            + fixture.categoryId()
            + "\",\"locationText\":\"District 1\","
            + "\"defaultInspectorId\":\""
            + inspector.getId()
            + "\",\"defaultDroneId\":\""
            + drone.getId()
            + "\"}";

    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(
            jsonPath("$.data.defaultPair.inspectorUserId").value(inspector.getId().toString()))
        .andExpect(jsonPath("$.data.defaultPair.droneId").value(drone.getId().toString()));

    var activePair =
        assetPairAssignments.findByOrganizationIdAndAssetIdAndStatus(
            fixture.organizationId(),
            assets
                .findByOrganizationIdAndCode(fixture.organizationId(), "BR-PAIR-01")
                .orElseThrow()
                .getId(),
            com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus.ACTIVE);
    org.assertj.core.api.Assertions.assertThat(activePair).isPresent();
    org.assertj.core.api.Assertions.assertThat(activePair.get().getInspectorUserId())
        .isEqualTo(inspector.getId());
    org.assertj.core.api.Assertions.assertThat(activePair.get().getDroneId())
        .isEqualTo(drone.getId());
  }

  @Test
  void cannotAssignInspectorFromAnotherOrganization() throws Exception {
    var foreignInspector =
        users.saveAndFlush(
            new com.smartdroneinspection.users.domain.User(
                "foreign-insp-" + UUID.randomUUID() + "@example.test",
                "Foreign Inspector",
                null,
                com.smartdroneinspection.users.domain.enums.UserStatus.ACTIVE,
                com.smartdroneinspection.users.domain.enums.ActorZone.CUSTOMER_ORGANIZATION,
                fixture.otherOrganizationId()));
    foreignInspector.addRole(com.smartdroneinspection.users.domain.enums.UserRole.INSPECTOR);
    users.saveAndFlush(foreignInspector);

    var drone =
        drones.saveAndFlush(
            new com.smartdroneinspection.assets.domain.Drone(
                fixture.organizationId(), "DJI-M300-" + UUID.randomUUID()));

    String body =
        "{\"code\":\"BR-BAD-INSP\",\"name\":\"Bridge\",\"categoryId\":\""
            + fixture.categoryId()
            + "\",\"locationText\":\"District 1\","
            + "\"defaultInspectorId\":\""
            + foreignInspector.getId()
            + "\",\"defaultDroneId\":\""
            + drone.getId()
            + "\"}";

    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INSPECTOR"));
  }

  @Test
  void cannotAssignDroneFromAnotherOrganization() throws Exception {
    var inspector =
        users.saveAndFlush(
            new com.smartdroneinspection.users.domain.User(
                "inspector-" + UUID.randomUUID() + "@example.test",
                "Inspector Bob",
                null,
                com.smartdroneinspection.users.domain.enums.UserStatus.ACTIVE,
                com.smartdroneinspection.users.domain.enums.ActorZone.CUSTOMER_ORGANIZATION,
                fixture.organizationId()));
    inspector.addRole(com.smartdroneinspection.users.domain.enums.UserRole.INSPECTOR);
    users.saveAndFlush(inspector);

    var foreignDrone =
        drones.saveAndFlush(
            new com.smartdroneinspection.assets.domain.Drone(
                fixture.otherOrganizationId(), "DJI-FOREIGN-" + UUID.randomUUID()));

    String body =
        "{\"code\":\"BR-BAD-DRONE\",\"name\":\"Bridge\",\"categoryId\":\""
            + fixture.categoryId()
            + "\",\"locationText\":\"District 1\","
            + "\"defaultInspectorId\":\""
            + inspector.getId()
            + "\",\"defaultDroneId\":\""
            + foreignDrone.getId()
            + "\"}";

    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_DRONE"));
  }

  @Test
  void cannotAssignRetiredDrone() throws Exception {
    var inspector =
        users.saveAndFlush(
            new com.smartdroneinspection.users.domain.User(
                "inspector-" + UUID.randomUUID() + "@example.test",
                "Inspector Charlie",
                null,
                com.smartdroneinspection.users.domain.enums.UserStatus.ACTIVE,
                com.smartdroneinspection.users.domain.enums.ActorZone.CUSTOMER_ORGANIZATION,
                fixture.organizationId()));
    inspector.addRole(com.smartdroneinspection.users.domain.enums.UserRole.INSPECTOR);
    users.saveAndFlush(inspector);

    var retiredDrone =
        drones.saveAndFlush(
            new com.smartdroneinspection.assets.domain.Drone(
                fixture.organizationId(),
                "DJI-RETIRED-" + UUID.randomUUID(),
                null,
                null,
                null,
                com.smartdroneinspection.assets.domain.enums.DroneServiceability.RETIRED,
                null,
                null,
                "Beyond repair"));

    String body =
        "{\"code\":\"BR-RETIRED-DRONE\",\"name\":\"Bridge\",\"categoryId\":\""
            + fixture.categoryId()
            + "\",\"locationText\":\"District 1\","
            + "\"defaultInspectorId\":\""
            + inspector.getId()
            + "\",\"defaultDroneId\":\""
            + retiredDrone.getId()
            + "\"}";

    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_DRONE"));
  }

  @Test
  void orgAdminCanReassignPairForAsset() throws Exception {
    var inspector1 =
        users.saveAndFlush(
            new com.smartdroneinspection.users.domain.User(
                "inspector-1-" + UUID.randomUUID() + "@example.test",
                "Inspector One",
                null,
                com.smartdroneinspection.users.domain.enums.UserStatus.ACTIVE,
                com.smartdroneinspection.users.domain.enums.ActorZone.CUSTOMER_ORGANIZATION,
                fixture.organizationId()));
    inspector1.addRole(com.smartdroneinspection.users.domain.enums.UserRole.INSPECTOR);
    users.saveAndFlush(inspector1);

    var drone1 =
        drones.saveAndFlush(
            new com.smartdroneinspection.assets.domain.Drone(
                fixture.organizationId(), "DJI-M300-1-" + UUID.randomUUID()));

    String createBody =
        "{\"code\":\"BR-REASSIGN-01\",\"name\":\"Bridge\",\"categoryId\":\""
            + fixture.categoryId()
            + "\",\"locationText\":\"District 1\","
            + "\"defaultInspectorId\":\""
            + inspector1.getId()
            + "\",\"defaultDroneId\":\""
            + drone1.getId()
            + "\"}";

    mockMvc
        .perform(
            post("/api/v1/assets")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
        .andExpect(status().isCreated());

    var asset =
        assets
            .findByOrganizationIdAndCode(fixture.organizationId(), "BR-REASSIGN-01")
            .orElseThrow();

    // Verify getActivePair
    mockMvc
        .perform(get("/api/v1/assets/" + asset.getId() + "/pair").with(client(fixture.clientId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inspectorUserId").value(inspector1.getId().toString()))
        .andExpect(jsonPath("$.data.droneId").value(drone1.getId().toString()));

    // Reassign to Inspector 2 and Drone 2
    var inspector2 =
        users.saveAndFlush(
            new com.smartdroneinspection.users.domain.User(
                "inspector-2-" + UUID.randomUUID() + "@example.test",
                "Inspector Two",
                null,
                com.smartdroneinspection.users.domain.enums.UserStatus.ACTIVE,
                com.smartdroneinspection.users.domain.enums.ActorZone.CUSTOMER_ORGANIZATION,
                fixture.organizationId()));
    inspector2.addRole(com.smartdroneinspection.users.domain.enums.UserRole.INSPECTOR);
    users.saveAndFlush(inspector2);

    var drone2 =
        drones.saveAndFlush(
            new com.smartdroneinspection.assets.domain.Drone(
                fixture.organizationId(), "DJI-M300-2-" + UUID.randomUUID()));

    String reassignBody =
        "{\"inspectorId\":\""
            + inspector2.getId()
            + "\",\"droneId\":\""
            + drone2.getId()
            + "\",\"reason\":\"Inspector 1 on leave\"}";

    mockMvc
        .perform(
            put("/api/v1/assets/" + asset.getId() + "/pair")
                .with(client(fixture.clientId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reassignBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inspectorUserId").value(inspector2.getId().toString()))
        .andExpect(jsonPath("$.data.droneId").value(drone2.getId().toString()));

    // Verify old pair is SUPERSEDED and new pair is ACTIVE
    var pairs = assetPairAssignments.findAll();
    var oldPair =
        pairs.stream()
            .filter(
                p ->
                    p.getAssetId().equals(asset.getId())
                        && p.getInspectorUserId().equals(inspector1.getId()))
            .findFirst()
            .orElseThrow();
    org.assertj.core.api.Assertions.assertThat(oldPair.getStatus())
        .isEqualTo(
            com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus.SUPERSEDED);

    var newPair =
        pairs.stream()
            .filter(
                p ->
                    p.getAssetId().equals(asset.getId())
                        && p.getInspectorUserId().equals(inspector2.getId()))
            .findFirst()
            .orElseThrow();
    org.assertj.core.api.Assertions.assertThat(newPair.getStatus())
        .isEqualTo(com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus.ACTIVE);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor client(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_ORG_ADMIN"));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor manager(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_ORG_ADMIN"));
  }
}

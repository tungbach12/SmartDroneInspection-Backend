package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetPairAssignment;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetPairAssignmentRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AssetPairAdminApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired AssetPairAssignmentRepository pairings;
  @Autowired DroneRepository drones;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;
  private UUID organizationId;
  private UUID otherOrganizationId;
  private UUID orgAdminId;
  private UUID platformAdminId;
  private UUID inspectorId;
  private UUID otherInspectorId;
  private UUID assetId;
  private UUID droneId;
  private UUID otherDroneId;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    AssetTestFixture.Data fixture =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = fixture.organizationId();
    otherOrganizationId = fixture.otherOrganizationId();
    orgAdminId = fixture.clientId();
    platformAdminId = fixture.adminId();

    inspectorId = saveInspector(organizationId, "pair-inspector");
    otherInspectorId = saveInspector(otherOrganizationId, "other-pair-inspector");
    assetId = saveAsset(organizationId, fixture.categoryId(), orgAdminId).getId();
    droneId = saveDrone(organizationId, "PAIR-DRONE").getId();
    otherDroneId = saveDrone(otherOrganizationId, "OTHER-PAIR-DRONE").getId();
    grantEntitlement(organizationId);
  }

  @Test
  void organizationAdminCreatesDraftPairAndListsOnlyOwnHistory() throws Exception {
    String response =
        createDraft(orgAdminId, assetId, inspectorId, droneId, null, "Scheduled from July")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.inspectorUserId").value(inspectorId.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID draftId =
        UUID.fromString(
            tools.jackson.databind.json.JsonMapper.shared()
                .readTree(response)
                .path("data")
                .path("id")
                .asString());

    mockMvc
        .perform(
            get("/api/v1/assets/{assetId}/pair-assignments", assetId)
                .with(principal(orgAdminId, "ORG_MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(draftId.toString()));

    mockMvc
        .perform(
            get("/api/v1/assets/{assetId}/pair-assignments", assetId)
                .with(principal(platformAdminId, "ADMIN")))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsWrongRoleForeignResourcesSuspendedInspectorUnavailableDroneAndBadWindow()
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/assets/{assetId}/pair-assignments", assetId)
                .with(principal(platformAdminId, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    pairRequestBody(
                        inspectorId, droneId, null, "reason", Instant.now().minusSeconds(60))))
        .andExpect(status().isForbidden());

    createDraft(orgAdminId, assetId, otherInspectorId, droneId, null, "foreign inspector")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INSPECTOR_NOT_ASSIGNABLE"));
    createDraft(orgAdminId, assetId, inspectorId, otherDroneId, null, "foreign drone")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DRONE_NOT_FOUND"));
    createDraft(orgAdminId, UUID.randomUUID(), inspectorId, droneId, null, "foreign asset")
        .andExpect(status().isNotFound());

    UUID suspendedInspectorId = saveInspector(organizationId, "suspended-pair-inspector");
    User suspendedUser = users.findById(suspendedInspectorId).orElseThrow();
    suspendedUser.disable(UserStatus.SUSPENDED);
    users.saveAndFlush(suspendedUser);
    createDraft(orgAdminId, assetId, suspendedInspectorId, droneId, null, "suspended inspector")
        .andExpect(status().isBadRequest());

    Drone unavailable = saveDrone(organizationId, "UNAVAILABLE-DRONE");
    unavailable.changeServiceability(DroneServiceability.MAINTENANCE);
    drones.saveAndFlush(unavailable);
    createDraft(orgAdminId, assetId, inspectorId, unavailable.getId(), null, "unavailable drone")
        .andExpect(status().isBadRequest());

    createDraft(
            orgAdminId,
            assetId,
            inspectorId,
            droneId,
            Instant.now().minusSeconds(120),
            "bad window")
        .andExpect(status().isBadRequest());
    String futurePairResponse =
        createDraft(
                orgAdminId,
                assetId,
                inspectorId,
                droneId,
                null,
                "future effective",
                Instant.now().plusSeconds(3600))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID futurePairId =
        UUID.fromString(
            tools.jackson.databind.json.JsonMapper.shared()
                .readTree(futurePairResponse)
                .path("data")
                .path("id")
                .asString());
    mockMvc
        .perform(
            post(
                    "/api/v1/assets/{assetId}/pair-assignments/{pairId}/activate",
                    assetId,
                    futurePairId)
                .with(principal(orgAdminId, "ORG_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Activate future pair\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ASSET_PAIR_NOT_EFFECTIVE"));
    createDraft(orgAdminId, assetId, inspectorId, droneId, null, " ")
        .andExpect(status().isBadRequest());
  }

  @Test
  void activationRequiresReasonAndSupersedesOldDefaultWithoutChangingItsResponse()
      throws Exception {
    String firstResponse =
        createDraft(orgAdminId, assetId, inspectorId, droneId, null, "first assignment")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID firstId =
        UUID.fromString(
            tools.jackson.databind.json.JsonMapper.shared()
                .readTree(firstResponse)
                .path("data")
                .path("id")
                .asString());

    mockMvc
        .perform(
            post("/api/v1/assets/{assetId}/pair-assignments/{pairId}/activate", assetId, firstId)
                .with(principal(orgAdminId, "ORG_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/v1/assets/{assetId}/pair-assignments/{pairId}/activate", assetId, firstId)
                .with(principal(orgAdminId, "ORG_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Approved primary asset default\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));

    AssetPairAssignment first =
        pairings.findByIdAndOrganizationId(firstId, organizationId).orElseThrow();
    first.respond(
        inspectorId,
        com.smartdroneinspection.assets.domain.enums.AssignmentResponse.ACCEPTED,
        null);
    pairings.saveAndFlush(first);

    UUID secondInspectorId = saveInspector(organizationId, "second-pair-inspector");
    String secondResponse =
        createDraft(
                orgAdminId,
                assetId,
                secondInspectorId,
                saveDrone(organizationId, "SECOND-PAIR-DRONE").getId(),
                null,
                "second assignment")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID secondId =
        UUID.fromString(
            tools.jackson.databind.json.JsonMapper.shared()
                .readTree(secondResponse)
                .path("data")
                .path("id")
                .asString());

    mockMvc
        .perform(
            post("/api/v1/assets/{assetId}/pair-assignments/{pairId}/activate", assetId, secondId)
                .with(principal(orgAdminId, "ORG_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Reassign future asset default\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));

    assertThat(
            pairings.findByIdAndOrganizationId(firstId, organizationId).orElseThrow().getStatus())
        .isEqualTo(AssetPairAssignmentStatus.SUPERSEDED);
    assertThat(
            pairings
                .findByIdAndOrganizationId(firstId, organizationId)
                .orElseThrow()
                .getAssignmentResponse())
        .isEqualTo(com.smartdroneinspection.assets.domain.enums.AssignmentResponse.ACCEPTED);
    assertThat(
            pairings.findByIdAndOrganizationId(secondId, organizationId).orElseThrow().getStatus())
        .isEqualTo(AssetPairAssignmentStatus.ACTIVE);
    Integer supersessionAudit =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_events WHERE aggregate_id = ? AND action = 'ASSET_PAIR_SUPERSEDED' AND actor_user_id = ?",
            Integer.class,
            firstId,
            orgAdminId);
    assertThat(supersessionAudit).isEqualTo(1);
  }

  @Test
  void unentitledOrganizationCannotCreateOrActivatePairDefaults() throws Exception {
    UUID unentitledOrgId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status) VALUES (?, 'Unentitled', 'Unentitled', ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')",
        unentitledOrgId,
        "ORG-" + unentitledOrgId);
    UUID unentitledAdmin = saveOrganizationAdmin(unentitledOrgId);
    UUID unentitledInspector = saveInspector(unentitledOrgId, "unentitled-inspector");
    UUID unentitledAsset =
        saveAsset(unentitledOrgId, categories.findAll().getFirst().getId(), unentitledAdmin)
            .getId();
    UUID unentitledDrone = saveDrone(unentitledOrgId, "UNENTITLED-DRONE").getId();

    createDraft(
            unentitledAdmin,
            unentitledAsset,
            unentitledInspector,
            unentitledDrone,
            null,
            "unentitled")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"));
  }

  private org.springframework.test.web.servlet.ResultActions createDraft(
      UUID actorId,
      UUID requestedAssetId,
      UUID requestedInspectorId,
      UUID requestedDroneId,
      Instant validUntil,
      String reason)
      throws Exception {
    return createDraft(
        actorId,
        requestedAssetId,
        requestedInspectorId,
        requestedDroneId,
        validUntil,
        reason,
        Instant.now().minusSeconds(60));
  }

  private org.springframework.test.web.servlet.ResultActions createDraft(
      UUID actorId,
      UUID requestedAssetId,
      UUID requestedInspectorId,
      UUID requestedDroneId,
      Instant validUntil,
      String reason,
      Instant validFrom)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/assets/{assetId}/pair-assignments", requestedAssetId)
            .with(principal(actorId, "ORG_MANAGER"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                pairRequestBody(
                    requestedInspectorId, requestedDroneId, validUntil, reason, validFrom)));
  }

  private static String pairRequestBody(
      UUID requestedInspectorId,
      UUID requestedDroneId,
      Instant validUntil,
      String reason,
      Instant validFrom) {
    String until = validUntil == null ? "null" : "\"" + validUntil + "\"";
    String quotedReason = reason == null ? "null" : "\"" + reason.replace("\"", "\\\"") + "\"";
    return """
        {
          "inspectorUserId": "%s",
          "droneId": "%s",
          "validFrom": "%s",
          "validUntil": %s,
          "reason": %s
        }
        """
        .formatted(requestedInspectorId, requestedDroneId, validFrom, until, quotedReason);
  }

  private UUID saveOrganizationAdmin(UUID orgId) {
    User admin =
        new User(
            "unentitled-admin-" + UUID.randomUUID() + "@example.test",
            "Unentitled Admin",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            orgId);
    admin.addRole(UserRole.ORG_MANAGER);
    return users.saveAndFlush(admin).getId();
  }

  private UUID saveInspector(UUID orgId, String prefix) {
    User inspector =
        new User(
            prefix + "-" + UUID.randomUUID() + "@example.test",
            prefix,
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            orgId);
    inspector.addRole(UserRole.INSPECTOR);
    return users.saveAndFlush(inspector).getId();
  }

  private Asset saveAsset(UUID orgId, UUID categoryId, UUID ownerId) {
    return assets.saveAndFlush(
        new Asset(
            orgId,
            categoryId,
            "PAIR-AST-" + UUID.randomUUID(),
            "Pair asset",
            null,
            null,
            null,
            null,
            null,
            ownerId));
  }

  private Drone saveDrone(UUID orgId, String prefix) {
    return drones.saveAndFlush(new Drone(orgId, prefix + "-" + UUID.randomUUID()));
  }

  private void grantEntitlement(UUID orgId) {
    jdbcTemplate.update(
        """
        INSERT INTO subscriptions (organization_id, plan_code, billing_period_months, status, starts_at)
        VALUES (?, 'ENTERPRISE', 1, 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '1 hour')
        """,
        orgId);
  }

  private static RequestPostProcessor principal(UUID id, String role) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
  }
}

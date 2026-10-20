package com.smartdroneinspection.inspections;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.AssetPairAssignment;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetPairAssignmentRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionCreationApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired InspectionRepository inspections;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired AssetPairAssignmentRepository assetPairAssignments;
  @Autowired DroneRepository drones;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;
  private UUID organizationId;
  private UUID otherOrganizationId;
  private UUID adminId;
  private UUID inspectorId;
  private UUID droneId;
  private UUID assetId;
  private UUID pairAssignmentId;

  @BeforeEach
  void setUp() {
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();

    organizationId = UUID.randomUUID();
    otherOrganizationId = UUID.randomUUID();

    jdbcTemplate.update(
        "INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status) VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')",
        organizationId,
        "Org " + organizationId,
        "Org " + organizationId,
        "ORG-" + organizationId);

    jdbcTemplate.update(
        "INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status) VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')",
        otherOrganizationId,
        "Other " + otherOrganizationId,
        "Other " + otherOrganizationId,
        "ORG-" + otherOrganizationId);

    User admin =
        new User(
            "admin-" + UUID.randomUUID() + "@example.test",
            "Org Admin",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    admin.addRole(UserRole.ORG_ADMIN);
    adminId = users.saveAndFlush(admin).getId();

    User inspector =
        new User(
            "inspector-" + UUID.randomUUID() + "@example.test",
            "Inspector One",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    inspector.addRole(UserRole.INSPECTOR);
    inspectorId = users.saveAndFlush(inspector).getId();

    Drone drone = drones.saveAndFlush(new Drone(organizationId, "DJI-M300-" + UUID.randomUUID()));
    droneId = drone.getId();

    AssetCategory category =
        categories.saveAndFlush(
            new AssetCategory(
                "cat-" + UUID.randomUUID(), "Bridge", "Bridge inspection category", true));

    Asset asset =
        assets.saveAndFlush(
            new Asset(
                organizationId,
                category.getId(),
                "BR-MF1-" + UUID.randomUUID(),
                "Main River Bridge",
                "Steel truss bridge",
                "{\"label\":\"District 1\"}",
                null,
                null,
                null,
                adminId));
    assetId = asset.getId();

    AssetPairAssignment pairing =
        new AssetPairAssignment(
            organizationId,
            assetId,
            inspectorId,
            droneId,
            Instant.now().minusSeconds(3600),
            adminId);
    pairing.activate("Operational default", null);
    pairAssignmentId = assetPairAssignments.saveAndFlush(pairing).getId();
  }

  @Test
  void orgAdminCreatesInspectionInheritingAssetPair() throws Exception {
    Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Instant end = start.plus(4, ChronoUnit.HOURS);

    String body =
        "{\"assetId\":\""
            + assetId
            + "\",\"objective\":\"Periodic visual crack survey\","
            + "\"scope\":\"{\\\"method\\\":\\\"visual\\\"}\","
            + "\"componentScope\":\"[\\\"main span\\\", \\\"pier 1\\\"]\","
            + "\"acceptanceCriteria\":\"{\\\"minResolution\\\":\\\"0.5mm\\\"}\","
            + "\"plannedStartAt\":\""
            + start
            + "\",\"plannedEndAt\":\""
            + end
            + "\"}";

    mockMvc
        .perform(
            post("/api/v1/inspections")
                .with(jwtUser(adminId, "ROLE_ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
        .andExpect(jsonPath("$.data.assetId").value(assetId.toString()))
        .andExpect(jsonPath("$.data.inspectorId").value(inspectorId.toString()))
        .andExpect(jsonPath("$.data.droneId").value(droneId.toString()))
        .andExpect(jsonPath("$.data.assetPairAssignmentId").value(pairAssignmentId.toString()))
        .andExpect(jsonPath("$.data.objective").value("Periodic visual crack survey"));
  }

  @Test
  void orgAdminCreatesInspectionWithReassignedPair() throws Exception {
    User inspector2 =
        new User(
            "inspector-2-" + UUID.randomUUID() + "@example.test",
            "Inspector Two",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    inspector2.addRole(UserRole.INSPECTOR);
    UUID inspector2Id = users.saveAndFlush(inspector2).getId();

    Drone drone2 = drones.saveAndFlush(new Drone(organizationId, "DJI-M350-" + UUID.randomUUID()));
    UUID drone2Id = drone2.getId();

    Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Instant end = start.plus(3, ChronoUnit.HOURS);

    String body =
        "{\"assetId\":\""
            + assetId
            + "\",\"objective\":\"Thermal inspection\","
            + "\"inspectorId\":\""
            + inspector2Id
            + "\",\"droneId\":\""
            + drone2Id
            + "\",\"reassignmentReason\":\"Requires thermal sensor payload\","
            + "\"plannedStartAt\":\""
            + start
            + "\",\"plannedEndAt\":\""
            + end
            + "\"}";

    mockMvc
        .perform(
            post("/api/v1/inspections")
                .with(jwtUser(adminId, "ROLE_ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
        .andExpect(jsonPath("$.data.inspectorId").value(inspector2Id.toString()))
        .andExpect(jsonPath("$.data.droneId").value(drone2Id.toString()));
  }

  @Test
  void cannotCreateInspectionForAssetWithoutPair() throws Exception {
    Asset assetWithoutPair =
        assets.saveAndFlush(
            new Asset(
                organizationId,
                categories.findAll().getFirst().getId(),
                "BR-NOPAIR-" + UUID.randomUUID(),
                "Unpaired Bridge",
                null,
                "{\"label\":\"District 2\"}",
                null,
                null,
                null,
                adminId));

    String body =
        "{\"assetId\":\"" + assetWithoutPair.getId() + "\",\"objective\":\"Visual check\"}";

    mockMvc
        .perform(
            post("/api/v1/inspections")
                .with(jwtUser(adminId, "ROLE_ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PAIR_REQUIRED"));
  }

  @Test
  void cannotCreateInspectionForForeignAsset() throws Exception {
    Asset foreignAsset =
        assets.saveAndFlush(
            new Asset(
                otherOrganizationId,
                categories.findAll().getFirst().getId(),
                "BR-FOREIGN-" + UUID.randomUUID(),
                "Foreign Bridge",
                null,
                "{\"label\":\"District 9\"}",
                null,
                null,
                null,
                UUID.randomUUID()));

    String body = "{\"assetId\":\"" + foreignAsset.getId() + "\",\"objective\":\"Visual check\"}";

    mockMvc
        .perform(
            post("/api/v1/inspections")
                .with(jwtUser(adminId, "ROLE_ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void cannotCreateInspectionWithInvalidDates() throws Exception {
    Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
    Instant end = start.minus(1, ChronoUnit.HOURS);

    String body =
        "{\"assetId\":\""
            + assetId
            + "\",\"objective\":\"Visual check\","
            + "\"plannedStartAt\":\""
            + start
            + "\",\"plannedEndAt\":\""
            + end
            + "\"}";

    mockMvc
        .perform(
            post("/api/v1/inspections")
                .with(jwtUser(adminId, "ROLE_ORG_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_DATES"));
  }

  @Test
  void inspectorCannotCreateInspection() throws Exception {
    String body = "{\"assetId\":\"" + assetId + "\",\"objective\":\"Unauthorized attempt\"}";

    mockMvc
        .perform(
            post("/api/v1/inspections")
                .with(jwtUser(inspectorId, "ROLE_INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor jwtUser(
      UUID userId, String role) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(role));
  }
}

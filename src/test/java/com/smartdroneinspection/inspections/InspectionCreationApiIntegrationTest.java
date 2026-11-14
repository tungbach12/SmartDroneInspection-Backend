package com.smartdroneinspection.inspections;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
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
  @Autowired DroneRepository drones;
  @Autowired UserRepository users;
  @Autowired com.smartdroneinspection.users.repository.InspectorProfileRepository inspectorProfiles;
  @Autowired JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;
  private UUID adminId;
  private UUID foreignCustomerId;
  private UUID inspectorId;
  private UUID droneId;
  private UUID assetId;

  @BeforeEach
  void setUp() {
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();

    User admin =
        new User(
            "admin-" + UUID.randomUUID() + "@example.test",
            "Org Admin",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER);
    admin.addRole(UserRole.CUSTOMER);
    adminId = users.saveAndFlush(admin).getId();

    User inspector =
        new User(
            "inspector-" + UUID.randomUUID() + "@example.test",
            "Inspector One",
            null,
            UserStatus.ACTIVE,
            ActorZone.INSPECTOR);
    inspector.addRole(UserRole.INSPECTOR);
    inspectorId = users.saveAndFlush(inspector).getId();
    jdbcTemplate.update("INSERT INTO inspector_profiles (user_id) VALUES (?)", inspectorId);

    Drone drone = drones.saveAndFlush(new Drone(adminId, "DJI-M300-" + UUID.randomUUID()));
    drone.markCustomerOwned(adminId);
    droneId = drones.saveAndFlush(drone).getId();

    AssetCategory category =
        categories.saveAndFlush(
            new AssetCategory(
                "cat-" + UUID.randomUUID(), "Bridge", "Bridge inspection category", true));

    Asset asset =
        assets.saveAndFlush(
            new Asset(
                adminId,
                category.getId(),
                "BR-MF1-" + UUID.randomUUID(),
                "Main River Bridge",
                "Steel truss bridge",
                "{\"label\":\"District 1\"}",
                null,
                null,
                null,
                List.of("Má»‘ cáº§u M1", "Trá»¥ cáº§u P1"),
                adminId));
    asset.markCustomerOwned(adminId);
    assetId = assets.saveAndFlush(asset).getId();
  }

  @Test
  void orgAdminCreatesInspection() throws Exception {
    Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Instant end = start.plus(4, ChronoUnit.HOURS);

    String body =
        "{\"assetId\":\""
            + assetId
            + "\",\"inspectorId\":\""
            + inspectorId
            + "\",\"droneId\":\""
            + droneId
            + "\",\"plannedStartAt\":\""
            + start
            + "\",\"plannedEndAt\":\""
            + end
            + "\"}";

    mockMvc
        .perform(
            post("/api/v1/inspections")
                .with(jwtUser(adminId, "ROLE_CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.data.assetId").value(assetId.toString()))
        .andExpect(jsonPath("$.data.inspectorId").value(inspectorId.toString()))
        .andExpect(jsonPath("$.data.droneId").value(droneId.toString()))
        .andExpect(jsonPath("$.data.inspectionItems[0]").value("Má»‘ cáº§u M1"));
  }

  @Test
  void cannotCreateInspectionForForeignAsset() throws Exception {
    User foreignCustomer =
        new User(
            "foreign-customer-" + UUID.randomUUID() + "@example.test",
            "Foreign Customer",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER);
    foreignCustomer.addRole(UserRole.CUSTOMER);
    UUID foreignCustomerId = users.saveAndFlush(foreignCustomer).getId();

    Asset foreignAsset =
        assets.saveAndFlush(
            new Asset(
                foreignCustomerId,
                categories.findAll().getFirst().getId(),
                "BR-FOREIGN-" + UUID.randomUUID(),
                "Foreign Bridge",
                null,
                "{\"label\":\"District 9\"}",
                null,
                null,
                null,
                UUID.randomUUID()));
    foreignAsset.markCustomerOwned(foreignCustomerId);
    assets.saveAndFlush(foreignAsset);

    Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
    Instant end = start.plus(2, ChronoUnit.HOURS);
    String body =
        "{\"assetId\":\""
            + foreignAsset.getId()
            + "\",\"inspectorId\":\""
            + inspectorId
            + "\",\"droneId\":\""
            + droneId
            + "\",\"plannedStartAt\":\""
            + start
            + "\",\"plannedEndAt\":\""
            + end
            + "\"}";

    mockMvc
        .perform(
            post("/api/v1/inspections")
                .with(jwtUser(adminId, "ROLE_CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void cannotCreateInspectionWithScheduleConflictForSameInspectorOrDrone() throws Exception {
    Instant start = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Instant end = start.plus(4, ChronoUnit.HOURS);

    String firstBody =
        "{\"assetId\":\""
            + assetId
            + "\",\"inspectorId\":\""
            + inspectorId
            + "\",\"droneId\":\""
            + droneId
            + "\",\"plannedStartAt\":\""
            + start
            + "\",\"plannedEndAt\":\""
            + end
            + "\"}";

    mockMvc
        .perform(
            post("/api/v1/inspections")
                .with(jwtUser(adminId, "ROLE_CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstBody))
        .andExpect(status().isCreated());

    Asset secondAsset =
        assets.saveAndFlush(
            new Asset(
                adminId,
                categories.findAll().getFirst().getId(),
                "BR-SECOND-" + UUID.randomUUID(),
                "Second Bridge",
                null,
                "{\"label\":\"District 2\"}",
                null,
                null,
                null,
                adminId));
    secondAsset.markCustomerOwned(adminId);
    assets.saveAndFlush(secondAsset);

    Instant overlappingStart = start.plus(1, ChronoUnit.HOURS);
    Instant overlappingEnd = end.plus(1, ChronoUnit.HOURS);

    String conflictingBody =
        "{\"assetId\":\""
            + secondAsset.getId()
            + "\",\"inspectorId\":\""
            + inspectorId
            + "\",\"droneId\":\""
            + droneId
            + "\",\"plannedStartAt\":\""
            + overlappingStart
            + "\",\"plannedEndAt\":\""
            + overlappingEnd
            + "\"}";

    mockMvc
        .perform(
            post("/api/v1/inspections")
                .with(jwtUser(adminId, "ROLE_CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(conflictingBody))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"));
  }

  @Test
  void uploadFlightPermitTransitionsInspectionToReadyForFlight() throws Exception {
    Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Instant end = start.plus(4, ChronoUnit.HOURS);

    // Customer-owned inspector so the owner-scoped flight-permit lookup matches the inspection.
    User customerInspector =
        new User(
            "customer-inspector-" + UUID.randomUUID() + "@example.test",
            "Customer Inspector",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER);
    customerInspector.addRole(UserRole.INSPECTOR);
    UUID customerInspectorId = users.saveAndFlush(customerInspector).getId();
    jdbcTemplate.update("INSERT INTO inspector_profiles (user_id) VALUES (?)", customerInspectorId);

    String body =
        "{\"assetId\":\""
            + assetId
            + "\",\"inspectorId\":\""
            + customerInspectorId
            + "\",\"droneId\":\""
            + droneId
            + "\",\"plannedStartAt\":\""
            + start
            + "\",\"plannedEndAt\":\""
            + end
            + "\"}";

    var result =
        mockMvc
            .perform(
                post("/api/v1/inspections")
                    .with(jwtUser(adminId, "ROLE_CUSTOMER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    String responseJson = result.getResponse().getContentAsString();
    String inspectionIdStr = com.jayway.jsonpath.JsonPath.read(responseJson, "$.data.id");
    UUID inspectionId = UUID.fromString(inspectionIdStr);

    // The owner-scoped inspector lookup matches partner-assigned inspections, so mark this one as
    // a partner assignment for the flight-permit path. Mutate the managed entity (not raw SQL) so
    // the shared persistence context sees the flag.
    Inspection managed = inspections.findById(inspectionId).orElseThrow();
    managed.markPartnerAssignment();
    inspections.saveAndFlush(managed);
    MockMultipartFile permitFile =
        new MockMultipartFile("file", "permit.pdf", "application/pdf", "%PDF-sample".getBytes());

    mockMvc
        .perform(
            MockMvcRequestBuilders.multipart(
                    "/api/v1/inspections/" + inspectionId + "/flight-permit")
                .file(permitFile)
                .with(jwtUser(customerInspectorId, "ROLE_INSPECTOR")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PILOT_LICENSE_REQUIRED"));

    // Inspector self-declares pilot license
    com.smartdroneinspection.users.domain.InspectorProfile profile =
        inspectorProfiles.findById(customerInspectorId).orElseThrow();
    profile.updatePilotLicense(
        "VN-UAV-12345", "CLASS_A", Instant.now().plus(365, ChronoUnit.DAYS), "license/key.pdf");
    inspectorProfiles.saveAndFlush(profile);

    // Inspector uploads flight permit -> status remains SCHEDULED with permit attached
    mockMvc
        .perform(
            MockMvcRequestBuilders.multipart(
                    "/api/v1/inspections/" + inspectionId + "/flight-permit")
                .file(permitFile)
                .with(jwtUser(customerInspectorId, "ROLE_INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.data.flightPermitFileName").value("permit.pdf"));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor jwtUser(
      UUID userId, String role) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(role));
  }
}

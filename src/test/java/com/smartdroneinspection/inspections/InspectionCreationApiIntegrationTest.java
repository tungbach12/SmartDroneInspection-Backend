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
  @Autowired JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;
  private UUID organizationId;
  private UUID otherOrganizationId;
  private UUID adminId;
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

    jdbcTemplate.update(
        "INSERT INTO subscriptions (organization_id, plan_code, billing_period_months, status, starts_at) VALUES (?, 'ENTERPRISE', 1, 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '1 hour')",
        organizationId);
    jdbcTemplate.update(
        "INSERT INTO subscriptions (organization_id, plan_code, billing_period_months, status, starts_at) VALUES (?, 'ENTERPRISE', 1, 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '1 hour')",
        otherOrganizationId);

    User admin =
        new User(
            "admin-" + UUID.randomUUID() + "@example.test",
            "Org Admin",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    admin.addRole(UserRole.ORG_MANAGER);
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
                List.of("Mố cầu M1", "Trụ cầu P1"),
                adminId));
    assetId = asset.getId();
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
        .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
        .andExpect(jsonPath("$.data.assetId").value(assetId.toString()))
        .andExpect(jsonPath("$.data.inspectorId").value(inspectorId.toString()))
        .andExpect(jsonPath("$.data.droneId").value(droneId.toString()))
        .andExpect(jsonPath("$.data.inspectionItems[0]").value("Mố cầu M1"));
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
                organizationId,
                categories.findAll().getFirst().getId(),
                "BR-SECOND-" + UUID.randomUUID(),
                "Second Bridge",
                null,
                "{\"label\":\"District 2\"}",
                null,
                null,
                null,
                adminId));

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

    // Attempt flight permit upload before pilot license declared -> fails with
    // PILOT_LICENSE_REQUIRED
    MockMultipartFile permitFile =
        new MockMultipartFile("file", "permit.pdf", "application/pdf", "%PDF-sample".getBytes());

    mockMvc
        .perform(
            MockMvcRequestBuilders.multipart(
                    "/api/v1/inspections/" + inspectionId + "/flight-permit")
                .file(permitFile)
                .with(jwtUser(inspectorId, "ROLE_INSPECTOR")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PILOT_LICENSE_REQUIRED"));

    // Inspector self-declares pilot license
    User inspector = users.findById(inspectorId).orElseThrow();
    inspector.updatePilotLicense(
        "VN-UAV-12345", "CLASS_A", Instant.now().plus(365, ChronoUnit.DAYS), "license/key.pdf");
    users.saveAndFlush(inspector);

    // Inspector uploads flight permit -> transitions to READY_FOR_FLIGHT
    mockMvc
        .perform(
            MockMvcRequestBuilders.multipart(
                    "/api/v1/inspections/" + inspectionId + "/flight-permit")
                .file(permitFile)
                .with(jwtUser(inspectorId, "ROLE_INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("READY_FOR_FLIGHT"))
        .andExpect(jsonPath("$.data.flightPermitFileName").value("permit.pdf"));
  }

  @Autowired
  com.smartdroneinspection.inspections.repository.ScheduleOccurrenceRepository occurrences;

  @Autowired com.smartdroneinspection.inspections.service.InspectionReminderJob reminderJob;

  @Autowired
  com.smartdroneinspection.notifications.service.InspectionNotificationService
      inspectionNotifications;

  @Test
  void orgAdminCreatesRecurringScheduleWithPlainTextScopeAndCompatibleSelfServicePermitFlow()
      throws Exception {
    java.time.LocalDateTime anchor =
        java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
            .plusDays(1)
            .truncatedTo(ChronoUnit.SECONDS);

    String scheduleBody =
        "{\"assetId\":\""
            + assetId
            + "\",\"anchor\":\""
            + anchor
            + "\",\"timezone\":\"UTC\","
            + "\"unit\":\"MONTH\","
            + "\"interval\":1,"
            + "\"durationSeconds\":7200,"
            + "\"reminderDays\":3,"
            + "\"inspectorId\":\""
            + inspectorId
            + "\",\"droneId\":\""
            + droneId
            + "\"}";

    var scheduleResult =
        mockMvc
            .perform(
                post("/api/v1/inspection-schedules")
                    .with(jwtUser(adminId, "ROLE_CUSTOMER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(scheduleBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.totalGeneratedOccurrences").value(1))
            .andReturn();

    UUID scheduleId =
        UUID.fromString(
            com.jayway.jsonpath.JsonPath.read(
                scheduleResult.getResponse().getContentAsString(), "$.data.id"));

    var occurrence = occurrences.findByScheduleIdOrderByOccurrenceIndexAsc(scheduleId).getFirst();
    UUID inspectionId = occurrence.getInspectionId();
    var generatedInspection = inspections.findById(inspectionId).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(generatedInspection.getStatus().name())
        .isEqualTo("ASSIGNED");
    org.assertj.core.api.Assertions.assertThat(generatedInspection.getRecurringScheduleId())
        .isEqualTo(scheduleId);

    inspectionNotifications.recordInspectionAssignment(
        inspectionId, organizationId, inspectorId, generatedInspection.getPlannedStartAt());
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/notifications")
                .with(jwtUser(inspectorId, "ROLE_INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].eventType").value("INSPECTION_ASSIGNED"))
        .andExpect(jsonPath("$.data.items[0].targetPath").value("/inspections/" + inspectionId));

    User inspector = users.findById(inspectorId).orElseThrow();
    inspector.updatePilotLicense(
        "VN-UAV-99999", "CLASS_A", Instant.now().plus(365, ChronoUnit.DAYS), "license/rec.pdf");
    users.saveAndFlush(inspector);

    Drone assignedDrone = drones.findById(droneId).orElseThrow();
    assignedDrone.updateRegistration(
        "VN-DRONE-REG-01", "drones/reg-01.pdf", Instant.now().plus(365, ChronoUnit.DAYS));
    drones.saveAndFlush(assignedDrone);

    MockMultipartFile permitFile =
        new MockMultipartFile(
            "file", "recurring-permit.pdf", "application/pdf", "%PDF-1.7".getBytes());
    mockMvc
        .perform(
            MockMvcRequestBuilders.multipart(
                    "/api/v1/inspections/" + inspectionId + "/flight-permit")
                .file(permitFile)
                .with(jwtUser(inspectorId, "ROLE_INSPECTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("READY_FOR_FLIGHT"))
        .andExpect(jsonPath("$.data.inspectionItems[0]").value("Mố cầu M1"));

    org.assertj.core.api.Assertions.assertThat(
            reminderJob.processSingleReminder(occurrence.getId(), Instant.now()))
        .isTrue();

    mockMvc
        .perform(
            post("/api/v1/inspections/" + inspectionId + "/field-sessions")
                .with(jwtUser(inspectorId, "ROLE_INSPECTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preFlightChecklistNote\":\"Battery, GNSS, propellers checked\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor jwtUser(
      UUID userId, String role) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(role));
  }
}

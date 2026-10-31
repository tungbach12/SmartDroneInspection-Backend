package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionManagerDispatchApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired InspectionRepository inspections;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired DroneRepository drones;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;
  private UUID organizationId;
  private UUID managerId;
  private UUID inspectorId;
  private UUID newInspectorId;
  private UUID droneId;
  private UUID assetId;
  private UUID inspectionId;

  @BeforeEach
  void setUp() {
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();

    organizationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, display_name, legal_name, registration_code, timezone, status, created_at, updated_at, row_version)
        VALUES (?, 'Dispatch Org', 'Dispatch Org JSC', 'DISP-ORG', 'UTC', 'ACTIVE', now(), now(), 0)
        """,
        organizationId);

    jdbcTemplate.update(
        "INSERT INTO subscriptions (organization_id, plan_code, billing_period_months, status, starts_at) VALUES (?, 'ENTERPRISE', 1, 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '1 hour')",
        organizationId);

    User manager =
        new User(
            "manager-" + UUID.randomUUID() + "@test.org",
            "Dispatch Manager",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    manager.addRole(UserRole.CUSTOMER);
    managerId = users.saveAndFlush(manager).getId();

    User inspector =
        new User(
            "inspector-" + UUID.randomUUID() + "@test.org",
            "Dispatch Inspector 1",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    inspector.addRole(UserRole.INSPECTOR);
    inspectorId = users.saveAndFlush(inspector).getId();

    User inspector2 =
        new User(
            "inspector2-" + UUID.randomUUID() + "@test.org",
            "Dispatch Inspector 2",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    inspector2.addRole(UserRole.INSPECTOR);
    newInspectorId = users.saveAndFlush(inspector2).getId();

    AssetCategory category =
        categories.saveAndFlush(
            new AssetCategory(
                "cat-" + UUID.randomUUID(), "Bridges", "Bridge inspection category", true));

    assetId =
        assets
            .saveAndFlush(
                new Asset(
                    organizationId,
                    category.getId(),
                    "ASSET-DISP-" + UUID.randomUUID(),
                    "Dispatch Asset",
                    "Bridge",
                    "{\"label\":\"District 1\"}",
                    null,
                    null,
                    null,
                    List.of("Mố cầu M1"),
                    managerId))
            .getId();

    droneId =
        drones.saveAndFlush(new Drone(organizationId, "DISP-DRONE-" + UUID.randomUUID())).getId();

    Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Instant end = start.plus(4, ChronoUnit.HOURS);
    Inspection inspection =
        new Inspection(organizationId, assetId, inspectorId, droneId, start, end);
    inspectionId = inspections.saveAndFlush(inspection).getId();
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor manager() {
    return jwt()
        .jwt(j -> j.subject(managerId.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_CUSTOMER"));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor inspector() {
    return jwt()
        .jwt(j -> j.subject(inspectorId.toString()))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_INSPECTOR"));
  }

  @Test
  void managerCanReschedulePlannedWindow() throws Exception {
    Instant newStart = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Instant newEnd = newStart.plus(5, ChronoUnit.HOURS);

    mockMvc
        .perform(
            put("/api/v1/inspections/{id}/schedule", inspectionId)
                .with(manager())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "plannedStartAt",
                            newStart.toString(),
                            "plannedEndAt",
                            newEnd.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.plannedStartAt").value(newStart.toString()))
        .andExpect(jsonPath("$.data.plannedEndAt").value(newEnd.toString()));

    Inspection updated = inspections.findById(inspectionId).orElseThrow();
    assertThat(updated.getPlannedStartAt()).isEqualTo(newStart);
    assertThat(updated.getPlannedEndAt()).isEqualTo(newEnd);
  }

  @Test
  void managerCanReassignInspector() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/inspections/{id}/assignment", inspectionId)
                .with(manager())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "inspectorId",
                            newInspectorId.toString(),
                            "droneId",
                            droneId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inspectorId").value(newInspectorId.toString()));

    Inspection updated = inspections.findById(inspectionId).orElseThrow();
    assertThat(updated.getInspectorId()).isEqualTo(newInspectorId);
  }

  @Test
  void managerCanCancelInspection() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/cancellation", inspectionId)
                .with(manager())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("reason", "Severe weather persists"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"));

    Inspection updated = inspections.findById(inspectionId).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(InspectionStatus.CANCELLED);
  }

  @Test
  void inspectorCannotRescheduleOrCancel() throws Exception {
    Instant newStart = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Instant newEnd = newStart.plus(5, ChronoUnit.HOURS);

    mockMvc
        .perform(
            put("/api/v1/inspections/{id}/schedule", inspectionId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "plannedStartAt",
                            newStart.toString(),
                            "plannedEndAt",
                            newEnd.toString()))))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/inspections/{id}/cancellation", inspectionId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Inspector cancel"))))
        .andExpect(status().isForbidden());
  }
}

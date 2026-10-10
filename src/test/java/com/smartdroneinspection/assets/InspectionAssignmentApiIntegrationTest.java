package com.smartdroneinspection.assets;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * The MF2-01/02 assignment endpoints over HTTP.
 *
 * <p>This layer is where authorization is actually enforced: the service tests cover scope, but
 * only a request through the filter chain proves that {@code @PreAuthorize} keeps other roles out.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionAssignmentApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired AssetRepository assets;
  @Autowired DroneRepository drones;
  @Autowired AssetPairAssignmentRepository pairings;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  MockMvc mockMvc;
  UUID organizationId;
  UUID inspectorId;
  UUID assignerId;
  UUID assetId;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    AssetTestFixture.Data data =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = data.organizationId();
    assignerId = data.managerId();

    User inspector =
        new User(
            "inspector-" + UUID.randomUUID() + "@example.test",
            "Field Inspector",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    inspector.addRole(UserRole.INSPECTOR);
    inspectorId = users.saveAndFlush(inspector).getId();

    assetId =
        assets
            .saveAndFlush(
                new Asset(
                    organizationId,
                    data.categoryId(),
                    "ASSET-" + UUID.randomUUID(),
                    "Sung Han Bridge",
                    null,
                    null,
                    null,
                    null,
                    null,
                    data.managerId()))
            .getId();
  }

  @Test
  void anInspectorSeesTheirUnansweredAssignmentAndAcceptsIt() throws Exception {
    AssetPairAssignment pairing = activePairing();

    mockMvc
        .perform(get("/api/v1/inspection-assignments/mine").with(inspector(inspectorId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(pairing.getId().toString()));

    mockMvc
        .perform(
            post("/api/v1/inspection-assignments/{id}/response", pairing.getId())
                .with(inspector(inspectorId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"response\":\"ACCEPTED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.assignmentResponse").value("ACCEPTED"))
        .andExpect(jsonPath("$.data.respondedAt").exists());

    mockMvc
        .perform(get("/api/v1/inspection-assignments/mine").with(inspector(inspectorId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void decliningSuspendsTheAssignmentAndKeepsTheReason() throws Exception {
    AssetPairAssignment pairing = activePairing();

    mockMvc
        .perform(
            post("/api/v1/inspection-assignments/{id}/response", pairing.getId())
                .with(inspector(inspectorId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"response\":\"REJECTED\",\"rejectionReason\":\"No night-flight qualification\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.assignmentResponse").value("REJECTED"))
        .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
        .andExpect(jsonPath("$.data.reason").value("No night-flight qualification"));
  }

  @Test
  void decliningWithoutAReasonIsRefused() throws Exception {
    AssetPairAssignment pairing = activePairing();

    mockMvc
        .perform(
            post("/api/v1/inspection-assignments/{id}/response", pairing.getId())
                .with(inspector(inspectorId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"response\":\"REJECTED\",\"rejectionReason\":\"   \"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ASSIGNMENT_RESPONSE_REJECTED"));
  }

  @Test
  void aResponseMissingTheDecisionFieldIsRefused() throws Exception {
    AssetPairAssignment pairing = activePairing();

    mockMvc
        .perform(
            post("/api/v1/inspection-assignments/{id}/response", pairing.getId())
                .with(inspector(inspectorId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void anotherRoleCannotReachTheAssignmentEndpoints() throws Exception {
    AssetPairAssignment pairing = activePairing();
    UUID adminId = UUID.randomUUID();

    mockMvc
        .perform(get("/api/v1/inspection-assignments/mine").with(orgAdmin(adminId)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/inspection-assignments/{id}/response", pairing.getId())
                .with(orgAdmin(adminId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"response\":\"ACCEPTED\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void anUnauthenticatedCallerIsRefused() throws Exception {
    mockMvc
        .perform(get("/api/v1/inspection-assignments/mine"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void anInspectorFromAnotherOrganizationSeesAnEmptyInbox() throws Exception {
    activePairing();
    UUID otherOrganizationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        otherOrganizationId,
        "Other org",
        "Other org",
        "ORG-" + otherOrganizationId);

    User outsider =
        new User(
            "outsider-" + UUID.randomUUID() + "@example.test",
            "Outsider",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            otherOrganizationId);
    outsider.addRole(UserRole.INSPECTOR);
    UUID outsiderId = users.saveAndFlush(outsider).getId();

    mockMvc
        .perform(get("/api/v1/inspection-assignments/mine").with(inspector(outsiderId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  private AssetPairAssignment activePairing() {
    Drone drone = drones.saveAndFlush(new Drone(organizationId, "DJI-M350-" + UUID.randomUUID()));
    AssetPairAssignment pairing =
        new AssetPairAssignment(
            organizationId,
            assetId,
            inspectorId,
            drone.getId(),
            Instant.now().minusSeconds(60),
            assignerId);
    pairing.activate("Primary crew", null);
    return pairings.saveAndFlush(pairing);
  }

  private RequestPostProcessor inspector(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_INSPECTOR"));
  }

  private RequestPostProcessor orgAdmin(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_ORG_ADMIN"));
  }
}

package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
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

/** MF2-09 to MF2-12 field-session HTTP behavior and attribution. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionFieldSessionApiIntegrationTest {

  private static final Instant PLANNED_START = Instant.parse("2026-11-01T09:00:00Z");

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired DroneRepository drones;
  @Autowired JdbcTemplate jdbcTemplate;

  MockMvc mockMvc;
  UUID organizationId;
  UUID inspectorId;
  UUID reviewerId;
  UUID assetId;
  UUID droneId;
  UUID inspectionId;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    AssetTestFixture.Data fixture =
        new AssetTestFixture(categories, assets, users, jdbcTemplate).create();
    organizationId = fixture.organizationId();
    reviewerId = createUser(organizationId, UserRole.ORG_MANAGER, "reviewer");
    inspectorId = createUser(organizationId, UserRole.INSPECTOR, "inspector");

    assetId =
        assets
            .saveAndFlush(
                new Asset(
                    organizationId,
                    fixture.categoryId(),
                    "ASSET-SESSION-API-" + UUID.randomUUID(),
                    "Session API asset",
                    null,
                    "Inspection location",
                    null,
                    null,
                    null,
                    reviewerId))
            .getId();
    droneId =
        drones.saveAndFlush(new Drone(organizationId, "READY-DRONE-" + UUID.randomUUID())).getId();
    inspectionId = createReadyInspection();
  }

  @Test
  void theAssignedInspectorStartsASessionThroughTheApi() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/field-sessions", inspectionId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(Map.of("preFlightChecklistNote", "Drone identified; bridge accessible"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.data.inspectionId").value(inspectionId.toString()))
        .andExpect(jsonPath("$.data.inspectorUserId").doesNotExist())
        .andExpect(jsonPath("$.data.droneId").doesNotExist())
        .andExpect(jsonPath("$.data.startedAt").exists());

    assertThat(statusOfInspection()).isEqualTo("IN_PROGRESS");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT pre_flight_checklist_note FROM field_sessions WHERE inspection_id = ?",
                String.class,
                inspectionId))
        .isEqualTo("Drone identified; bridge accessible");
  }

  @Test
  void theAssignedInspectorListsTheirSessionsForAnInspection() throws Exception {
    startSession();

    mockMvc
        .perform(get("/api/v1/inspections/{i}/field-sessions", inspectionId).with(inspector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value("IN_PROGRESS"))
        .andExpect(
            jsonPath("$.data[0].preFlightChecklistNote")
                .value("Drone identified; bridge accessible"));
  }

  @Test
  void aPostponementRecordsTheReasonThroughTheApi() throws Exception {
    UUID sessionId = startSession();

    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/field-sessions/{s}/postponement", inspectionId, sessionId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", "High wind above 10 m/s"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("POSTPONED"))
        .andExpect(jsonPath("$.data.postponementReason").value("High wind above 10 m/s"));

    assertThat(statusOfInspection()).isEqualTo("READY_FOR_FLIGHT");
  }

  @Test
  void anAbortRecordsItsOwnReasonThroughTheApi() throws Exception {
    UUID sessionId = startSession();

    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/field-sessions/{s}/abort", inspectionId, sessionId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", "Structure found unsafe on site"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ABORTED"))
        .andExpect(jsonPath("$.data.abortReason").value("Structure found unsafe on site"));

    assertThat(statusOfInspection()).isEqualTo("IN_PROGRESS");
  }

  @Test
  void theOrganizationAdminCannotCompleteASession() throws Exception {
    UUID sessionId = startSession();

    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/field-sessions/{s}/completion", inspectionId, sessionId)
                .with(orgAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("outcomeNotes", "Session completed"))))
        .andExpect(status().isForbidden());

    assertThat(sessionStatus(sessionId)).isEqualTo("IN_PROGRESS");
    assertThat(statusOfInspection()).isEqualTo("IN_PROGRESS");
  }

  @Test
  void theAssignedInspectorCompletesTheSessionAndReturnsTheInspectionToMf3() throws Exception {
    UUID sessionId = startSession();

    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/field-sessions/{s}/completion", inspectionId, sessionId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("outcomeNotes", "East face not accessible; photos recorded"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("FIELD_COMPLETED"))
        .andExpect(jsonPath("$.data.endedAt").exists())
        .andExpect(
            jsonPath("$.data.outcomeNotes").value("East face not accessible; photos recorded"));

    assertThat(statusOfInspection()).isEqualTo("FIELD_COMPLETED");
    assertThat(sessionStatus(sessionId)).isEqualTo("FIELD_COMPLETED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM field_sessions WHERE inspection_id = ?",
                Long.class,
                inspectionId))
        .isEqualTo(1L);
  }

  @Test
  void anotherInspectorCannotCompleteThisSession() throws Exception {
    UUID sessionId = startSession();
    UUID otherInspectorId = createUser(organizationId, UserRole.INSPECTOR, "other-completer");

    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/field-sessions/{s}/completion", inspectionId, sessionId)
                .with(inspector(otherInspectorId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("outcomeNotes", "Session completed"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_SCOPE_DENIED"));

    assertThat(sessionStatus(sessionId)).isEqualTo("IN_PROGRESS");
    assertThat(statusOfInspection()).isEqualTo("IN_PROGRESS");
  }

  @Test
  void completionRequiresOutcomeNotesAndLeavesTheSessionOpenOnRefusal() throws Exception {
    UUID sessionId = startSession();

    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/field-sessions/{s}/completion", inspectionId, sessionId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("outcomeNotes", "  "))))
        .andExpect(status().isBadRequest());

    assertThat(sessionStatus(sessionId)).isEqualTo("IN_PROGRESS");
    assertThat(statusOfInspection()).isEqualTo("IN_PROGRESS");
  }

  @Test
  void aBlankPreFlightNoteIsRefused() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/field-sessions", inspectionId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("preFlightChecklistNote", "   "))))
        .andExpect(status().isBadRequest());

    assertThat(sessionCount()).isZero();
  }

  @Test
  void anOrganizationAdminCannotStartOrCloseASession() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/field-sessions", inspectionId)
                .with(orgAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("preFlightChecklistNote", "Paperwork is fine"))))
        .andExpect(status().isForbidden());

    UUID sessionId = startSession();
    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/field-sessions/{s}/postponement", inspectionId, sessionId)
                .with(orgAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", "Wind"))))
        .andExpect(status().isForbidden());

    assertThat(sessionStatus(sessionId)).isEqualTo("IN_PROGRESS");
  }

  @Test
  void anotherInspectorCannotPostponeThisSession() throws Exception {
    UUID sessionId = startSession();
    UUID otherInspectorId = createUser(organizationId, UserRole.INSPECTOR, "other-inspector");

    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/field-sessions/{s}/postponement", inspectionId, sessionId)
                .with(inspector(otherInspectorId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reason", "Wind"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_SCOPE_DENIED"));

    assertThat(sessionStatus(sessionId)).isEqualTo("IN_PROGRESS");
  }

  @Test
  void anInspectorFromAnotherOrganizationSeesNoSession() throws Exception {
    UUID outsiderOrganizationId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status) "
            + "VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')",
        outsiderOrganizationId,
        "Outsider org",
        "Outsider org",
        "ORG-" + outsiderOrganizationId);
    UUID scopedOutsiderId =
        createUser(outsiderOrganizationId, UserRole.INSPECTOR, "scoped-outsider");

    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/field-sessions", inspectionId)
                .with(inspector(scopedOutsiderId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("preFlightChecklistNote", "Ready to fly"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INSPECTION_NOT_FOUND"));

    mockMvc
        .perform(
            get("/api/v1/inspections/{i}/field-sessions", inspectionId)
                .with(inspector(scopedOutsiderId)))
        .andExpect(status().isNotFound());

    assertThat(sessionCount()).isZero();
  }

  @Test
  void anUnauthenticatedCallerIsRefused() throws Exception {
    mockMvc
        .perform(post("/api/v1/inspections/{i}/field-sessions", inspectionId))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/inspections/{i}/field-sessions", inspectionId))
        .andExpect(status().isUnauthorized());
  }

  private UUID startSession() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/inspections/{i}/field-sessions", inspectionId)
                    .with(inspector())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "preFlightChecklistNote", "Drone identified; bridge accessible"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(new ObjectMapper().readTree(body).at("/data/id").asText());
  }

  private static String json(Map<String, Object> fields) throws Exception {
    return new ObjectMapper().writeValueAsString(fields);
  }

  private UUID createUser(UUID organizationId, UserRole role, String label) {
    User user =
        new User(
            label + "-" + UUID.randomUUID() + "@example.test",
            label,
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    user.addRole(role);
    return users.saveAndFlush(user).getId();
  }

  private UUID createReadyInspection() {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO inspections (id, organization_id, asset_id, inspector_id, drone_id, flight_permit_file_key, flight_permit_file_name, flight_permit_uploaded_at, status, planned_start_at, planned_end_at) "
            + "VALUES (?, ?, ?, ?, ?, 'permits/test.pdf', 'test.pdf', CURRENT_TIMESTAMP, 'READY_FOR_FLIGHT', ?, ?)",
        id,
        organizationId,
        assetId,
        inspectorId,
        droneId,
        Timestamp.from(PLANNED_START),
        Timestamp.from(PLANNED_START.plus(2, ChronoUnit.HOURS)));
    return id;
  }

  private long sessionCount() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM field_sessions WHERE inspection_id = ?", Long.class, inspectionId);
  }

  private String sessionStatus(UUID sessionId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM field_sessions WHERE id = ?", String.class, sessionId);
  }

  private String statusOfInspection() {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM inspections WHERE id = ?", String.class, inspectionId);
  }

  private RequestPostProcessor inspector() {
    return inspector(inspectorId);
  }

  private RequestPostProcessor inspector(UUID subject) {
    return jwt()
        .jwt(token -> token.subject(subject.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_INSPECTOR"));
  }

  private RequestPostProcessor orgAdmin() {
    return jwt()
        .jwt(token -> token.subject(reviewerId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
  }
}

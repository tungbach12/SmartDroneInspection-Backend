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
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.domain.InspectionPreparation;
import com.smartdroneinspection.inspections.domain.InspectionReadinessDecision;
import com.smartdroneinspection.inspections.domain.enums.ReadinessDecisionType;
import com.smartdroneinspection.inspections.repository.InspectionPreparationRepository;
import com.smartdroneinspection.inspections.repository.InspectionReadinessDecisionRepository;
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

/**
 * The MF2-09 to MF2-11 field-session endpoints over HTTP.
 *
 * <p>{@link InspectionFieldSessionServiceTest} covers the recheck rules. This layer proves what
 * reaches a caller: that only the assigned Inspector starts or closes a session, that an
 * organization administrator cannot do it on their behalf, and that a refusal arrives as a stable
 * Problem Details code rather than a half-started session.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionFieldSessionApiIntegrationTest {

  private static final Instant PLANNED_START = Instant.parse("2026-11-01T09:00:00Z");

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired DroneRepository drones;
  @Autowired InspectionPreparationRepository preparations;
  @Autowired InspectionReadinessDecisionRepository decisions;
  @Autowired JdbcTemplate jdbcTemplate;

  MockMvc mockMvc;
  UUID organizationId;
  UUID inspectorId;
  UUID reviewerId;
  UUID assetId;
  UUID droneId;
  UUID inspectionId;
  UUID checklistTemplateId;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    AssetTestFixture.Data fixture =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = fixture.organizationId();
    checklistTemplateId = fixture.checklistTemplateId();
    reviewerId = createUser(organizationId, UserRole.ORG_ADMIN, "reviewer");
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
    insertApprovedReadinessDecision();
  }

  @Test
  void theAssignedInspectorStartsASessionThroughTheApi() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/field-sessions", inspectionId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "checklistTemplateId",
                            checklistTemplateId.toString(),
                            "preFlightChecklistNote",
                            "Drone identified; bridge accessible"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.data.inspectionId").value(inspectionId.toString()))
        .andExpect(jsonPath("$.data.inspectorUserId").value(inspectorId.toString()))
        .andExpect(jsonPath("$.data.readinessDecisionId").exists())
        .andExpect(jsonPath("$.data.startedAt").exists());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM inspections WHERE id = ?", String.class, inspectionId))
        .isEqualTo("IN_PROGRESS");
  }

  @Test
  void theAssignedInspectorListsTheirSessionsForAnInspection() throws Exception {
    startSession();

    mockMvc
        .perform(get("/api/v1/inspections/{i}/field-sessions", inspectionId).with(inspector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.data[0].readinessDecisionId").exists());
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

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM inspections WHERE id = ?", String.class, inspectionId))
        .isEqualTo("READY_FOR_FLIGHT");
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

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM inspections WHERE id = ?", String.class, inspectionId))
        .isEqualTo("IN_PROGRESS");
  }

  /**
   * The blank pre-flight note is caught by bean validation before the service runs, so this is a
   * 400 rather than the 409 the service would raise for the same condition called directly. The
   * service-level rule is covered in {@link InspectionFieldSessionServiceTest}.
   */
  @Test
  void aBlankPreFlightNoteIsRefused() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/field-sessions", inspectionId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "checklistTemplateId",
                            checklistTemplateId.toString(),
                            "preFlightChecklistNote",
                            "   "))))
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
                .content(
                    json(
                        Map.of(
                            "checklistTemplateId",
                            checklistTemplateId.toString(),
                            "preFlightChecklistNote",
                            "Paperwork is fine"))))
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

  /**
   * Another Inspector in the same organization is refused with 409 {@code SESSION_SCOPE_DENIED}:
   * the session exists and belongs to this tenant, so the refusal is about ownership rather than
   * visibility. 403 is reserved for the wrong role, as the ORG_ADMIN case above shows.
   */
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
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
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
                .content(
                    json(
                        Map.of(
                            "checklistTemplateId",
                            checklistTemplateId.toString(),
                            "preFlightChecklistNote",
                            "Ready to fly"))))
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
                                "checklistTemplateId",
                                checklistTemplateId.toString(),
                                "preFlightChecklistNote",
                                "Drone identified; bridge accessible"))))
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
        """
        INSERT INTO inspections (id, organization_id, asset_id, inspector_id, drone_id, status, objective,
          planned_start_at, planned_end_at)
        VALUES (?, ?, ?, ?, ?, 'READY_FOR_FLIGHT', 'Inspect main span', ?, ?)
        """,
        id,
        organizationId,
        assetId,
        inspectorId,
        droneId,
        Timestamp.from(PLANNED_START),
        Timestamp.from(PLANNED_START.plus(2, ChronoUnit.HOURS)));
    return id;
  }

  private void insertApprovedReadinessDecision() {
    InspectionPreparation preparation = new InspectionPreparation(inspectionId, inspectorId, 1);
    preparation.recordShotList("{\"shots\":[\"span\"]}");
    preparation.recordSafetyObservations("No hazards observed");
    preparation.submit(inspectorId);
    preparation = preparations.saveAndFlush(preparation);
    preparation.markReady();
    preparations.saveAndFlush(preparation);

    decisions.saveAndFlush(
        new InspectionReadinessDecision(
            inspectionId,
            preparation.getId(),
            preparation.getPreparationVersion(),
            ReadinessDecisionType.APPROVED,
            reviewerId,
            null,
            "{\"source_envelope\":{}}",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "a".repeat(64)));
  }

  private long sessionCount() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM field_sessions WHERE inspection_id = ?",
            Long.class,
            inspectionId);
    return count == null ? 0L : count;
  }

  private String sessionStatus(UUID sessionId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM field_sessions WHERE id = ?", String.class, sessionId);
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
        .authorities(new SimpleGrantedAuthority("ROLE_ORG_ADMIN"));
  }
}

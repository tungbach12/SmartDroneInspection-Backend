package com.smartdroneinspection.inspections;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.FlightPermit;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.FlightPermitRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.time.ZoneOffset;
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
 * The MF2-03 to MF2-06 preparation endpoints over HTTP.
 *
 * <p>The service tests cover scope and the refusal rules. This layer proves what actually reaches a
 * caller: that the two roles are kept apart by the filter chain and not only by the service, and
 * that a compliance blocker arrives as a 200 carrying the list rather than as an error status.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionPreparationApiIntegrationTest {

  static final String SHOT_LIST =
      """
      [{"component":"Span P4","modality":"RGB","required":true}]\
      """;

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired FlightPermitRepository permits;
  @Autowired JdbcTemplate jdbcTemplate;

  MockMvc mockMvc;
  UUID organizationId;
  UUID inspectorId;
  UUID adminId;
  UUID assetId;
  UUID inspectionId;
  Instant plannedStart;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    AssetTestFixture.Data data =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = data.organizationId();
    adminId = data.managerId();
    inspectorId = createInspector(organizationId);

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
                    adminId))
            .getId();

    plannedStart = Instant.now().plus(2, ChronoUnit.DAYS);
    inspectionId = createInspection(plannedStart);
  }

  @Test
  void anInspectorDraftsAndSubmitsThroughTheApi() throws Exception {
    UUID draftId =
        draftId(
            json(
                Map.of(
                    "shotList", SHOT_LIST,
                    "evidenceTypes", "[\"RGB\"]",
                    "safetyObservations", "Live 110V near pier 3")));

    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/preparation/{p}/submission", inspectionId, draftId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acknowledgment\":\"Restrictions read\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.data.submittedAt").exists());
  }

  @Test
  void anIncompletePreparationIsRefusedAtSubmission() throws Exception {
    UUID draftId = draftWithoutSafetyObservations();

    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/preparation/{p}/submission", inspectionId, draftId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PREPARATION_INCOMPLETE"));
  }

  @Test
  void anOrganizationAdminCannotDraftOrSubmit() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/inspections/{id}/preparation", inspectionId)
                .with(orgAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("shotList", SHOT_LIST))))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post(
                    "/api/v1/inspections/{i}/preparation/{p}/submission",
                    inspectionId,
                    UUID.randomUUID())
                .with(orgAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void anInspectorCannotRunTheComplianceGate() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/preparation/compliance", inspectionId).with(inspector()))
        .andExpect(status().isForbidden());
  }

  @Test
  void aComplianceBlockerArrivesAsAListRatherThanAnError() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/preparation/compliance", inspectionId).with(orgAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.blockers[0].code").value("PERMIT_MISSING"))
        .andExpect(jsonPath("$.data.linkedPermitIds").isEmpty());
  }

  @Test
  void aGrantedPermitClearsTheGate() throws Exception {
    FlightPermit permit = new FlightPermit(organizationId, assetId, "UAV", null, "CAAC");
    permit.grant(
        "UAV-2026-" + UUID.randomUUID(),
        "CAAC",
        plannedStart.minus(1, ChronoUnit.DAYS),
        plannedStart.plus(1, ChronoUnit.DAYS));
    permit = permits.saveAndFlush(permit);

    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/preparation/compliance", inspectionId).with(orgAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.blockers").isEmpty())
        .andExpect(jsonPath("$.data.linkedPermitIds[0]").value(permit.getId().toString()));
  }

  @Test
  void anUnauthenticatedCallerIsRefused() throws Exception {
    mockMvc
        .perform(put("/api/v1/inspections/{id}/preparation", inspectionId))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(get("/api/v1/inspections/{id}/preparation/compliance", inspectionId))
        .andExpect(status().isUnauthorized());
  }

  /** Drafts a preparation through the API and returns its id. */
  private UUID draftId(String requestBody) throws Exception {
    String body =
        mockMvc
            .perform(
                put("/api/v1/inspections/{id}/preparation", inspectionId)
                    .with(inspector())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    return UUID.fromString(new ObjectMapper().readTree(body).at("/data/id").asText());
  }

  private UUID draftWithoutSafetyObservations() throws Exception {
    return draftId(json(Map.of("shotList", SHOT_LIST)));
  }

  /**
   * Builds a request body with the real serialiser.
   *
   * <p>The shot-list is itself JSON, so escaping it by hand inside a Java string nests two levels
   * of quoting and silently produces malformed JSON. Composing with a map keeps that out of the
   * test.
   */
  private static String json(Map<String, String> fields) throws Exception {
    return new ObjectMapper().writeValueAsString(fields);
  }

  private UUID createInspector(UUID ownerOrganizationId) {
    User inspector =
        new User(
            "inspector-" + UUID.randomUUID() + "@example.test",
            "Field Inspector",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            ownerOrganizationId);
    inspector.addRole(UserRole.INSPECTOR);
    return users.saveAndFlush(inspector).getId();
  }

  private UUID createInspection(Instant plannedStartAt) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspections (id, organization_id, asset_id, inspector_id, status, objective,
                                planned_start_at, planned_end_at)
        VALUES (?, ?, ?, ?, 'ASSIGNED', 'Routine span inspection', ?, ?)
        """,
        id,
        organizationId,
        assetId,
        inspectorId,
        plannedStartAt.atOffset(ZoneOffset.UTC),
        plannedStartAt.plus(2, ChronoUnit.HOURS).atOffset(ZoneOffset.UTC));
    return id;
  }

  private RequestPostProcessor inspector() {
    return jwt()
        .jwt(token -> token.subject(inspectorId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_INSPECTOR"));
  }

  private RequestPostProcessor orgAdmin() {
    return jwt()
        .jwt(token -> token.subject(adminId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_ORG_ADMIN"));
  }
}

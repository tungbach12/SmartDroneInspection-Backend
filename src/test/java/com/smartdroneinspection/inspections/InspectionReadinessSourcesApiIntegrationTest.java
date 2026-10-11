package com.smartdroneinspection.inspections;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * MF2-07 sources: what a reviewer needs in order to name ids in an approval.
 *
 * <p>Approval takes {@code reviewerCredentialId}, {@code inspectorCredentialIds} and {@code
 * droneDocumentIds}. Without this endpoint a client cannot obtain any of them, so the reviewer
 * would have to type UUIDs — which is why these are read endpoints rather than an optional
 * convenience.
 *
 * <p>The scope rules are the point. The caller reads their own credentials and the assigned
 * Inspector's credentials and documents, never another organization's and never another person's.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionReadinessSourcesApiIntegrationTest {

  private static final Instant PLANNED_START = Instant.parse("2026-11-01T09:00:00Z");

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired DroneRepository drones;
  @Autowired JdbcTemplate jdbcTemplate;

  MockMvc mockMvc;
  UUID organizationId;
  UUID otherOrganizationId;
  UUID reviewerId;
  UUID inspectorId;
  UUID assetId;
  UUID droneId;
  UUID inspectionId;
  UUID reviewerCredentialId;
  UUID inspectorCredentialId;
  UUID droneDocumentId;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    AssetTestFixture.Data fixture =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = fixture.organizationId();
    otherOrganizationId = fixture.otherOrganizationId();
    reviewerId = createUser(organizationId, UserRole.ORG_ADMIN, "reviewer");
    inspectorId = createUser(organizationId, UserRole.INSPECTOR, "inspector");

    assetId =
        assets
            .saveAndFlush(
                new Asset(
                    organizationId,
                    fixture.categoryId(),
                    "ASSET-SOURCES-" + UUID.randomUUID(),
                    "Sources asset",
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

    reviewerCredentialId = insertCredential(reviewerId, "PILOT");
    inspectorCredentialId = insertCredential(inspectorId, "PILOT");
    droneDocumentId = insertDroneDocument();
  }

  @Test
  void aReviewerSeesTheirOwnCredentialsToPresentForApproval() throws Exception {
    mockMvc
        .perform(get("/api/v1/workforce/credentials/me").with(orgAdmin(reviewerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(reviewerCredentialId.toString()))
        .andExpect(jsonPath("$.data[0].credentialType").value("PILOT"))
        .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data[0].verifiedAt").exists());
  }

  @Test
  void aReviewerSeesTheAssignedInspectorsCredentialsAndTheDronesDocuments() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/readiness/sources", inspectionId)
                .with(orgAdmin(reviewerId)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.inspectorCredentials[0].id").value(inspectorCredentialId.toString()))
        .andExpect(jsonPath("$.data.droneDocuments[0].id").value(droneDocumentId.toString()))
        .andExpect(
            jsonPath("$.data.droneDocuments[0].documentType").value("AIRWORTHINESS_CERTIFICATE"));
  }

  @Test
  void theSourcesResponseCarriesTheInspectorAndDroneItDescribes() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/readiness/sources", inspectionId)
                .with(orgAdmin(reviewerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inspectorUserId").value(inspectorId.toString()))
        .andExpect(jsonPath("$.data.droneId").value(droneId.toString()));
  }

  @Test
  void aReviewerOfAnotherOrganizationSeesNoSources() throws Exception {
    UUID outsiderId = createUser(otherOrganizationId, UserRole.ORG_ADMIN, "outsider");

    mockMvc
        .perform(
            get("/api/v1/inspections/{id}/readiness/sources", inspectionId)
                .with(orgAdmin(outsiderId)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INSPECTION_NOT_FOUND"));
  }

  @Test
  void anInspectorCannotReadTheReviewSources() throws Exception {
    mockMvc
        .perform(get("/api/v1/inspections/{id}/readiness/sources", inspectionId).with(inspector()))
        .andExpect(status().isForbidden());
  }

  @Test
  void anUnauthenticatedCallerIsRefused() throws Exception {
    mockMvc.perform(get("/api/v1/workforce/credentials/me")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/inspections/{id}/readiness/sources", inspectionId))
        .andExpect(status().isUnauthorized());
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

  private UUID insertCredential(UUID userId, String type) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO workforce_credentials (id, organization_id, user_id, credential_type, issuer,
          credential_reference, issued_at, expires_at, status, verified_by_user_id, verified_at,
          verification_reason)
        VALUES (?, ?, ?, ?, 'Aviation Authority', ?, ?, ?, 'ACTIVE', ?, ?, ?)
        """,
        id,
        organizationId,
        userId,
        type,
        "LIC-" + id.toString().substring(0, 8),
        Timestamp.from(PLANNED_START.minus(30, ChronoUnit.DAYS)),
        Timestamp.from(PLANNED_START.plus(10, ChronoUnit.DAYS)),
        reviewerId,
        Timestamp.from(Instant.now()),
        "Verified source evidence");
    return id;
  }

  private UUID insertDroneDocument() {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO drone_documents (id, drone_id, document_type, issuer, document_reference, valid_from,
          valid_until, status, object_key, checksum_sha256, uploaded_by_user_id, reviewed_by_user_id, reviewed_at)
        VALUES (?, ?, 'AIRWORTHINESS_CERTIFICATE', 'Aviation Authority', ?, ?, ?, 'ACTIVE', ?,
          '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef', ?, ?, ?)
        """,
        id,
        droneId,
        "DOC-" + id.toString().substring(0, 8),
        Timestamp.from(PLANNED_START.minus(30, ChronoUnit.DAYS)),
        Timestamp.from(PLANNED_START.plus(10, ChronoUnit.DAYS)),
        "drone-documents/" + id,
        inspectorId,
        reviewerId,
        Timestamp.from(Instant.now()));
    return id;
  }

  private RequestPostProcessor orgAdmin() {
    return orgAdmin(reviewerId);
  }

  private RequestPostProcessor orgAdmin(UUID subject) {
    return jwt()
        .jwt(token -> token.subject(subject.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_ORG_ADMIN"));
  }

  private RequestPostProcessor inspector() {
    return jwt()
        .jwt(token -> token.subject(inspectorId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_INSPECTOR"));
  }
}

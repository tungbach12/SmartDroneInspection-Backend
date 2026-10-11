package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.FlightPermit;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.assets.repository.FlightPermitRepository;
import com.smartdroneinspection.inspections.domain.InspectionPreparation;
import com.smartdroneinspection.inspections.repository.InspectionPreparationRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.sql.Timestamp;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * The MF2-07 readiness decision over HTTP.
 *
 * <p>{@link InspectionReadinessServiceTest} covers the decision rules against the service. This
 * layer proves what actually reaches a caller: that reviewer identity and organization come from
 * the token rather than from the request body, that an inspector cannot reach the review endpoint
 * at all, and that a refusal arrives as a Problem Details code instead of a silent success.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionReadinessApiIntegrationTest {

  private static final Instant PLANNED_START = Instant.parse("2026-11-01T09:00:00Z");

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired DroneRepository drones;
  @Autowired FlightPermitRepository permits;
  @Autowired InspectionPreparationRepository preparations;
  @Autowired JdbcTemplate jdbcTemplate;

  MockMvc mockMvc;
  UUID organizationId;
  UUID otherOrganizationId;
  UUID inspectorId;
  UUID reviewerId;
  UUID assetId;
  UUID droneId;
  UUID inspectionId;
  UUID preparationId;
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
                    "ASSET-READINESS-API-" + UUID.randomUUID(),
                    "Readiness API asset",
                    null,
                    "Inspection location",
                    null,
                    null,
                    null,
                    reviewerId))
            .getId();
    droneId =
        drones.saveAndFlush(new Drone(organizationId, "READY-DRONE-" + UUID.randomUUID())).getId();
    inspectionId = createInspection();
    preparationId = createSubmittedPreparation();
    UUID pairId = createAcceptedPair();
    jdbcTemplate.update(
        "UPDATE inspections SET asset_pair_assignment_id = ? WHERE id = ?", pairId, inspectionId);

    reviewerCredentialId = insertCredential(reviewerId, true, true);
    inspectorCredentialId = insertCredential(inspectorId, true, true);
    droneDocumentId =
        insertDroneDocument(
            PLANNED_START.minus(30, ChronoUnit.DAYS), PLANNED_START.plus(10, ChronoUnit.DAYS));
    FlightPermit permit =
        new FlightPermit(organizationId, assetId, "UAV", null, "Civil Aviation Authority");
    permit.grant(
        "PERMIT-" + UUID.randomUUID(),
        "Civil Aviation Authority",
        PLANNED_START.minus(1, ChronoUnit.DAYS),
        PLANNED_START.plus(1, ChronoUnit.DAYS));
    permits.saveAndFlush(permit);
  }

  @Test
  void aQualifiedReviewerApprovesThroughTheApi() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/readiness/{p}/approval", inspectionId, preparationId)
                .with(orgAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "reviewerCredentialId",
                            reviewerCredentialId,
                            "inspectorCredentialIds",
                            List.of(inspectorCredentialId.toString()),
                            "droneDocumentIds",
                            List.of(droneDocumentId.toString()),
                            "applicabilityComplete",
                            true,
                            "applicabilityBasisReference",
                            "inspection-scope-policy-2026"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.decision").value("APPROVED"))
        .andExpect(jsonPath("$.data.reviewedByUserId").value(reviewerId.toString()))
        .andExpect(jsonPath("$.data.preparationVersion").value(1));

    assertThat(statusOfInspection()).isEqualTo("READY_FOR_FLIGHT");
  }

  @Test
  void aQualifiedReviewerReturnsWithAReasonThroughTheApi() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/readiness/{p}/return", inspectionId, preparationId)
                .with(orgAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "reviewerCredentialId",
                            reviewerCredentialId,
                            "inspectorCredentialIdsObserved",
                            List.of(inspectorCredentialId.toString()),
                            "droneDocumentIdsObserved",
                            List.of(),
                            "reason",
                            "Airworthiness document is missing"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.decision").value("RETURNED"))
        .andExpect(jsonPath("$.data.reason").value("Airworthiness document is missing"));

    assertThat(statusOfInspection()).isEqualTo("PREPARING");
  }

  @Test
  void aReturnWithoutAReasonIsRefused() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/readiness/{p}/return", inspectionId, preparationId)
                .with(orgAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "reviewerCredentialId",
                            reviewerCredentialId,
                            "inspectorCredentialIdsObserved",
                            List.of(),
                            "droneDocumentIdsObserved",
                            List.of(),
                            "reason",
                            "   "))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RETURN_REASON_REQUIRED"));

    assertThat(decisionCount()).isZero();
  }

  @Test
  void anEmptyApplicabilityAttestationIsRefused() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/readiness/{p}/approval", inspectionId, preparationId)
                .with(orgAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "reviewerCredentialId",
                            reviewerCredentialId,
                            "inspectorCredentialIds",
                            List.of(inspectorCredentialId.toString()),
                            "droneDocumentIds",
                            List.of(droneDocumentId.toString()),
                            "applicabilityComplete",
                            false,
                            "applicabilityBasisReference",
                            "inspection-scope-policy-2026"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPLICABILITY_ATTESTATION_REQUIRED"));

    assertThat(decisionCount()).isZero();
    assertThat(statusOfInspection()).isEqualTo("PREPARING");
  }

  @Test
  void theInspectorCannotReachTheReadinessEndpoints() throws Exception {
    // A well-formed body is required: the body is deserialized before @PreAuthorize runs, so an
    // empty object would fail parsing and never reach the role check this test is about.
    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/readiness/{p}/approval", inspectionId, preparationId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "reviewerCredentialId",
                            reviewerCredentialId,
                            "inspectorCredentialIds",
                            List.of(inspectorCredentialId.toString()),
                            "droneDocumentIds",
                            List.of(droneDocumentId.toString()),
                            "applicabilityComplete",
                            true,
                            "applicabilityBasisReference",
                            "inspection-scope-policy-2026"))))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/readiness/{p}/return", inspectionId, preparationId)
                .with(inspector())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "reviewerCredentialId",
                            reviewerCredentialId,
                            "inspectorCredentialIdsObserved",
                            List.of(),
                            "droneDocumentIdsObserved",
                            List.of(),
                            "reason",
                            "Not my decision to make"))))
        .andExpect(status().isForbidden());

    assertThat(decisionCount()).isZero();
  }

  @Test
  void anUnauthenticatedCallerIsRefused() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/readiness/{p}/approval", inspectionId, preparationId))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aReviewerOfAnotherOrganizationGetsNothing() throws Exception {
    UUID otherReviewerId = createUser(otherOrganizationId, UserRole.ORG_ADMIN, "outsider");

    mockMvc
        .perform(
            post("/api/v1/inspections/{i}/readiness/{p}/approval", inspectionId, preparationId)
                .with(orgAdmin(otherReviewerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "reviewerCredentialId",
                            reviewerCredentialId,
                            "inspectorCredentialIds",
                            List.of(inspectorCredentialId.toString()),
                            "droneDocumentIds",
                            List.of(droneDocumentId.toString()),
                            "applicabilityComplete",
                            true,
                            "applicabilityBasisReference",
                            "inspection-scope-policy-2026"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INSPECTION_NOT_FOUND"));

    assertThat(decisionCount()).isZero();
    assertThat(statusOfInspection()).isEqualTo("PREPARING");
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

  private UUID createInspection() {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspections (id, organization_id, asset_id, inspector_id, drone_id, status, objective,
          scope, component_scope, acceptance_criteria, planned_start_at, planned_end_at)
        VALUES (?, ?, ?, ?, ?, 'PREPARING', 'Inspect main span', '{"zone":"north"}'::jsonb,
          '{"components":["span"]}'::jsonb, '["no crack"]'::jsonb, ?, ?)
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

  private UUID createSubmittedPreparation() {
    InspectionPreparation preparation = new InspectionPreparation(inspectionId, inspectorId, 1);
    preparation.recordShotList("{\"shots\":[\"span\"]}");
    preparation.recordEvidenceTypes("[\"RGB\"]");
    preparation.recordSafetyObservations("No hazards observed");
    preparation.submit(inspectorId);
    return preparations.saveAndFlush(preparation).getId();
  }

  private UUID createAcceptedPair() {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO asset_pair_assignments (id, organization_id, asset_id, inspector_user_id, drone_id,
          valid_from, valid_until, status, assigned_by_user_id, assignment_response, responded_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, 'ACCEPTED', ?)
        """,
        id,
        organizationId,
        assetId,
        inspectorId,
        droneId,
        Timestamp.from(PLANNED_START.minus(30, ChronoUnit.DAYS)),
        Timestamp.from(PLANNED_START.plus(10, ChronoUnit.DAYS)),
        reviewerId,
        Timestamp.from(PLANNED_START.minus(1, ChronoUnit.HOURS)));
    return id;
  }

  private UUID insertCredential(UUID userId, boolean verified, boolean withEvidence) {
    UUID id = UUID.randomUUID();
    UUID evidenceId = withEvidence ? UUID.randomUUID() : null;
    if (evidenceId != null) {
      jdbcTemplate.update(
          """
          INSERT INTO evidence (id, inspection_id, uploaded_by_user_id, evidence_kind, file_name,
            content_type, size_bytes, checksum_sha256, object_key, source, upload_status)
          VALUES (?, ?, ?, 'INSPECTION', 'credential.pdf', 'application/pdf', 100,
            ?, ?, 'WEB_UPLOAD', 'AVAILABLE')
          """,
          evidenceId,
          inspectionId,
          userId,
          evidenceId.toString().replace("-", "").repeat(2),
          "credential-evidence/" + evidenceId);
    }
    jdbcTemplate.update(
        """
        INSERT INTO workforce_credentials (id, organization_id, user_id, credential_type, issuer,
          credential_reference, issued_at, expires_at, status, evidence_id, verified_by_user_id,
          verified_at, verification_reason)
        VALUES (?, ?, ?, 'PILOT', 'Aviation Authority', ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
        """,
        id,
        organizationId,
        userId,
        "LIC-" + id.toString().substring(0, 8),
        Timestamp.from(PLANNED_START.minus(30, ChronoUnit.DAYS)),
        Timestamp.from(PLANNED_START.plus(10, ChronoUnit.DAYS)),
        evidenceId,
        verified ? reviewerId : null,
        verified ? Timestamp.from(Instant.now()) : null,
        verified ? "Verified source evidence" : null);
    return id;
  }

  private UUID insertDroneDocument(Instant from, Instant until) {
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
        Timestamp.from(from),
        Timestamp.from(until),
        "drone-documents/" + id,
        inspectorId,
        reviewerId,
        Timestamp.from(Instant.now()));
    return id;
  }

  private long decisionCount() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM inspection_readiness_decisions WHERE inspection_id = ?",
            Long.class,
            inspectionId);
    return count == null ? 0L : count;
  }

  private String statusOfInspection() {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM inspections WHERE id = ?", String.class, inspectionId);
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

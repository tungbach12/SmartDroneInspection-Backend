package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.FlightPermit;
import com.smartdroneinspection.assets.readiness.DroneDocumentReadinessAccess;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.assets.repository.FlightPermitRepository;
import com.smartdroneinspection.inspections.domain.InspectionPreparation;
import com.smartdroneinspection.inspections.domain.InspectionReadinessDecision;
import com.smartdroneinspection.inspections.domain.enums.InspectionPreparationStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.ReadinessDecisionType;
import com.smartdroneinspection.inspections.repository.InspectionPreparationRepository;
import com.smartdroneinspection.inspections.repository.InspectionReadinessDecisionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.service.ComplianceGateService;
import com.smartdroneinspection.inspections.service.InspectionReadinessService;
import com.smartdroneinspection.inspections.service.ReadinessReturnCommand;
import com.smartdroneinspection.inspections.service.ReadinessReviewCommand;
import com.smartdroneinspection.inspections.service.ReadinessSnapshotFactory;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.workforce.credential.WorkforceCredentialAccess;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionReadinessServiceTest {

  private static final Instant PLANNED_START = Instant.parse("2026-11-01T09:00:00Z");

  @Autowired InspectionRepository inspections;
  @Autowired InspectionPreparationRepository preparations;
  @Autowired InspectionReadinessDecisionRepository decisions;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired DroneRepository drones;
  @Autowired FlightPermitRepository permits;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired UserAccess userAccess;
  @Autowired ComplianceGateService compliance;
  @Autowired WorkforceCredentialAccess workforceCredentials;
  @Autowired DroneDocumentReadinessAccess assetReadiness;
  @Autowired ReadinessSnapshotFactory snapshots;
  @Autowired EntityManager entityManager;

  private UUID organizationId;
  private UUID otherOrganizationId;
  private UUID inspectorId;
  private UUID reviewerId;
  private UUID otherOrgAdminId;
  private UUID assetId;
  private UUID droneId;
  private UUID inspectionId;
  private UUID preparationId;
  private UUID pairId;
  private UUID reviewerCredentialId;
  private UUID inspectorCredentialId;
  private UUID droneDocumentId;
  private InspectionReadinessService service;

  @BeforeEach
  void setUp() {
    AssetTestFixture.Data fixture =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = fixture.organizationId();
    otherOrganizationId = fixture.otherOrganizationId();
    inspectorId = createUser(organizationId, UserRole.INSPECTOR, "inspector", UserStatus.ACTIVE);
    reviewerId = createUser(organizationId, UserRole.ORG_ADMIN, "reviewer", UserStatus.ACTIVE);
    otherOrgAdminId =
        createUser(otherOrganizationId, UserRole.ORG_ADMIN, "other-admin", UserStatus.ACTIVE);
    assetId = createAsset(organizationId, fixture.categoryId(), reviewerId);
    droneId =
        drones.saveAndFlush(new Drone(organizationId, "READY-DRONE-" + UUID.randomUUID())).getId();
    inspectionId =
        createInspection(InspectionStatus.PREPARING, assetId, inspectorId, droneId, PLANNED_START);
    preparationId = createSubmittedPreparation(inspectionId, inspectorId, 1);
    pairId = createAcceptedPair(organizationId, assetId, inspectorId, droneId, reviewerId);
    jdbcTemplate.update(
        "UPDATE inspections SET asset_pair_assignment_id = ? WHERE id = ?", pairId, inspectionId);
    reviewerCredentialId =
        insertCredential(
            reviewerId,
            organizationId,
            UserRole.ORG_ADMIN,
            "ACTIVE",
            true,
            true,
            PLANNED_START.minus(30, ChronoUnit.DAYS),
            PLANNED_START.plus(10, ChronoUnit.DAYS));
    inspectorCredentialId =
        insertCredential(
            inspectorId,
            organizationId,
            UserRole.INSPECTOR,
            "ACTIVE",
            true,
            true,
            PLANNED_START.minus(30, ChronoUnit.DAYS),
            PLANNED_START.plus(10, ChronoUnit.DAYS));
    droneDocumentId =
        insertDroneDocument(
            droneId,
            inspectorId,
            reviewerId,
            PLANNED_START.minus(30, ChronoUnit.DAYS),
            PLANNED_START.plus(10, ChronoUnit.DAYS),
            "ACTIVE");
    FlightPermit permit =
        new FlightPermit(organizationId, assetId, "UAV", null, "Civil Aviation Authority");
    permit.grant(
        "PERMIT-" + UUID.randomUUID(),
        "Civil Aviation Authority",
        PLANNED_START.minus(1, ChronoUnit.DAYS),
        PLANNED_START.plus(1, ChronoUnit.DAYS));
    permits.saveAndFlush(permit);

    service =
        new InspectionReadinessService(
            inspections,
            preparations,
            decisions,
            userAccess,
            compliance,
            workforceCredentials,
            assetReadiness,
            drones,
            snapshots,
            entityManager);
  }

  @Test
  void independentQualifiedOrgAdminApprovesAndTransitionsInspectionAndPreparation() {
    InspectionReadinessDecision decision =
        service.approve(reviewerId, inspectionId, preparationId, validReview());

    assertThat(decision.getDecision()).isEqualTo(ReadinessDecisionType.APPROVED);
    assertThat(decision.getReviewedByUserId()).isEqualTo(reviewerId);
    assertThat(decision.getSourceHash()).matches("[0-9a-f]{64}");
    assertThat(decision.getPermitSnapshot())
        .contains("PERMIT-")
        .contains("\"status\":\"ACTIVE\"")
        .contains("source_envelope")
        .contains("\"scope\":{\"zone\":\"north\"}")
        .contains("\"component_scope\":{\"components\":[\"span\"]}")
        .contains("\"shot_list\":{\"shots\":[\"span\"]}");
    assertThat(statusOfInspection()).isEqualTo(InspectionStatus.READY_FOR_FLIGHT);
    assertThat(preparations.findById(preparationId).orElseThrow().getStatus())
        .isEqualTo(InspectionPreparationStatus.READY);
  }

  @Test
  void assignedInspectorCannotReviewTheirOwnInspectionEvenWithOrgAdminRole() {
    User assignedInspector = users.findById(inspectorId).orElseThrow();
    assignedInspector.addRole(UserRole.ORG_ADMIN);
    users.saveAndFlush(assignedInspector);
    UUID dualRoleCredentialId =
        insertCredential(
            inspectorId,
            organizationId,
            UserRole.ORG_ADMIN,
            "ACTIVE",
            true,
            true,
            PLANNED_START.minus(30, ChronoUnit.DAYS),
            PLANNED_START.plus(10, ChronoUnit.DAYS));
    ReadinessReviewCommand command = reviewWithCredential(dualRoleCredentialId);

    assertThatThrownBy(() -> service.approve(inspectorId, inspectionId, preparationId, command))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("REVIEWER_NOT_INDEPENDENT"));
    assertNoDecisionOrStateChange();
  }

  @Test
  void wrongRoleCannotApprove() {
    UUID wrongRoleId =
        createUser(organizationId, UserRole.INSPECTOR, "wrong-role", UserStatus.ACTIVE);

    assertThatThrownBy(
            () -> service.approve(wrongRoleId, inspectionId, preparationId, validReview()))
        .isInstanceOf(BusinessException.class)
        .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo("FORBIDDEN"));
    assertNoDecisionOrStateChange();
  }

  @Test
  void inactiveReviewerCannotApprove() {
    UUID inactiveReviewer =
        createUser(organizationId, UserRole.ORG_ADMIN, "inactive", UserStatus.DISABLED);

    assertThatThrownBy(
            () -> service.approve(inactiveReviewer, inspectionId, preparationId, validReview()))
        .isInstanceOf(BusinessException.class)
        .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo("FORBIDDEN"));
    assertNoDecisionOrStateChange();
  }

  @Test
  void otherOrganizationReviewerCannotResolveInspection() {
    assertThatThrownBy(
            () -> service.approve(otherOrgAdminId, inspectionId, preparationId, validReview()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("INSPECTION_NOT_FOUND"));
    assertNoDecisionOrStateChange();
  }

  @Test
  void returnIsAllowedWithMissingReadinessEvidenceAndRecordsObservedIdsAsIncomplete() {
    jdbcTemplate.update("DELETE FROM flight_permits WHERE asset_id = ?", assetId);
    ReadinessReturnCommand command =
        new ReadinessReturnCommand(
            reviewerCredentialId,
            List.of(inspectorCredentialId),
            List.of(UUID.randomUUID()),
            "Please attach current authorization evidence");

    InspectionReadinessDecision decision =
        service.returnPreparation(reviewerId, inspectionId, preparationId, command);

    assertThat(decision.getDecision()).isEqualTo(ReadinessDecisionType.RETURNED);
    assertThat(decision.getReason()).contains("authorization");
    assertThat(decision.getPermitSnapshot()).contains("\"observed_source_set_complete\":false");
    assertThat(decision.getPermitSnapshot()).contains(inspectorCredentialId.toString());
    assertThat(statusOfInspection()).isEqualTo(InspectionStatus.PREPARING);
    assertThat(preparations.findById(preparationId).orElseThrow().getStatus())
        .isEqualTo(InspectionPreparationStatus.RETURNED);
  }

  @Test
  void reviewerCredentialMustBeOwnedByReviewer() {
    UUID inspectorOwnedCredential =
        insertCredential(
            inspectorId,
            organizationId,
            UserRole.INSPECTOR,
            "ACTIVE",
            true,
            true,
            PLANNED_START.minus(30, ChronoUnit.DAYS),
            PLANNED_START.plus(10, ChronoUnit.DAYS));

    assertThatThrownBy(
            () ->
                service.approve(
                    reviewerId,
                    inspectionId,
                    preparationId,
                    reviewWithCredential(inspectorOwnedCredential)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("REVIEWER_CREDENTIAL_INVALID"));
    assertNoDecisionOrStateChange();
  }

  @Test
  void permitBlockerCannotBeOverriddenByReviewerAttestation() {
    jdbcTemplate.update("DELETE FROM flight_permits WHERE asset_id = ?", assetId);

    assertThatThrownBy(
            () -> service.approve(reviewerId, inspectionId, preparationId, validReview()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error -> assertThat(((BusinessException) error).code()).isEqualTo("PERMIT_MISSING"));
    assertNoDecisionOrStateChange();
  }

  @Test
  void reviewerCredentialWithNullDatesCannotApprove() {
    UUID credentialId =
        insertCredential(
            reviewerId,
            organizationId,
            UserRole.ORG_ADMIN,
            "ACTIVE",
            true,
            true,
            null,
            PLANNED_START.plus(10, ChronoUnit.DAYS));

    assertThatThrownBy(
            () ->
                service.approve(
                    reviewerId, inspectionId, preparationId, reviewWithCredential(credentialId)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("REVIEWER_CREDENTIAL_INVALID"));
    assertNoDecisionOrStateChange();
  }

  @Test
  void inspectorCredentialWithoutEvidenceAttributionCannotApprove() {
    UUID credentialId =
        insertCredential(
            inspectorId,
            organizationId,
            UserRole.INSPECTOR,
            "ACTIVE",
            true,
            false,
            PLANNED_START.minus(30, ChronoUnit.DAYS),
            PLANNED_START.plus(10, ChronoUnit.DAYS));
    ReadinessReviewCommand command =
        new ReadinessReviewCommand(
            reviewerCredentialId,
            List.of(credentialId),
            List.of(droneDocumentId),
            true,
            "inspection-scope-policy-2026",
            null,
            null,
            null);

    assertThatThrownBy(() -> service.approve(reviewerId, inspectionId, preparationId, command))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("INSPECTOR_CREDENTIAL_INVALID"));
    assertNoDecisionOrStateChange();
  }

  @Test
  void unreviewedDroneDocumentCannotApprove() {
    UUID documentId =
        insertDroneDocument(
            droneId,
            inspectorId,
            null,
            PLANNED_START.minus(30, ChronoUnit.DAYS),
            PLANNED_START.plus(10, ChronoUnit.DAYS),
            "ACTIVE");
    ReadinessReviewCommand command =
        new ReadinessReviewCommand(
            reviewerCredentialId,
            List.of(inspectorCredentialId),
            List.of(documentId),
            true,
            "inspection-scope-policy-2026",
            null,
            null,
            null);

    assertThatThrownBy(() -> service.approve(reviewerId, inspectionId, preparationId, command))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("DRONE_DOCUMENT_INVALID"));
    assertNoDecisionOrStateChange();
  }

  @Test
  void missingApplicabilityBasisCannotApprove() {
    ReadinessReviewCommand command =
        new ReadinessReviewCommand(
            reviewerCredentialId,
            List.of(inspectorCredentialId),
            List.of(droneDocumentId),
            true,
            " ",
            null,
            null,
            null);

    assertThatThrownBy(() -> service.approve(reviewerId, inspectionId, preparationId, command))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("APPLICABILITY_ATTESTATION_REQUIRED"));
    assertNoDecisionOrStateChange();
  }

  @Test
  void emptySelectedDocumentListsRequireCategorySpecificReason() {
    ReadinessReviewCommand command =
        new ReadinessReviewCommand(
            reviewerCredentialId,
            List.of(),
            List.of(),
            true,
            "inspection-scope-policy-2026",
            "No additional Inspector qualification applies",
            null,
            null);

    assertThatThrownBy(() -> service.approve(reviewerId, inspectionId, preparationId, command))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("DRONE_DOCUMENT_BASIS_REQUIRED"));
    assertNoDecisionOrStateChange();
  }

  @Test
  void returnedPreparationRequiresNonblankReason() {
    ReadinessReturnCommand command =
        new ReadinessReturnCommand(reviewerCredentialId, List.of(), List.of(), "  ");

    assertThatThrownBy(
            () -> service.returnPreparation(reviewerId, inspectionId, preparationId, command))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("RETURN_REASON_REQUIRED"));
    assertNoDecisionOrStateChange();
  }

  @Test
  void pairResponseAtPlannedStartCannotApprove() {
    jdbcTemplate.update(
        "UPDATE asset_pair_assignments SET responded_at = ? WHERE id = ?",
        Timestamp.from(PLANNED_START),
        pairId);

    assertThatThrownBy(
            () -> service.approve(reviewerId, inspectionId, preparationId, validReview()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("PAIR_RESPONSE_LATE"));
    assertNoDecisionOrStateChange();
  }

  @Test
  void inspectionOutsidePreparingStateCannotBeReviewed() {
    jdbcTemplate.update(
        "UPDATE inspections SET status = 'READY_FOR_FLIGHT' WHERE id = ?", inspectionId);

    assertThatThrownBy(
            () -> service.approve(reviewerId, inspectionId, preparationId, validReview()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("READINESS_NOT_ALLOWED"));
    assertThat(statusOfInspection()).isEqualTo(InspectionStatus.READY_FOR_FLIGHT);
    assertThat(decisions.findByInspectionIdOrderByDecidedAtDesc(inspectionId)).isEmpty();
  }

  private ReadinessReviewCommand validReview() {
    return reviewWithCredential(reviewerCredentialId);
  }

  private ReadinessReviewCommand reviewWithCredential(UUID credentialId) {
    return new ReadinessReviewCommand(
        credentialId,
        List.of(inspectorCredentialId),
        List.of(droneDocumentId),
        true,
        "inspection-scope-policy-2026",
        null,
        null,
        null);
  }

  private void assertNoDecisionOrStateChange() {
    assertThat(decisions.findByInspectionIdOrderByDecidedAtDesc(inspectionId)).isEmpty();
    assertThat(statusOfInspection()).isEqualTo(InspectionStatus.PREPARING);
    assertThat(preparations.findById(preparationId).orElseThrow().getStatus())
        .isEqualTo(InspectionPreparationStatus.SUBMITTED);
  }

  private UUID createAsset(UUID orgId, UUID categoryId, UUID creatorId) {
    return assets
        .saveAndFlush(
            new Asset(
                orgId,
                categoryId,
                "ASSET-READINESS-" + UUID.randomUUID(),
                "Readiness test asset",
                null,
                "Inspection location",
                null,
                null,
                null,
                creatorId))
        .getId();
  }

  private UUID createUser(UUID orgId, UserRole role, String label, UserStatus status) {
    User user =
        new User(
            label + "-" + UUID.randomUUID() + "@example.test",
            label,
            null,
            status,
            ActorZone.CUSTOMER_ORGANIZATION,
            orgId);
    user.addRole(role);
    return users.saveAndFlush(user).getId();
  }

  private UUID createInspection(
      InspectionStatus status, UUID assetId, UUID inspectorId, UUID droneId, Instant plannedStart) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspections (id, organization_id, asset_id, inspector_id, drone_id, status, objective,
          scope, component_scope, acceptance_criteria, planned_start_at, planned_end_at)
        VALUES (?, ?, ?, ?, ?, ?, 'Inspect main span', '{"zone":"north"}'::jsonb,
          '{"components":["span"]}'::jsonb, '["no crack"]'::jsonb, ?, ?)
        """,
        id,
        organizationId,
        assetId,
        inspectorId,
        droneId,
        status.name(),
        Timestamp.from(plannedStart),
        Timestamp.from(plannedStart.plus(2, ChronoUnit.HOURS)));
    return id;
  }

  private UUID createSubmittedPreparation(
      UUID inspectionId, UUID assignedInspectorId, int version) {
    InspectionPreparation preparation =
        new InspectionPreparation(inspectionId, assignedInspectorId, version);
    preparation.recordShotList("{\"shots\":[\"span\"]}");
    preparation.recordEvidenceTypes("[\"RGB\"]");
    preparation.recordSafetyObservations("No hazards observed");
    preparation.submit(assignedInspectorId);
    return preparations.saveAndFlush(preparation).getId();
  }

  private UUID createAcceptedPair(
      UUID orgId, UUID assetId, UUID assignedInspectorId, UUID droneId, UUID assignerId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO asset_pair_assignments (id, organization_id, asset_id, inspector_user_id, drone_id,
          valid_from, valid_until, status, assigned_by_user_id, assignment_response, responded_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, 'ACCEPTED', ?)
        """,
        id,
        orgId,
        assetId,
        assignedInspectorId,
        droneId,
        Timestamp.from(PLANNED_START.minus(30, ChronoUnit.DAYS)),
        Timestamp.from(PLANNED_START.plus(10, ChronoUnit.DAYS)),
        assignerId,
        Timestamp.from(PLANNED_START.minus(1, ChronoUnit.HOURS)));
    return id;
  }

  private UUID insertCredential(
      UUID userId,
      UUID orgId,
      UserRole verifierRole,
      String status,
      boolean verified,
      boolean evidence,
      Instant issuedAt,
      Instant expiresAt) {
    UUID id = UUID.randomUUID();
    UUID evidenceId = evidence ? UUID.randomUUID() : null;
    if (evidenceId != null) {
      jdbcTemplate.update(
          """
          INSERT INTO evidence (id, inspection_id, uploaded_by_user_id, evidence_kind, file_name, content_type,
            size_bytes, checksum_sha256, object_key, source, upload_status)
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
        VALUES (?, ?, ?, 'PILOT', 'Aviation Authority', ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        orgId,
        userId,
        "LIC-" + id.toString().substring(0, 8),
        sqlTimestamp(issuedAt),
        sqlTimestamp(expiresAt),
        status,
        evidenceId,
        verified ? reviewerId : null,
        verified ? Timestamp.from(Instant.now()) : null,
        verified ? "Verified source evidence" : null);
    return id;
  }

  private UUID insertDroneDocument(
      UUID droneId, UUID uploaderId, UUID reviewer, Instant from, Instant until, String status) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO drone_documents (id, drone_id, document_type, issuer, document_reference, valid_from,
          valid_until, status, object_key, checksum_sha256, uploaded_by_user_id, reviewed_by_user_id, reviewed_at)
        VALUES (?, ?, 'AIRWORTHINESS_CERTIFICATE', 'Aviation Authority', ?, ?, ?, ?, ?,
          '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef', ?, ?, ?)
        """,
        id,
        droneId,
        "DOC-" + id.toString().substring(0, 8),
        Timestamp.from(from),
        Timestamp.from(until),
        status,
        "drone-documents/" + id,
        uploaderId,
        reviewer,
        reviewer == null ? null : Timestamp.from(Instant.now()));
    return id;
  }

  private static Object sqlTimestamp(Instant instant) {
    return instant == null
        ? new SqlParameterValue(Types.TIMESTAMP_WITH_TIMEZONE, null)
        : instant.atOffset(ZoneOffset.UTC);
  }

  private InspectionStatus statusOfInspection() {
    return InspectionStatus.valueOf(
        jdbcTemplate.queryForObject(
            "SELECT status FROM inspections WHERE id = ?", String.class, inspectionId));
  }
}

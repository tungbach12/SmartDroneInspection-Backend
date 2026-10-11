package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.FlightPermit;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.domain.enums.FlightPermitStatus;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.assets.repository.FlightPermitRepository;
import com.smartdroneinspection.inspections.api.dto.request.LinkPermitReferencesRequest;
import com.smartdroneinspection.inspections.api.dto.response.ComplianceGateResponse;
import com.smartdroneinspection.inspections.service.ComplianceGateService;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * MF2-04 and MF2-05 against a real database.
 *
 * <p>These steps decide whether an organization's own paperwork supports a mission. They never
 * decide whether flight is lawful - that belongs to the authority that issues permits - so the gate
 * reports blockers and lets a named reviewer decide. The tests here are mostly about which states
 * refuse, because that list is what an organization relies on when it is told to go and get a
 * permit rather than to fix a form.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ComplianceGateServiceTest {

  @Autowired ComplianceGateService service;
  @Autowired FlightPermitRepository permits;
  @Autowired DroneRepository drones;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired JdbcTemplate jdbcTemplate;

  UUID organizationId;
  UUID adminId;
  UUID assetId;
  UUID inspectionId;
  Instant plannedStart;

  @BeforeEach
  void setUp() {
    AssetTestFixture.Data data =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = data.organizationId();
    adminId = data.managerId();

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
    inspectionId = createInspection("ASSIGNED", plannedStart, null);
  }

  @Test
  void anActivePermitCoveringThePlannedWindowClearsTheGate() {
    FlightPermit permit =
        grantedPermit(
            plannedStart.minus(1, ChronoUnit.DAYS), plannedStart.plus(3, ChronoUnit.DAYS));

    ComplianceGateResponse gate = service.evaluate(adminId, inspectionId);

    assertThat(gate.blockers()).isEmpty();
    assertThat(gate.linkedPermitIds()).containsExactly(permit.getId());
  }

  @Test
  void aPermitThatHasNotBeenGrantedBlocksReadiness() {
    permits.saveAndFlush(new FlightPermit(organizationId, assetId, "UAV", null, "CAAC"));

    ComplianceGateResponse gate = service.evaluate(adminId, inspectionId);

    assertThat(gate.blockers())
        .anySatisfy(blocker -> assertThat(blocker.code()).isEqualTo("PERMIT_NOT_ISSUED"));
  }

  @Test
  void aRejectedPermitBlocksReadiness() {
    FlightPermit permit = grantedPermit(plannedStart, plannedStart.plus(1, ChronoUnit.DAYS));
    permit.refuse(FlightPermitStatus.REJECTED, "Airspace restricted", Instant.now());

    ComplianceGateResponse gate = service.evaluate(adminId, inspectionId);

    assertThat(gate.blockers())
        .anySatisfy(blocker -> assertThat(blocker.code()).isEqualTo("PERMIT_NOT_ISSUED"));
  }

  @Test
  void aRevokedPermitBlocksReadiness() {
    FlightPermit permit = grantedPermit(plannedStart, plannedStart.plus(1, ChronoUnit.DAYS));
    permit.refuse(FlightPermitStatus.REVOKED, "Withdrawn by authority", Instant.now());

    ComplianceGateResponse gate = service.evaluate(adminId, inspectionId);

    assertThat(gate.blockers())
        .anySatisfy(blocker -> assertThat(blocker.code()).isEqualTo("PERMIT_NOT_ISSUED"));
  }

  @Test
  void anExpiredPermitBlocksReadiness() {
    FlightPermit permit = grantedPermit(plannedStart, plannedStart.plus(1, ChronoUnit.DAYS));
    permit.defineValidity(
        plannedStart.minus(10, ChronoUnit.DAYS), plannedStart.minus(1, ChronoUnit.DAYS));

    ComplianceGateResponse gate = service.evaluate(adminId, inspectionId);

    assertThat(gate.blockers())
        .anySatisfy(blocker -> assertThat(blocker.code()).isEqualTo("PERMIT_OUTSIDE_VALIDITY"));
  }

  @Test
  void aPermitThatDoesNotCoverThePlannedWindowBlocksReadiness() {
    grantedPermit(plannedStart.plus(5, ChronoUnit.DAYS), plannedStart.plus(6, ChronoUnit.DAYS));

    ComplianceGateResponse gate = service.evaluate(adminId, inspectionId);

    assertThat(gate.blockers())
        .anySatisfy(blocker -> assertThat(blocker.code()).isEqualTo("PERMIT_OUTSIDE_VALIDITY"));
  }

  @Test
  void anInspectionWithNoPermitAtAllIsBlockedRatherThanAssumedClear() {
    ComplianceGateResponse gate = service.evaluate(adminId, inspectionId);

    assertThat(gate.blockers())
        .anySatisfy(blocker -> assertThat(blocker.code()).isEqualTo("PERMIT_MISSING"));
  }

  @Test
  void aRecordedExemptionClearsTheGateAndKeepsItsLegalBasis() {
    FlightPermit permit = new FlightPermit(organizationId, assetId, "UAV", null, "Not applicable");
    permit.markNotApplicable(
        "Bridge is inside a no-fly-exempt municipal zone per Decision 21/2024");
    permit = permits.saveAndFlush(permit);

    ComplianceGateResponse gate = service.evaluate(adminId, inspectionId);

    assertThat(gate.blockers()).isEmpty();
    assertThat(gate.linkedPermitIds()).containsExactly(permit.getId());
  }

  @Test
  void anExemptionWithoutALegalBasisIsNotAnExemption() {
    FlightPermit permit = new FlightPermit(organizationId, assetId, "UAV", null, "Not applicable");
    permit.markNotApplicable("  ");
    permits.saveAndFlush(permit);

    ComplianceGateResponse gate = service.evaluate(adminId, inspectionId);

    assertThat(gate.blockers())
        .anySatisfy(blocker -> assertThat(blocker.code()).isEqualTo("EXEMPTION_BASIS_MISSING"));
  }

  @Test
  void anInspectionWithNoPlannedSessionBlocksReadiness() {
    UUID unplannedInspection = createInspection("ASSIGNED", null, null);
    grantedPermit(plannedStart, plannedStart.plus(1, ChronoUnit.DAYS));

    ComplianceGateResponse gate = service.evaluate(adminId, unplannedInspection);

    assertThat(gate.blockers())
        .anySatisfy(blocker -> assertThat(blocker.code()).isEqualTo("PLANNED_SESSION_MISSING"));
  }

  @Test
  void aDroneThatIsNotServiceableBlocksReadiness() {
    grantedPermit(plannedStart, plannedStart.plus(1, ChronoUnit.DAYS));
    Drone drone = new Drone(organizationId, "DJI-M350-" + UUID.randomUUID());
    drone.changeServiceability(DroneServiceability.MAINTENANCE);
    UUID droneId = drones.saveAndFlush(drone).getId();
    jdbcTemplate.update("UPDATE inspections SET drone_id = ? WHERE id = ?", droneId, inspectionId);

    ComplianceGateResponse gate = service.evaluate(adminId, inspectionId);

    assertThat(gate.blockers())
        .anySatisfy(blocker -> assertThat(blocker.code()).isEqualTo("DRONE_NOT_SERVICEABLE"));
  }

  @Test
  void anInspectorMayNotRunTheComplianceGate() {
    UUID inspectorId = createInspector();

    assertThatThrownBy(() -> service.evaluate(inspectorId, inspectionId))
        .isInstanceOf(BusinessException.class)
        .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo("FORBIDDEN"));
  }

  @Test
  void anAdministratorFromAnotherOrganizationSeesNothing() {
    grantedPermit(plannedStart, plannedStart.plus(1, ChronoUnit.DAYS));
    UUID otherOrganizationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, 'Other', 'Other', ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        otherOrganizationId,
        "ORG-" + otherOrganizationId);

    assertThatThrownBy(
            () ->
                service.evaluate(createUser(otherOrganizationId, UserRole.ORG_ADMIN), inspectionId))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("INSPECTION_NOT_FOUND"));
  }

  @Test
  void linkingAPermitFromAnotherOrganizationIsRefused() {
    UUID otherOrganizationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, 'Other', 'Other', ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        otherOrganizationId,
        "ORG-" + otherOrganizationId);
    UUID otherCategoryId = UUID.randomUUID();
    String code = "BRIDGE-" + UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO asset_categories (id, organization_id, code, name) VALUES (?, ?, ?, 'Bridge')",
        otherCategoryId,
        otherOrganizationId,
        code.toUpperCase());
    UUID foreignAssetId =
        assets
            .saveAndFlush(
                new Asset(
                    otherOrganizationId,
                    otherCategoryId,
                    "ASSET-" + UUID.randomUUID(),
                    "Foreign Bridge",
                    null,
                    null,
                    null,
                    null,
                    null,
                    adminId))
            .getId();
    FlightPermit foreignPermit =
        permits.saveAndFlush(
            new FlightPermit(otherOrganizationId, foreignAssetId, "UAV", null, "CAAC"));

    assertThatThrownBy(
            () ->
                service.linkPermitReferences(
                    adminId,
                    inspectionId,
                    new LinkPermitReferencesRequest(List.of(foreignPermit.getId()))))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("PERMIT_SCOPE_DENIED"));
  }

  @Test
  void anInspectorCannotLinkPermitEvenWithinTheirOrganization() {
    UUID inspectorId = createInspector();
    FlightPermit permit = grantedPermit(plannedStart, plannedStart.plus(1, ChronoUnit.DAYS));

    assertThatThrownBy(
            () ->
                service.linkPermitReferences(
                    inspectorId,
                    inspectionId,
                    new LinkPermitReferencesRequest(List.of(permit.getId()))))
        .isInstanceOf(BusinessException.class)
        .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo("FORBIDDEN"));
  }

  @Test
  void aPermitForAnotherAssetInTheSameOrganizationCannotBeLinked() {
    UUID otherAssetId = createAsset("ASSET-" + UUID.randomUUID(), "Other bridge");
    FlightPermit permit =
        permits.saveAndFlush(new FlightPermit(organizationId, otherAssetId, "UAV", null, "CAAC"));

    assertThatThrownBy(
            () ->
                service.linkPermitReferences(
                    adminId,
                    inspectionId,
                    new LinkPermitReferencesRequest(List.of(permit.getId()))))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("PERMIT_ASSET_MISMATCH"));
  }

  @Test
  void anActivePermitDoesNotHideAnotherExpiredApplicablePermit() {
    FlightPermit active =
        grantedPermit(
            plannedStart.minus(1, ChronoUnit.DAYS), plannedStart.plus(1, ChronoUnit.DAYS));
    FlightPermit expired = grantedPermit(plannedStart, plannedStart.plus(1, ChronoUnit.DAYS));
    expired.defineValidity(
        plannedStart.minus(10, ChronoUnit.DAYS), plannedStart.minus(1, ChronoUnit.DAYS));

    ComplianceGateResponse gate = service.evaluate(adminId, inspectionId);

    assertThat(gate.linkedPermitIds()).contains(active.getId());
    assertThat(gate.blockers())
        .anySatisfy(blocker -> assertThat(blocker.code()).isEqualTo("PERMIT_OUTSIDE_VALIDITY"));
  }

  private UUID createInspector() {
    return createUser(organizationId, UserRole.INSPECTOR);
  }

  private UUID createAsset(String code, String name) {
    return assets
        .saveAndFlush(
            new Asset(
                organizationId,
                categories
                    .findById(assets.findById(assetId).orElseThrow().getCategoryId())
                    .orElseThrow()
                    .getId(),
                code,
                name,
                null,
                null,
                null,
                null,
                null,
                adminId))
        .getId();
  }

  private UUID createUser(UUID ownerOrganizationId, UserRole role) {
    User user =
        new User(
            "user-" + UUID.randomUUID() + "@example.test",
            "User",
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            ownerOrganizationId);
    user.addRole(role);
    return users.saveAndFlush(user).getId();
  }

  private FlightPermit grantedPermit(Instant from, Instant until) {
    FlightPermit permit = new FlightPermit(organizationId, assetId, "UAV", null, "CAAC");
    permit.grant("UAV-2026-" + UUID.randomUUID(), "CAAC", from, until);
    return permits.saveAndFlush(permit);
  }

  private UUID createInspection(String status, Instant plannedStartAt, UUID droneId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspections (id, organization_id, asset_id, drone_id, status, objective,
                                planned_start_at, planned_end_at)
        VALUES (?, ?, ?, ?, ?, 'Routine span inspection', ?, ?)
        """,
        id,
        organizationId,
        assetId,
        droneId,
        status,
        toOffsetDateTime(plannedStartAt),
        toOffsetDateTime(plannedStartAt == null ? null : plannedStartAt.plus(2, ChronoUnit.HOURS)));
    return id;
  }

  /** JdbcTemplate cannot infer a Postgres type for {@link Instant}; TIMESTAMPTZ wants an offset. */
  private static OffsetDateTime toOffsetDateTime(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}

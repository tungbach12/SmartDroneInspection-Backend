package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.AssetTestFixture;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.inspections.api.dto.request.PrepareShotListRequest;
import com.smartdroneinspection.inspections.api.dto.request.SubmitPreparationRequest;
import com.smartdroneinspection.inspections.api.dto.response.InspectionPreparationResponse;
import com.smartdroneinspection.inspections.domain.enums.InspectionPreparationStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.service.InspectionPreparationService;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * MF2-03 and MF2-06 against a real database.
 *
 * <p>Two things are being proved here. First that an inspector drafts and submits a preparation.
 * Second, and more load-bearing, that {@code inspection_preparations} has no organization column of
 * its own - tenant scope has to come from the inspection it belongs to - so the scope rules are the
 * part most likely to be wrong.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InspectionPreparationServiceTest {

  static final String SPAN_SHOTS =
      """
      [{"component":"Span P4","modality":"RGB","required":true},\
      {"component":"Pier 3 base","modality":"RGB","required":true}]\
      """;

  @Autowired InspectionPreparationService service;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired AssetCategoryRepository categories;
  @Autowired ChecklistTemplateRepository templates;
  @Autowired JdbcTemplate jdbcTemplate;

  UUID organizationId;
  UUID inspectorId;
  UUID inspectionId;

  @BeforeEach
  void setUp() {
    AssetTestFixture.Data data =
        new AssetTestFixture(categories, templates, assets, users, jdbcTemplate).create();
    organizationId = data.organizationId();
    inspectorId = createInspector(organizationId);

    UUID assetId =
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

    inspectionId = createInspection(organizationId, assetId, inspectorId);
  }

  @Test
  void anInspectorDraftsAShotListAndSubmitsIt() {
    InspectionPreparationResponse drafted =
        service.prepareShotList(
            inspectorId,
            inspectionId,
            new PrepareShotListRequest(
                SPAN_SHOTS,
                """
                ["RGB","THERMAL"]\
                """,
                """
                [{"restriction":"No access 06:00-08:00"}]\
                """,
                "Live 110V near pier 3; harbour traffic after 16:00"));

    assertThat(drafted.status()).isEqualTo(InspectionPreparationStatus.DRAFT);
    assertThat(drafted.shotList()).contains("Span P4");
    assertThat(drafted.evidenceTypes()).contains("THERMAL");
    assertThat(drafted.accessConstraints()).contains("No access");
    assertThat(drafted.safetyObservations()).contains("harbour traffic");
    assertThat(drafted.submittedAt()).isNull();

    var submitted =
        service.submitPreparation(inspectorId, drafted.id(), new SubmitPreparationRequest(null));

    assertThat(submitted.status()).isEqualTo(InspectionPreparationStatus.SUBMITTED);
    assertThat(submitted.submittedAt()).isNotNull();
    assertThat(submitted.preparationVersion()).isEqualTo(1);
  }

  @Test
  void startingTheFirstPreparationMovesAssignedInspectionToPreparing() {
    InspectionPreparationResponse drafted =
        service.prepareShotList(
            inspectorId,
            inspectionId,
            new PrepareShotListRequest(SPAN_SHOTS, null, null, "Live 110V near pier 3"));

    assertThat(drafted.status()).isEqualTo(InspectionPreparationStatus.DRAFT);
    assertThat(inspectionStatus()).isEqualTo(InspectionStatus.PREPARING);
  }

  @Test
  void editingAnExistingDraftKeepsInspectionPreparing() {
    service.prepareShotList(
        inspectorId,
        inspectionId,
        new PrepareShotListRequest(SPAN_SHOTS, null, null, "Initial hazard"));

    InspectionPreparationResponse edited =
        service.prepareShotList(
            inspectorId,
            inspectionId,
            new PrepareShotListRequest(SPAN_SHOTS, null, null, "Updated hazard"));

    assertThat(edited.safetyObservations()).isEqualTo("Updated hazard");
    assertThat(inspectionStatus()).isEqualTo(InspectionStatus.PREPARING);
  }

  @Test
  void readyInspectionCannotStartPreparationAndRemainsUnchanged() {
    jdbcTemplate.update(
        "UPDATE inspections SET status = 'READY_FOR_FLIGHT' WHERE id = ?", inspectionId);

    assertThatThrownBy(
            () ->
                service.prepareShotList(
                    inspectorId,
                    inspectionId,
                    new PrepareShotListRequest(SPAN_SHOTS, null, null, "Live 110V")))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("PREPARATION_NOT_ALLOWED"));

    assertThat(inspectionStatus()).isEqualTo(InspectionStatus.READY_FOR_FLIGHT);
  }

  @Test
  void readyInspectionCannotSubmitAnExistingDraft() {
    InspectionPreparationResponse drafted =
        service.prepareShotList(
            inspectorId,
            inspectionId,
            new PrepareShotListRequest(SPAN_SHOTS, null, null, "No hazards"));
    jdbcTemplate.update(
        "UPDATE inspections SET status = 'READY_FOR_FLIGHT' WHERE id = ?", inspectionId);

    assertThatThrownBy(
            () ->
                service.submitPreparation(
                    inspectorId, drafted.id(), new SubmitPreparationRequest(null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("PREPARATION_NOT_ALLOWED"));

    assertThat(inspectionStatus()).isEqualTo(InspectionStatus.READY_FOR_FLIGHT);
    assertThat(service.getPreparation(inspectorId, drafted.id()).status())
        .isEqualTo(InspectionPreparationStatus.DRAFT);
  }

  @Test
  void aPreparationWithNoShotListCannotBeSubmitted() {
    InspectionPreparationResponse drafted =
        service.prepareShotList(
            inspectorId,
            inspectionId,
            new PrepareShotListRequest(null, null, null, "Live 110V near pier 3"));

    assertThatThrownBy(
            () ->
                service.submitPreparation(
                    inspectorId, drafted.id(), new SubmitPreparationRequest(null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("PREPARATION_INCOMPLETE"));
  }

  @Test
  void aPreparationWithNoSafetyObservationsCannotBeSubmitted() {
    InspectionPreparationResponse drafted =
        service.prepareShotList(
            inspectorId, inspectionId, new PrepareShotListRequest(SPAN_SHOTS, null, null, null));

    assertThatThrownBy(
            () ->
                service.submitPreparation(
                    inspectorId, drafted.id(), new SubmitPreparationRequest(null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("PREPARATION_INCOMPLETE"));
  }

  @Test
  void anEmptyShotListArrayIsNotAShotList() {
    InspectionPreparationResponse drafted =
        service.prepareShotList(
            inspectorId,
            inspectionId,
            new PrepareShotListRequest("  []  ", null, null, "Live 110V near pier 3"));

    assertThatThrownBy(
            () ->
                service.submitPreparation(
                    inspectorId, drafted.id(), new SubmitPreparationRequest(null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("PREPARATION_INCOMPLETE"));
  }

  @Test
  void anotherInspectorCannotSubmitThisPreparation() {
    InspectionPreparationResponse drafted =
        service.prepareShotList(
            inspectorId,
            inspectionId,
            new PrepareShotListRequest(SPAN_SHOTS, null, null, "Live 110V near pier 3"));
    UUID otherInspectorId = createInspector(organizationId);

    assertThatThrownBy(
            () ->
                service.submitPreparation(
                    otherInspectorId,
                    drafted.id(),
                    new SubmitPreparationRequest("Not mine to submit")))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("PREPARATION_SCOPE_DENIED"));
  }

  @Test
  void anInspectorFromAnotherOrganizationSeesNothing() {
    InspectionPreparationResponse drafted =
        service.prepareShotList(
            inspectorId,
            inspectionId,
            new PrepareShotListRequest(SPAN_SHOTS, null, null, "Live 110V near pier 3"));

    UUID otherOrganizationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, 'Other', 'Other', ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        otherOrganizationId,
        "ORG-" + otherOrganizationId);
    UUID outsiderId = createInspector(otherOrganizationId);

    assertThatThrownBy(
            () ->
                service.submitPreparation(
                    outsiderId,
                    drafted.id(),
                    new SubmitPreparationRequest("Another tenant's work")))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("PREPARATION_NOT_FOUND"));
  }

  @Test
  void anOrganizationAdminCannotDraftOrSubmitForAnInspector() {
    UUID adminId = createUser(organizationId, UserRole.ORG_ADMIN, "org-admin");

    assertThatThrownBy(
            () ->
                service.prepareShotList(
                    adminId,
                    inspectionId,
                    new PrepareShotListRequest(SPAN_SHOTS, null, null, "Live 110V")))
        .isInstanceOf(BusinessException.class)
        .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo("FORBIDDEN"));
  }

  @Test
  void anInspectionWithoutAPreparationHasNothingToOpen() {
    assertThatThrownBy(
            () ->
                service.submitPreparation(
                    inspectorId, UUID.randomUUID(), new SubmitPreparationRequest(null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code()).isEqualTo("PREPARATION_NOT_FOUND"));
  }

  @Test
  void aSubmittedPreparationCannotBeSubmittedAgain() {
    InspectionPreparationResponse drafted =
        service.prepareShotList(
            inspectorId,
            inspectionId,
            new PrepareShotListRequest(SPAN_SHOTS, null, null, "Live 110V near pier 3"));
    service.submitPreparation(inspectorId, drafted.id(), new SubmitPreparationRequest(null));

    assertThatThrownBy(
            () ->
                service.submitPreparation(
                    inspectorId, drafted.id(), new SubmitPreparationRequest("Resubmitting")))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("PREPARATION_NOT_EDITABLE"));
  }

  private InspectionStatus inspectionStatus() {
    return InspectionStatus.valueOf(
        jdbcTemplate.queryForObject(
            "SELECT status FROM inspections WHERE id = ?", String.class, inspectionId));
  }

  private UUID createInspector(UUID ownerOrganizationId) {
    return createUser(ownerOrganizationId, UserRole.INSPECTOR, "inspector");
  }

  private UUID createUser(UUID ownerOrganizationId, UserRole role, String label) {
    User user =
        new User(
            label + "-" + UUID.randomUUID() + "@example.test",
            label,
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            ownerOrganizationId);
    user.addRole(role);
    return users.saveAndFlush(user).getId();
  }

  private UUID createInspection(UUID ownerOrganizationId, UUID assetId, UUID creatorId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO inspections (id, organization_id, asset_id, inspector_id, drone_id, status, objective)
        VALUES (?, ?, ?, ?, NULL, 'ASSIGNED', 'Routine span inspection')
        """,
        id,
        ownerOrganizationId,
        assetId,
        creatorId);
    return id;
  }
}

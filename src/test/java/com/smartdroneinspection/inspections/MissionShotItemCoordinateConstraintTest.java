package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.enums.ChecklistResponseType;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.inspectionrequests.domain.InspectionQuotation;
import com.smartdroneinspection.inspectionrequests.domain.InspectionRequest;
import com.smartdroneinspection.inspectionrequests.domain.InspectionServiceOrder;
import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionRequestPriority;
import com.smartdroneinspection.inspectionrequests.repository.InspectionQuotationRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionRequestRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionServiceOrderRepository;
import com.smartdroneinspection.inspections.domain.DroneMissionPlan;
import com.smartdroneinspection.inspections.repository.DroneMissionPlanRepository;
import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code ck_mission_shot_items_waypoint} executed, rather than asserted about.
 *
 * <p>BR-07: waypoints are optional, but a half-specified waypoint is not. Each probe runs in its
 * own autocommit transaction so a refusal cannot poison the next probe.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MissionShotItemCoordinateConstraintTest {

  @Autowired DroneMissionPlanRepository missionPlanRepository;
  @Autowired InspectionServiceOrderRepository serviceOrderRepository;
  @Autowired InspectionQuotationRepository quotationRepository;
  @Autowired InspectionRequestRepository requestRepository;
  @Autowired OrganizationRepository organizationRepository;
  @Autowired UserRepository userRepository;
  @Autowired AssetCategoryRepository categoryRepository;
  @Autowired ChecklistTemplateRepository templateRepository;
  @Autowired AssetRepository assetRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  private UUID planId;
  private UUID orderId;
  private UUID quotationId;
  private UUID requestId;

  @AfterEach
  void cleanUp() {
    if (planId != null) {
      jdbcTemplate.update("DELETE FROM mission_shot_items WHERE mission_plan_id = ?", planId);
      jdbcTemplate.update("DELETE FROM drone_mission_plans WHERE id = ?", planId);
      planId = null;
    }
    if (orderId != null) {
      jdbcTemplate.update("DELETE FROM inspection_service_orders WHERE id = ?", orderId);
      orderId = null;
    }
    if (quotationId != null) {
      jdbcTemplate.update("DELETE FROM inspection_quotations WHERE id = ?", quotationId);
      quotationId = null;
    }
    if (requestId != null) {
      jdbcTemplate.update("DELETE FROM inspection_requests WHERE id = ?", requestId);
      requestId = null;
    }
  }

  @Test
  void acceptsBothCoordinatesAbsentForManualFlight() {
    UUID plan = createPlan();

    assertThat(insertShotItem(plan, 1, null, null, "45.000"))
        .as("manual flight: no waypoint coordinates at all")
        .isEqualTo(1);
    assertThat(insertShotItem(plan, 2, null, null, "80.000"))
        .as("altitude-only is the both-null case plus an altitude")
        .isEqualTo(1);
  }

  @Test
  void acceptsBothCoordinatesSuppliedAndInRange() {
    UUID plan = createPlan();

    assertThat(insertShotItem(plan, 1, "21.0285", "105.8542", "50.000")).isEqualTo(1);
    assertThat(insertShotItem(plan, 2, "-90", "-180", "50.000")).isEqualTo(1);
    assertThat(insertShotItem(plan, 3, "90", "180", "50.000")).isEqualTo(1);
  }

  @Test
  void refusesLatitudeWithoutLongitude() {
    UUID plan = createPlan();

    assertThatThrownBy(() -> insertShotItem(plan, 1, "21.0285", null, "45.000"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_mission_shot_items_waypoint");
  }

  @Test
  void refusesLongitudeWithoutLatitude() {
    UUID plan = createPlan();

    assertThatThrownBy(() -> insertShotItem(plan, 1, null, "105.8542", "45.000"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_mission_shot_items_waypoint");
  }

  @Test
  void refusesLatitudeOutOfRange() {
    UUID plan = createPlan();

    assertThatThrownBy(() -> insertShotItem(plan, 1, "95.0000", "105.8542", "45.000"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_mission_shot_items_waypoint");
  }

  @Test
  void refusesLongitudeOutOfRange() {
    UUID plan = createPlan();

    assertThatThrownBy(() -> insertShotItem(plan, 1, "21.0285", "195.8542", "45.000"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_mission_shot_items_waypoint");
  }

  private int insertShotItem(UUID plan, int sequence, String lat, String lon, String altitude) {
    return jdbcTemplate.update(
        "INSERT INTO mission_shot_items (id, mission_plan_id, sequence_number,"
            + " component_reference, waypoint_latitude, waypoint_longitude,"
            + " waypoint_altitude_m, created_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, now())",
        UUID.randomUUID(),
        plan,
        sequence,
        "Raw constraint probe " + sequence,
        lat == null ? null : new BigDecimal(lat),
        lon == null ? null : new BigDecimal(lon),
        new BigDecimal(altitude));
  }

  private UUID createPlan() {
    Organization clientOrg =
        organizationRepository.saveAndFlush(
            new Organization(
                "Client Org " + UUID.randomUUID(),
                "CLI-" + UUID.randomUUID().toString().substring(0, 8),
                "Client description"));

    UUID providerId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO provider_organizations (id, name, legal_name, tax_code, business_license_no, status) VALUES (?,?,?,?,?, 'VERIFIED')",
        providerId,
        "Skyline " + UUID.randomUUID(),
        "Skyline JSC",
        "010" + System.nanoTime(),
        "p@skyline.test");

    User user =
        new User(
            "user" + UUID.randomUUID() + "@test.com",
            "Inspector User",
            "hashed",
            UserStatus.ACTIVE,
            ActorZone.SERVICE_WORKFORCE,
            null);
    user.setProviderId(providerId);
    user.addRole(UserRole.INSPECTOR);
    user = userRepository.saveAndFlush(user);

    AssetCategory category =
        categoryRepository.saveAndFlush(
            new AssetCategory("cat-" + UUID.randomUUID(), "Towers", "Telecommunication", true));
    ChecklistTemplate template =
        new ChecklistTemplate(
            "tmpl-" + UUID.randomUUID(),
            1,
            category.getId(),
            "Tower Checklist",
            "Desc",
            user.getId());
    template.addItem(
        "item1", "Rust Check", "Check rust", ChecklistResponseType.PASS_FAIL, true, 0, null, null);
    template.publish();
    UUID templateId = templateRepository.saveAndFlush(template).getId();

    UUID assetId =
        assetRepository
            .saveAndFlush(
                new Asset(
                    clientOrg.getId(),
                    category.getId(),
                    "asset-" + UUID.randomUUID(),
                    "Tower Alpha",
                    "Main tower",
                    "Hanoi",
                    null,
                    null,
                    null,
                    user.getId()))
            .getId();

    InspectionRequest request =
        requestRepository.saveAndFlush(
            InspectionRequest.adHoc(
                clientOrg.getId(),
                assetId,
                templateId,
                user.getId(),
                "Tower inspect",
                InspectionRequestPriority.NORMAL,
                null,
                null,
                null,
                null,
                null,
                null));
    requestId = request.getId();

    InspectionQuotation quotation =
        new InspectionQuotation(
            UUID.randomUUID(),
            request.getId(),
            1,
            null,
            user.getId(),
            "USD",
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(10),
            BigDecimal.valueOf(110),
            "{\"items\":[]}",
            "{\"scope\":\"tower\"}",
            BigDecimal.valueOf(2),
            "NET 30");
    quotation.setProviderId(providerId);
    quotation.send();
    quotation.approve(user.getId());
    quotation = quotationRepository.saveAndFlush(quotation);
    quotationId = quotation.getId();

    InspectionServiceOrder order =
        serviceOrderRepository.saveAndFlush(
            InspectionServiceOrder.fromApprovedQuotation(
                quotation,
                "SO-" + UUID.randomUUID(),
                user.getId(),
                "{\"report\":\"Final inspection report\"}"));
    orderId = order.getId();

    planId =
        missionPlanRepository
            .saveAndFlush(new DroneMissionPlan(order.getId(), providerId, 1, null, user.getId()))
            .getId();
    return planId;
  }
}

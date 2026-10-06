package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.domain.DroneMissionPlan;
import com.smartdroneinspection.inspections.domain.MissionShotItem;
import com.smartdroneinspection.inspections.domain.enums.AirspaceCheckStatus;
import com.smartdroneinspection.inspections.domain.enums.MissionPlanStatus;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class DroneMissionPlanTest {

  private static final String CAMERA = "DJI Matrice 300 RTK - Zenmuse H20T";
  private static final String DRONE = "DRONE-VN-2026-0042";
  private static final BigDecimal GSD = new BigDecimal("1.500000");
  private static final BigDecimal AGL = new BigDecimal("50.000");
  private static final BigDecimal FORWARD = new BigDecimal("75.00");
  private static final BigDecimal SIDE = new BigDecimal("65.00");
  private static final BigDecimal SENSOR = new BigDecimal("6.4");
  private static final BigDecimal FOCAL = new BigDecimal("24.0");
  private static final int WIDTH = 4000;

  private void assertSowRefused(String missingClause, Consumer<DroneMissionPlan> omit) {
    DroneMissionPlan plan = newCompleteDraftPlan();
    omit.accept(plan);

    assertThatThrownBy(() -> plan.approve(UUID.randomUUID()))
        .as("approval must refuse a plan missing " + missingClause)
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).code())
                    .isEqualTo("SOW_SPECIFICATION_INCOMPLETE"));
  }

  private void assertNotEditable(String mutator, Consumer<DroneMissionPlan> attempt) {
    DroneMissionPlan approved = newApprovedPlan();

    assertThatThrownBy(() -> attempt.accept(approved))
        .as(mutator + " must refuse to touch an approved mission plan")
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Approved or superseded mission plan cannot be edited");
  }

  private DroneMissionPlan newApprovedPlan() {
    DroneMissionPlan plan = newCompleteDraftPlan();
    plan.addShotItem(newValidShotItem());
    plan.recordAirspacePreCheck(AirspaceCheckStatus.CLEARED);
    plan.approve(UUID.randomUUID());
    return plan;
  }

  @Test
  void approvalRequiresEveryCompleteSowClause() {
    assertSowRefused(
        "no camera model",
        plan ->
            plan.setEquipmentAndSow(
                null, DRONE, UUID.randomUUID(), GSD, AGL, FORWARD, SIDE, SENSOR, FOCAL, WIDTH));
    assertSowRefused(
        "blank camera model",
        plan ->
            plan.setEquipmentAndSow(
                "  ", DRONE, UUID.randomUUID(), GSD, AGL, FORWARD, SIDE, SENSOR, FOCAL, WIDTH));
    assertSowRefused(
        "no target GSD",
        plan ->
            plan.setEquipmentAndSow(
                CAMERA, DRONE, UUID.randomUUID(), null, AGL, FORWARD, SIDE, SENSOR, FOCAL, WIDTH));
    assertSowRefused(
        "a zero target GSD",
        plan ->
            plan.setEquipmentAndSow(
                CAMERA,
                DRONE,
                UUID.randomUUID(),
                BigDecimal.ZERO,
                AGL,
                FORWARD,
                SIDE,
                SENSOR,
                FOCAL,
                WIDTH));
    assertSowRefused(
        "no planned AGL",
        plan ->
            plan.setEquipmentAndSow(
                CAMERA, DRONE, UUID.randomUUID(), GSD, null, FORWARD, SIDE, SENSOR, FOCAL, WIDTH));
    assertSowRefused(
        "a zero planned AGL",
        plan ->
            plan.setEquipmentAndSow(
                CAMERA,
                DRONE,
                UUID.randomUUID(),
                GSD,
                BigDecimal.ZERO,
                FORWARD,
                SIDE,
                SENSOR,
                FOCAL,
                WIDTH));
    assertSowRefused(
        "no forward overlap",
        plan ->
            plan.setEquipmentAndSow(
                CAMERA, DRONE, UUID.randomUUID(), GSD, AGL, null, SIDE, SENSOR, FOCAL, WIDTH));
    assertSowRefused(
        "no side overlap",
        plan ->
            plan.setEquipmentAndSow(
                CAMERA, DRONE, UUID.randomUUID(), GSD, AGL, FORWARD, null, SENSOR, FOCAL, WIDTH));
    assertSowRefused(
        "a zero forward overlap",
        plan ->
            plan.setEquipmentAndSow(
                CAMERA,
                DRONE,
                UUID.randomUUID(),
                GSD,
                AGL,
                BigDecimal.ZERO,
                SIDE,
                SENSOR,
                FOCAL,
                WIDTH));
    assertSowRefused(
        "a zero side overlap",
        plan ->
            plan.setEquipmentAndSow(
                CAMERA,
                DRONE,
                UUID.randomUUID(),
                GSD,
                AGL,
                FORWARD,
                BigDecimal.ZERO,
                SENSOR,
                FOCAL,
                WIDTH));
    assertSowRefused("no shot items", plan -> {});
  }

  @Test
  void approvalRequiresSowSpecifications_OnAnEmptyDraft() {
    DroneMissionPlan plan = newDraftPlan();

    assertThatThrownBy(() -> plan.approve(UUID.randomUUID()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException be = (BusinessException) ex;
              assertThat(be.code()).isEqualTo("SOW_SPECIFICATION_INCOMPLETE");
            });
  }

  @Test
  void everyMissionPlanMutatorRefusesToTouchAnApprovedPlan() {
    assertNotEditable(
        "setEquipmentAndSow",
        plan ->
            plan.setEquipmentAndSow(
                "Another camera",
                DRONE,
                UUID.randomUUID(),
                GSD,
                AGL,
                FORWARD,
                SIDE,
                SENSOR,
                FOCAL,
                WIDTH));
    assertNotEditable("addShotItem", plan -> plan.addShotItem(newValidShotItem()));
    assertNotEditable("clearShotItems", DroneMissionPlan::clearShotItems);
    assertNotEditable(
        "recordAirspacePreCheck", plan -> plan.recordAirspacePreCheck(AirspaceCheckStatus.CLEARED));
    assertNotEditable("verifyFlightPermit", plan -> plan.verifyFlightPermit("PERMIT-2026-0001"));
  }

  @Test
  void manualFlightIsValidWithoutWaypointCoordinates() {
    DroneMissionPlan plan = newCompleteDraftPlan();
    MissionShotItem shotItem =
        new MissionShotItem(
            1,
            "Turbine Blade A - Tip",
            null,
            null,
            new BigDecimal("45.0"),
            new BigDecimal("90.0"),
            new BigDecimal("-15.0"),
            new BigDecimal("1.2"),
            "Close-up inspection of leading edge");
    plan.addShotItem(shotItem);

    plan.recordAirspacePreCheck(AirspaceCheckStatus.CLEARED);
    plan.approve(UUID.randomUUID());

    assertThat(plan.getStatus()).isEqualTo(MissionPlanStatus.APPROVED);
    assertThat(plan.getShotItems()).hasSize(1);
    assertThat(plan.getShotItems().get(0).getWaypointLatitude()).isNull();
    assertThat(plan.getShotItems().get(0).getWaypointLongitude()).isNull();
  }

  @Test
  void waypointCoordinatesValidateWhenSupplied() {
    assertThatThrownBy(
            () ->
                new MissionShotItem(
                    1,
                    "Tower Base",
                    new BigDecimal("21.0285"),
                    null,
                    new BigDecimal("10.0"),
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Waypoint latitude and longitude must both be provided or both be null");

    assertThatThrownBy(
            () ->
                new MissionShotItem(
                    1,
                    "Tower Base",
                    new BigDecimal("95.0000"),
                    new BigDecimal("105.8542"),
                    new BigDecimal("10.0"),
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Latitude must be between -90 and 90");

    assertThatThrownBy(
            () ->
                new MissionShotItem(
                    1,
                    "Tower Base",
                    new BigDecimal("21.0285"),
                    new BigDecimal("195.8542"),
                    new BigDecimal("10.0"),
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Longitude must be between -180 and 180");
  }

  @Test
  void approvalBlockedWhenAirspaceNotChecked() {
    DroneMissionPlan plan = newCompleteDraftPlan();
    plan.addShotItem(newValidShotItem());

    assertThat(plan.getAirspaceCheckStatus()).isEqualTo(AirspaceCheckStatus.NOT_CHECKED);

    assertThatThrownBy(() -> plan.approve(UUID.randomUUID()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException be = (BusinessException) ex;
              assertThat(be.code()).isEqualTo("AIRSPACE_CLEARANCE_REQUIRED");
            });
  }

  @Test
  void approvalBlockedWhenAirspaceRequiredClearanceButNoPermit() {
    DroneMissionPlan plan = newCompleteDraftPlan();
    plan.addShotItem(newValidShotItem());
    plan.recordAirspacePreCheck(AirspaceCheckStatus.CLEARANCE_REQUIRED);

    assertThatThrownBy(() -> plan.approve(UUID.randomUUID()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException be = (BusinessException) ex;
              assertThat(be.code()).isEqualTo("FLIGHT_PERMIT_REQUIRED");
            });
  }

  @Test
  void approvalSucceedsWhenClearanceRequiredWithPermit() {
    DroneMissionPlan plan = newCompleteDraftPlan();
    plan.addShotItem(newValidShotItem());
    plan.recordAirspacePreCheck(AirspaceCheckStatus.CLEARANCE_REQUIRED);
    plan.verifyFlightPermit("PERMIT-2026-MIL-0084");
    plan.recordAirspacePreCheck(AirspaceCheckStatus.CLEARANCE_REQUIRED);

    plan.approve(UUID.randomUUID());
    assertThat(plan.getStatus()).isEqualTo(MissionPlanStatus.APPROVED);
    assertThat(plan.getFlightPermitReference()).isEqualTo("PERMIT-2026-MIL-0084");
  }

  @ParameterizedTest(name = "{0} cleared={1}")
  @CsvSource({
    "NOT_CHECKED, false",
    "CLEARED, true",
    "MANUAL_REVIEW, true",
    "CLEARANCE_REQUIRED, true",
    "BLOCKED, false"
  })
  void everyAirspaceStatusDecidesFlightClearingExplicitly(
      AirspaceCheckStatus status, boolean expectedCleared) {
    assertThat(status.isFlightCleared()).isEqualTo(expectedCleared);
  }

  @ParameterizedTest(name = "{0} blocks approval")
  @EnumSource(
      value = AirspaceCheckStatus.class,
      names = {"NOT_CHECKED", "BLOCKED"})
  void everyUnverifiedAirspaceStatusBlocksMissionApproval(AirspaceCheckStatus status) {
    DroneMissionPlan plan = newCompleteDraftPlan();
    plan.addShotItem(newValidShotItem());
    plan.recordAirspacePreCheck(status);

    assertThatThrownBy(() -> plan.approve(UUID.randomUUID()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).code())
                    .isEqualTo("AIRSPACE_CLEARANCE_REQUIRED"));
    assertThat(plan.getStatus())
        .as("a refused approval leaves the plan in DRAFT")
        .isEqualTo(MissionPlanStatus.DRAFT);
  }

  private DroneMissionPlan newDraftPlan() {
    return new DroneMissionPlan(UUID.randomUUID(), UUID.randomUUID(), 1, null, UUID.randomUUID());
  }

  private DroneMissionPlan newCompleteDraftPlan() {
    DroneMissionPlan plan = newDraftPlan();
    plan.setEquipmentAndSow(
        "DJI Matrice 300 RTK - Zenmuse H20T",
        "DRONE-VN-2026-0042",
        UUID.randomUUID(),
        new BigDecimal("1.500000"),
        new BigDecimal("50.000"),
        new BigDecimal("75.00"),
        new BigDecimal("65.00"),
        new BigDecimal("6.4"),
        new BigDecimal("24.0"),
        4000);
    return plan;
  }

  private MissionShotItem newValidShotItem() {
    return new MissionShotItem(
        1,
        "Structural Inspection Point 1",
        null,
        null,
        new BigDecimal("50.0"),
        new BigDecimal("180.0"),
        new BigDecimal("-30.0"),
        new BigDecimal("1.5"),
        "Standard capture");
  }
}

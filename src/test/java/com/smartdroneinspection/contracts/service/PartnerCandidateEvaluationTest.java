package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartnerCandidateEvaluationTest {

  private final UUID inspectorId = UUID.randomUUID();
  private final UUID droneId = UUID.randomUUID();
  private final Instant startAt = Instant.parse("2026-12-04T01:00:00Z");

  @Test
  void evaluatesPassingPairWithMatchingCapabilities() {
    Drone drone = mock(Drone.class);
    when(drone.getId()).thenReturn(droneId);
    when(drone.getInspectorOwnerId()).thenReturn(inspectorId);
    when(drone.isEligibleForAssignment()).thenReturn(true);
    when(drone.getRegistrationNumber()).thenReturn("VN-RPAS-001");
    when(drone.getRegistrationExpiresAt()).thenReturn(Instant.parse("2027-12-31T00:00:00Z"));
    when(drone.resolveMtowKg()).thenReturn(6.47);
    when(drone.requiresClassBLicense()).thenReturn(true);
    when(drone.getFeatures()).thenReturn(List.of("THERMAL_CAMERA"));
    when(drone.isHasRtk()).thenReturn(true);

    UserAccess.ActiveUser inspector =
        new UserAccess.ActiveUser(
            inspectorId,
            Set.of(Roles.INSPECTOR),
            "INSPECTOR",
            "Tran Minh Quan",
            "quan@example.test",
            "LIC-700",
            "CLASS_B",
            Instant.parse("2027-12-31T00:00:00Z"),
            "licenses/quan.pdf",
            "https://cdn.example.test/avatars/quan.png");

    PartnerCandidateEvaluation.Result result =
        PartnerCandidateEvaluation.evaluate(
            inspectorId,
            droneId,
            "Tran Minh Quan",
            "https://cdn.example.test/avatars/quan.png",
            "SDI-700",
            "Matrice 350 RTK",
            true,
            inspector,
            drone,
            startAt,
            false,
            false,
            true,
            null,
            List.of("THERMAL_CAMERA", "RTK_POSITIONING"));

    assertThat(result.eligibility()).isEqualTo(PartnerCandidateEvaluation.Status.PASS);
    assertThat(result.capabilityFit()).isEqualTo(PartnerCandidateEvaluation.Status.MATCH);
    assertThat(result.inspectorAvatarUrl()).isEqualTo("https://cdn.example.test/avatars/quan.png");
    assertThat(result.droneHasImage()).isTrue();
    assertThat(result.droneFeatureCodes())
        .containsExactlyInAnyOrder("THERMAL_CAMERA", "RTK_POSITIONING");
  }

  @Test
  void returnsRegisteredDroneCapabilityCodesEvenWithoutRequiredCapabilities() {
    Drone drone = mock(Drone.class);
    when(drone.isEligibleForAssignment()).thenReturn(true);
    when(drone.getRegistrationNumber()).thenReturn("VN-RPAS-CAP");
    when(drone.getRegistrationExpiresAt()).thenReturn(Instant.parse("2027-12-31T00:00:00Z"));
    when(drone.resolveMtowKg()).thenReturn(1.0);
    when(drone.requiresClassBLicense()).thenReturn(false);
    when(drone.getFeatures()).thenReturn(List.of("THERMAL_CAMERA", " NIGHT_VISION "));

    UserAccess.ActiveUser inspector =
        new UserAccess.ActiveUser(
            inspectorId,
            Set.of(Roles.INSPECTOR),
            "INSPECTOR",
            "Inspector",
            "inspector@example.test",
            "LIC-CAP",
            "CLASS_A",
            Instant.parse("2027-12-31T00:00:00Z"),
            "licenses/inspector.pdf");

    var result =
        PartnerCandidateEvaluation.evaluate(
            inspectorId,
            droneId,
            "Inspector",
            null,
            "SERIAL-CAP",
            "Test drone",
            false,
            inspector,
            drone,
            startAt,
            false,
            false,
            true,
            null,
            List.of());

    assertThat(result.droneFeatureCodes()).containsExactly("NIGHT_VISION", "THERMAL_CAMERA");
  }

  @Test
  void reportsFailAndMismatchWhenScheduleConflictsOrCapabilitiesAreMissing() {
    Drone drone = mock(Drone.class);
    when(drone.getId()).thenReturn(droneId);
    when(drone.getInspectorOwnerId()).thenReturn(inspectorId);
    when(drone.isEligibleForAssignment()).thenReturn(false);
    when(drone.getRegistrationNumber()).thenReturn("VN-RPAS-002");
    when(drone.getRegistrationExpiresAt()).thenReturn(Instant.parse("2026-11-01T00:00:00Z"));
    when(drone.resolveMtowKg()).thenReturn(6.47);
    when(drone.requiresClassBLicense()).thenReturn(true);
    when(drone.getFeatures()).thenReturn(List.of("VISUAL_RGB"));

    UserAccess.ActiveUser inspector =
        new UserAccess.ActiveUser(
            inspectorId,
            Set.of(Roles.INSPECTOR),
            "INSPECTOR",
            "Le Van Nam",
            "nam@example.test",
            "LIC-800",
            "CLASS_A",
            Instant.parse("2026-11-01T00:00:00Z"),
            "licenses/nam.pdf");

    PartnerCandidateEvaluation.Result result =
        PartnerCandidateEvaluation.evaluate(
            inspectorId,
            droneId,
            "Le Van Nam",
            null,
            "SDI-800",
            "Mavic 3T",
            false,
            inspector,
            drone,
            startAt,
            true,
            true,
            false,
            null,
            List.of("THERMAL_CAMERA"));

    assertThat(result.eligibility()).isEqualTo(PartnerCandidateEvaluation.Status.FAIL);
    assertThat(result.capabilityFit()).isEqualTo(PartnerCandidateEvaluation.Status.MISMATCH);
  }

  @Test
  void reportsUnknownWhenMtowOrCapabilitiesAreUnrecorded() {
    Drone drone = mock(Drone.class);
    when(drone.getId()).thenReturn(droneId);
    when(drone.getInspectorOwnerId()).thenReturn(inspectorId);
    when(drone.isEligibleForAssignment()).thenReturn(true);
    when(drone.getRegistrationNumber()).thenReturn("VN-RPAS-003");
    when(drone.getRegistrationExpiresAt()).thenReturn(null);
    when(drone.resolveMtowKg()).thenReturn(null);
    when(drone.getFeatures()).thenReturn(List.of());

    UserAccess.ActiveUser inspector =
        new UserAccess.ActiveUser(
            inspectorId,
            Set.of(Roles.INSPECTOR),
            "INSPECTOR",
            "Pham Thi Hoa",
            "hoa@example.test",
            "LIC-900",
            null,
            null,
            "licenses/hoa.pdf");

    PartnerCandidateEvaluation.Result result =
        PartnerCandidateEvaluation.evaluate(
            inspectorId,
            droneId,
            "Pham Thi Hoa",
            null,
            "SDI-900",
            "Custom Quad",
            false,
            inspector,
            drone,
            startAt,
            false,
            false,
            true,
            null,
            List.of("LIDAR_3D"));

    assertThat(result.eligibility()).isEqualTo(PartnerCandidateEvaluation.Status.UNKNOWN);
    assertThat(result.capabilityFit()).isEqualTo(PartnerCandidateEvaluation.Status.UNKNOWN);
  }

  @Test
  void altitudeMatchesWhenDroneCeilingMeetsOrExceedsStructureHeight() {
    var equal = evaluateWithHeight("80.00", "80.0");
    var above = evaluateWithHeight("80.00", "120.0");

    assertThat(equal.capabilityFit()).isEqualTo(PartnerCandidateEvaluation.Status.MATCH);
    assertThat(above.capabilityFit()).isEqualTo(PartnerCandidateEvaluation.Status.MATCH);
    assertThat(equal.capabilityChecks())
        .singleElement()
        .satisfies(check -> assertThat(check.code()).isEqualTo("DRONE_MAX_ALTITUDE"));
    assertThat(above.structureHeightMeters()).isEqualByComparingTo("80.00");
    assertThat(above.droneMaxAltitudeMeters()).isEqualByComparingTo("120.0");
    assertThat(above.footprintAreaSquareMeters()).isEqualByComparingTo("1500.00");
  }

  @Test
  void altitudeBelowStructureHeightIsMismatchAndStaysAdvisory() {
    var result = evaluateWithHeight("81.00", "80.0");

    assertThat(result.capabilityFit()).isEqualTo(PartnerCandidateEvaluation.Status.MISMATCH);
    assertThat(result.capabilityChecks())
        .singleElement()
        .satisfies(
            check -> {
              assertThat(check.status()).isEqualTo(PartnerCandidateEvaluation.Status.MISMATCH);
              assertThat(check.explanation()).contains("80 m", "81 m", "not flight clearance");
            });
  }

  @Test
  void unrecordedAltitudeIsUnknownOnlyWhenStructureHeightIsKnown() {
    var unknown = evaluateWithHeight("45.00", null);
    var noConstraint = evaluateWithHeight(null, null);

    assertThat(unknown.capabilityFit()).isEqualTo(PartnerCandidateEvaluation.Status.UNKNOWN);
    assertThat(noConstraint.capabilityFit()).isEqualTo(PartnerCandidateEvaluation.Status.MATCH);
    assertThat(noConstraint.capabilityChecks())
        .singleElement()
        .satisfies(check -> assertThat(check.code()).isEqualTo("NO_CAPABILITY_REQUIREMENTS"));
  }

  private PartnerCandidateEvaluation.Result evaluateWithHeight(String height, String ceiling) {
    Drone drone = mock(Drone.class);
    when(drone.getInspectorOwnerId()).thenReturn(inspectorId);
    when(drone.isEligibleForAssignment()).thenReturn(true);
    when(drone.getRegistrationNumber()).thenReturn("VN-RPAS-010");
    when(drone.getRegistrationExpiresAt()).thenReturn(Instant.parse("2027-12-31T00:00:00Z"));
    when(drone.resolveMtowKg()).thenReturn(6.47);
    when(drone.requiresClassBLicense()).thenReturn(true);
    when(drone.getFeatures()).thenReturn(List.of());
    when(drone.getMaxAltitudeMeters()).thenReturn(ceiling == null ? null : new BigDecimal(ceiling));
    Asset asset = mock(Asset.class);
    when(asset.getStructureHeightMeters())
        .thenReturn(height == null ? null : new BigDecimal(height));
    when(asset.getFootprintAreaSquareMeters()).thenReturn(new BigDecimal("1500.00"));
    UserAccess.ActiveUser inspector =
        new UserAccess.ActiveUser(
            inspectorId,
            Set.of(Roles.INSPECTOR),
            "INSPECTOR",
            "Tran Minh Quan",
            "quan@example.test",
            "LIC-700",
            "CLASS_B",
            Instant.parse("2027-12-31T00:00:00Z"),
            "licenses/quan.pdf",
            null);
    return PartnerCandidateEvaluation.evaluate(
        inspectorId,
        droneId,
        "Tran Minh Quan",
        null,
        "SDI-700",
        "Matrice 350 RTK",
        false,
        inspector,
        drone,
        startAt,
        false,
        false,
        true,
        asset,
        List.of());
  }
}

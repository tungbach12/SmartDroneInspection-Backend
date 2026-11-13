package com.smartdroneinspection.contracts.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Deterministic advisory evaluation of a partner Inspector/drone pair. */
public final class PartnerCandidateEvaluation {

  private PartnerCandidateEvaluation() {}

  public static Result evaluate(
      UUID inspectorId,
      UUID droneId,
      String inspectorName,
      String inspectorAvatarUrl,
      String serialNumber,
      String model,
      boolean droneHasImage,
      UserAccess.ActiveUser inspector,
      Drone drone,
      Instant occurrenceStart,
      boolean contractScheduleConflict,
      boolean inspectionScheduleConflict,
      boolean qualifiesForWindow,
      Asset asset,
      List<String> requiredFeatureCodes) {
    List<Check> hardGates = new ArrayList<>();
    List<Check> capabilityChecks = new ArrayList<>();

    boolean activePartner = inspector != null && inspector.isInspectorPartner();
    hardGates.add(
        check(
            "INSPECTOR_ACTIVE_PARTNER",
            activePartner ? Status.PASS : Status.FAIL,
            activePartner ? "Active partner." : "Inspector is not an active partner."));

    if (!activePartner) {
      hardGates.add(check("PILOT_LICENSE_VALID", Status.FAIL, "No active partner pilot profile."));
      hardGates.add(
          check("PILOT_LICENSE_CLASS_MATCH", Status.FAIL, "No active partner pilot profile."));
    } else if (inspector.pilotLicenseNumber() == null || inspector.pilotLicenseNumber().isBlank()) {
      hardGates.add(check("PILOT_LICENSE_VALID", Status.FAIL, "Pilot license number is missing."));
    } else if (occurrenceStart == null) {
      hardGates.add(check("PILOT_LICENSE_VALID", Status.UNKNOWN, "Occurrence time is missing."));
    } else if (inspector.pilotLicenseExpiresAt() == null) {
      hardGates.add(
          check("PILOT_LICENSE_VALID", Status.UNKNOWN, "License expiry is not recorded."));
    } else {
      boolean licenseValid = inspector.pilotLicenseExpiresAt().isAfter(occurrenceStart);
      hardGates.add(
          check(
              "PILOT_LICENSE_VALID",
              licenseValid ? Status.PASS : Status.FAIL,
              licenseValid
                  ? "License valid for planned start."
                  : "License expires before planned start."));
    }

    if (drone == null) {
      hardGates.add(check("DRONE_AVAILABLE", Status.FAIL, "Drone record is unavailable."));
      hardGates.add(check("DRONE_OWNER", Status.FAIL, "Drone record is unavailable."));
      hardGates.add(check("DRONE_REGISTRATION", Status.UNKNOWN, "Registration is unknown."));
      hardGates.add(check("DRONE_MTOW_UNKNOWN", Status.UNKNOWN, "Drone MTOW is not known."));
    } else {
      boolean available = drone.isEligibleForAssignment();
      hardGates.add(
          check(
              "DRONE_AVAILABLE",
              available ? Status.PASS : Status.FAIL,
              available
                  ? "Drone is active and serviceable."
                  : "Drone is not active and serviceable."));
      boolean ownerMatches = inspectorId != null && inspectorId.equals(drone.getInspectorOwnerId());
      hardGates.add(
          check(
              "DRONE_OWNER",
              ownerMatches ? Status.PASS : Status.FAIL,
              ownerMatches
                  ? "Drone belongs to this Inspector."
                  : "Drone belongs to another Inspector."));
      if (drone.getRegistrationNumber() == null || drone.getRegistrationNumber().isBlank()) {
        hardGates.add(check("DRONE_REGISTRATION", Status.FAIL, "Registration number is missing."));
      } else if (drone.getRegistrationExpiresAt() == null || occurrenceStart == null) {
        hardGates.add(
            check("DRONE_REGISTRATION", Status.UNKNOWN, "Registration validity is incomplete."));
      } else {
        boolean registrationValid = drone.getRegistrationExpiresAt().isAfter(occurrenceStart);
        hardGates.add(
            check(
                "DRONE_REGISTRATION",
                registrationValid ? Status.PASS : Status.FAIL,
                registrationValid
                    ? "Registration valid for planned start."
                    : "Registration expires before planned start."));
      }

      if (drone.resolveMtowKg() == null) {
        hardGates.add(check("DRONE_MTOW_UNKNOWN", Status.UNKNOWN, "Drone MTOW is unknown."));
      }
      if (inspector == null
          || inspector.pilotLicenseClass() == null
          || inspector.pilotLicenseClass().isBlank()) {
        hardGates.add(
            check("PILOT_LICENSE_CLASS_MATCH", Status.UNKNOWN, "License class is not recorded."));
      } else {
        boolean qualified = inspector.isLicenseClassQualifiedFor(drone.requiresClassBLicense());
        hardGates.add(
            check(
                "PILOT_LICENSE_CLASS_MATCH",
                qualified ? Status.PASS : Status.FAIL,
                qualified
                    ? "License class qualifies for drone MTOW."
                    : "Drone MTOW requires a higher license class."));
      }
    }

    hardGates.add(
        check(
            "CONTRACT_SCHEDULE_AVAILABLE",
            contractScheduleConflict ? Status.FAIL : Status.PASS,
            contractScheduleConflict
                ? "Inspector or drone has another contract at this time."
                : "No overlapping contract occurrence."));
    hardGates.add(
        check(
            "INSPECTION_SCHEDULE_AVAILABLE",
            inspectionScheduleConflict ? Status.FAIL : Status.PASS,
            inspectionScheduleConflict
                ? "Inspector or drone has an overlapping inspection."
                : "No overlapping inspection."));
    hardGates.add(
        check(
            "PARTNER_WINDOW_ELIGIBLE",
            qualifiesForWindow ? Status.PASS : Status.FAIL,
            qualifiesForWindow
                ? "Partner license and drone registration cover the requested schedule."
                : "Partner license or drone registration does not cover the requested schedule."));

    List<String> required =
        requiredFeatureCodes == null
            ? List.of()
            : requiredFeatureCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    Set<String> features =
        drone == null
            ? Set.of()
            : drone.getFeatures().stream()
                .filter(feature -> feature != null && !feature.isBlank())
                .map(feature -> feature.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    boolean hasKnownDeclaration = drone != null && !features.isEmpty();
    for (String code : required) {
      boolean declared =
          features.contains(code)
              || ("THERMAL_CAMERA".equals(code) && drone != null && drone.isHasThermalCamera())
              || ("RTK_POSITIONING".equals(code) && drone != null && drone.isHasRtk());
      Status status =
          drone == null
              ? Status.UNKNOWN
              : declared ? Status.MATCH : hasKnownDeclaration ? Status.MISMATCH : Status.UNKNOWN;
      capabilityChecks.add(
          check(
              code,
              status,
              declared
                  ? "Drone registration declares this capability."
                  : status == Status.UNKNOWN
                      ? "Capability declaration is not available."
                      : "Drone registration does not declare this capability."));
    }

    BigDecimal structureHeight = asset == null ? null : asset.getStructureHeightMeters();
    if (structureHeight != null) {
      BigDecimal maxAltitude = drone == null ? null : drone.getMaxAltitudeMeters();
      if (maxAltitude == null) {
        capabilityChecks.add(
            check(
                "DRONE_MAX_ALTITUDE",
                Status.UNKNOWN,
                "Drone maximum altitude is not recorded; structure-height fit needs verification."));
      } else {
        boolean reaches = maxAltitude.compareTo(structureHeight) >= 0;
        capabilityChecks.add(
            check(
                "DRONE_MAX_ALTITUDE",
                reaches ? Status.MATCH : Status.MISMATCH,
                "Declared drone maximum altitude ("
                    + plain(maxAltitude)
                    + " m) "
                    + (reaches ? "meets or exceeds" : "is below")
                    + " the asset structure height ("
                    + plain(structureHeight)
                    + " m). Approximate advisory comparison, not flight clearance."));
      }
    }

    if (required.isEmpty() && structureHeight == null) {
      capabilityChecks.add(
          check("NO_CAPABILITY_REQUIREMENTS", Status.MATCH, "No explicit capabilities required."));
    }

    return new Result(
        inspectorId,
        droneId,
        inspectorName,
        inspectorAvatarUrl,
        serialNumber,
        model,
        droneHasImage,
        registeredFeatureCodes(drone),
        summarizeHardGates(hardGates),
        summarizeCapabilityFit(capabilityChecks),
        drone == null ? null : drone.getMaxAltitudeMeters(),
        structureHeight,
        asset == null ? null : asset.getFootprintAreaSquareMeters(),
        hardGates,
        capabilityChecks,
        "Evaluation is advisory. It does not confirm a flight permit or clearance.");
  }

  private static List<String> registeredFeatureCodes(Drone drone) {
    if (drone == null) return List.of();
    Set<String> codes =
        drone.getFeatures().stream()
            .filter(code -> code != null && !code.isBlank())
            .map(code -> code.trim().toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());
    if (drone.isHasThermalCamera()) codes.add("THERMAL_CAMERA");
    if (drone.isHasRtk()) codes.add("RTK_POSITIONING");
    return codes.stream().sorted().toList();
  }

  private static Status summarizeHardGates(List<Check> checks) {
    if (checks.stream().anyMatch(check -> check.status() == Status.FAIL)) return Status.FAIL;
    if (checks.stream().anyMatch(check -> check.status() == Status.UNKNOWN)) return Status.UNKNOWN;
    return Status.PASS;
  }

  private static Status summarizeCapabilityFit(List<Check> checks) {
    if (checks.stream().anyMatch(check -> check.status() == Status.MISMATCH))
      return Status.MISMATCH;
    if (checks.stream().anyMatch(check -> check.status() == Status.UNKNOWN)) return Status.UNKNOWN;
    return Status.MATCH;
  }

  private static String plain(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private static Check check(String code, Status status, String explanation) {
    return new Check(code, status, explanation);
  }

  public enum Status {
    PASS,
    FAIL,
    UNKNOWN,
    MATCH,
    MISMATCH
  }

  public record Check(String code, Status status, String explanation) {}

  public record Result(
      UUID inspectorId,
      UUID droneId,
      String inspectorName,
      String inspectorAvatarUrl,
      String droneSerialNumber,
      String droneModel,
      boolean droneHasImage,
      List<String> droneFeatureCodes,
      Status eligibility,
      Status capabilityFit,
      BigDecimal droneMaxAltitudeMeters,
      BigDecimal structureHeightMeters,
      BigDecimal footprintAreaSquareMeters,
      List<Check> hardGates,
      List<Check> capabilityChecks,
      String disclaimer) {
    public Result {
      droneFeatureCodes = List.copyOf(droneFeatureCodes);
      hardGates = List.copyOf(hardGates);
      capabilityChecks = List.copyOf(capabilityChecks);
    }
  }
}

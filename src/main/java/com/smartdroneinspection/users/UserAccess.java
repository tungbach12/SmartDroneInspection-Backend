package com.smartdroneinspection.users;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public users-module boundary for feature authorization checks. */
public interface UserAccess {

  Optional<ActiveUser> findActiveUser(UUID userId);

  Optional<ActiveUser> findUser(UUID userId);

  default List<ActiveUser> listActiveInspectorPartners() {
    return List.of();
  }

  default List<ActiveUser> listActiveInspectors() {
    return listActiveInspectorPartners();
  }

  default List<ActiveUser> listActivePlatformManagers() {
    return List.of();
  }

  boolean isVisionFallbackEnabled(UUID userId);

  ProvisionedWorkforceMember provisionWorkforceUser(
      UUID actorId,
      String email,
      String fullName,
      Set<String> roles,
      String ipAddress,
      String userAgent,
      String correlationId);

  void recordSecurityAudit(
      UUID actorUserId,
      UUID aggregateId,
      String action,
      String afterStatus,
      String ipAddress,
      String userAgent,
      String correlationId);

  void recordInspectorFlightLog(
      UUID inspectorUserId,
      String flightCode,
      java.time.LocalDate flightDate,
      String missionTitle,
      String droneModel,
      String droneSerialNumber,
      String location,
      int flightDurationMinutes,
      String inspectionType,
      String outcomeNotes);

  record ActiveUser(
      UUID id,
      Set<String> roles,
      String actorZone,
      String fullName,
      String email,
      String pilotLicenseNumber,
      String pilotLicenseClass,
      java.time.Instant pilotLicenseExpiresAt,
      String pilotLicenseFileKey,
      String avatarUrl) {

    public ActiveUser(
        UUID id,
        Set<String> roles,
        String fullName,
        String email,
        String pilotLicenseNumber,
        String pilotLicenseClass,
        java.time.Instant pilotLicenseExpiresAt,
        String pilotLicenseFileKey) {
      this(
          id,
          roles,
          null,
          fullName,
          email,
          pilotLicenseNumber,
          pilotLicenseClass,
          pilotLicenseExpiresAt,
          pilotLicenseFileKey,
          null);
    }

    public ActiveUser(
        UUID id,
        Set<String> roles,
        String actorZone,
        String fullName,
        String email,
        String pilotLicenseNumber,
        String pilotLicenseClass,
        java.time.Instant pilotLicenseExpiresAt,
        String pilotLicenseFileKey) {
      this(
          id,
          roles,
          actorZone,
          fullName,
          email,
          pilotLicenseNumber,
          pilotLicenseClass,
          pilotLicenseExpiresAt,
          pilotLicenseFileKey,
          null);
    }

    public ActiveUser(
        UUID id,
        Set<String> roles,
        String actorZone,
        String pilotLicenseNumber,
        String pilotLicenseClass,
        java.time.LocalDate pilotLicenseExpiresAt,
        String pilotLicenseFileKey) {
      this(
          id,
          roles,
          actorZone,
          null,
          null,
          pilotLicenseNumber,
          pilotLicenseClass,
          pilotLicenseExpiresAt == null
              ? null
              : pilotLicenseExpiresAt.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
          pilotLicenseFileKey,
          null);
    }

    public ActiveUser(UUID id, Set<String> roles, String fullName, String email) {
      this(id, roles, null, fullName, email, null, null, null, null, null);
    }

    public ActiveUser(UUID id, Set<String> roles, String fullName) {
      this(id, roles, null, fullName, null, null, null, null, null, null);
    }

    public ActiveUser(UUID id, Set<String> roles) {
      this(id, roles, null, null, null, null, null, null, null, null);
    }

    public ActiveUser {
      roles = Set.copyOf(roles);
    }

    /**
     * Phase-2 bridge: the owning customer user for customer-scoped resources. A customer owns its
     * own data, so this is the user's own id; null for inspectors, managers, and admins.
     */
    public UUID ownerCustomerId() {
      return hasRole("CUSTOMER") ? id : null;
    }

    public boolean hasRole(String role) {
      if (roles.contains(role)) {
        return true;
      }
      if ("ORG_MANAGER".equals(role) || "ORG_ADMIN".equals(role)) {
        return roles.contains("CUSTOMER");
      }
      if ("PLATFORM_MANAGER".equals(role)) {
        return roles.contains("MANAGER");
      }
      return false;
    }

    public boolean hasValidPilotLicense(java.time.Instant at) {
      java.time.Instant checkTime = at != null ? at : java.time.Instant.now();
      return pilotLicenseNumber != null
          && !pilotLicenseNumber.isBlank()
          && (pilotLicenseExpiresAt == null || pilotLicenseExpiresAt.isAfter(checkTime));
    }

    /**
     * Checks whether the pilot's license class is qualified for a Category 3+ UAV (MTOW >= 2 kg)
     * under Decree 288/2025/ND-CP Article 27. Explicit Class A ("CLASS_A", "HANG_A", "HẠNG A") is
     * restricted to < 2 kg (Category 2 VLOS) and is rejected for MTOW >= 2 kg.
     */
    public boolean isLicenseClassQualifiedFor(boolean requiresClassB) {
      if (!requiresClassB || pilotLicenseClass == null || pilotLicenseClass.isBlank()) {
        return true;
      }
      String norm = pilotLicenseClass.trim().toUpperCase(java.util.Locale.ROOT);
      if (norm.contains("CLASS_B")
          || norm.contains("CLASS B")
          || norm.contains("HẠNG B")
          || norm.contains("HANG B")
          || norm.equals("B")) {
        return true;
      }
      if (norm.contains("CLASS_A")
          || norm.contains("CLASS A")
          || norm.contains("HẠNG A")
          || norm.contains("HANG A")
          || norm.equals("A")) {
        return false;
      }
      return true;
    }

    /**
     * Partner-inspector distinction for read authorization. Tenancy is now per-record: a partner
     * inspection is identified by {@code Inspection.partnerAssignment}, not by an org-coupled actor
     * zone. Any active Inspector may be assigned; the per-record flag separates partner reads.
     */
    public boolean isInspectorPartner() {
      return hasRole("INSPECTOR");
    }
  }

  /** A workforce user, free of persistence types. */
  record WorkforceMember(UUID id, String email, String fullName, Set<String> roles, String status) {

    public WorkforceMember {
      roles = Set.copyOf(roles);
    }
  }

  /**
   * A newly provisioned workforce user together with its one-time temporary password. The password
   * is returned once, at creation time only, and is never logged or persisted in plain text.
   */
  record ProvisionedWorkforceMember(WorkforceMember member, String temporaryPassword) {}
}

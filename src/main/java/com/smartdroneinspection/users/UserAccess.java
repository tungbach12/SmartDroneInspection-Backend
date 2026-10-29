package com.smartdroneinspection.users;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public users-module boundary for feature authorization checks. */
public interface UserAccess {

  Optional<ActiveUser> findActiveUser(UUID userId);

  Optional<String> findOrganizationName(UUID organizationId);

  Optional<String> findOrganizationCode(UUID organizationId);

  boolean organizationExists(UUID organizationId);

  boolean isVisionFallbackEnabled(UUID userId);

  Optional<WorkforceMember> findWorkforceMember(UUID userId, UUID organizationId);

  List<WorkforceMember> listWorkforceMembers(UUID organizationId);

  ProvisionedWorkforceMember provisionWorkforceUser(
      UUID actorId,
      UUID organizationId,
      String email,
      String fullName,
      Set<String> roles,
      String ipAddress,
      String userAgent,
      String correlationId);

  WorkforceMember updateWorkforceStatus(
      UUID actorId,
      UUID userId,
      String status,
      String ipAddress,
      String userAgent,
      String correlationId);

  WorkforceMember updateWorkforceRoles(
      UUID actorId,
      UUID userId,
      Set<String> roles,
      String ipAddress,
      String userAgent,
      String correlationId);

  record ActiveUser(
      UUID id,
      Set<String> roles,
      UUID organizationId,
      String actorZone,
      String fullName,
      String email,
      String pilotLicenseNumber,
      String pilotLicenseClass,
      java.time.Instant pilotLicenseExpiresAt,
      String pilotLicenseFileKey) {

    public ActiveUser(
        UUID id,
        Set<String> roles,
        UUID organizationId,
        String fullName,
        String email,
        String pilotLicenseNumber,
        String pilotLicenseClass,
        java.time.Instant pilotLicenseExpiresAt,
        String pilotLicenseFileKey) {
      this(
          id,
          roles,
          organizationId,
          null,
          fullName,
          email,
          pilotLicenseNumber,
          pilotLicenseClass,
          pilotLicenseExpiresAt,
          pilotLicenseFileKey);
    }

    public ActiveUser(
        UUID id,
        Set<String> roles,
        UUID organizationId,
        String actorZone,
        String pilotLicenseNumber,
        String pilotLicenseClass,
        java.time.LocalDate pilotLicenseExpiresAt,
        String pilotLicenseFileKey) {
      this(
          id,
          roles,
          organizationId,
          actorZone,
          null,
          null,
          pilotLicenseNumber,
          pilotLicenseClass,
          pilotLicenseExpiresAt == null
              ? null
              : pilotLicenseExpiresAt.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
          pilotLicenseFileKey);
    }

    public ActiveUser(
        UUID id, Set<String> roles, UUID organizationId, String fullName, String email) {
      this(id, roles, organizationId, null, fullName, email, null, null, null, null);
    }

    public ActiveUser(UUID id, Set<String> roles, UUID organizationId, String fullName) {
      this(id, roles, organizationId, null, fullName, null, null, null, null, null);
    }

    public ActiveUser(UUID id, Set<String> roles) {
      this(id, roles, null, null, null, null, null, null, null, null);
    }

    public ActiveUser(UUID id, Set<String> roles, UUID organizationId) {
      this(id, roles, organizationId, null, null, null, null, null, null, null);
    }

    public ActiveUser {
      roles = Set.copyOf(roles);
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
  }

  /** Organization-scoped projection of a workforce user, free of persistence types. */
  record WorkforceMember(
      UUID id,
      String email,
      String fullName,
      Set<String> roles,
      String status,
      UUID organizationId) {

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

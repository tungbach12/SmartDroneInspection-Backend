package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.api.dto.request.CreateUserRequest;
import com.smartdroneinspection.users.domain.InspectorProfile;
import com.smartdroneinspection.users.domain.RolePolicy;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.InspectorProfileRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccessService implements UserAccess {

  private final UserRepository users;
  private final AdminUserService adminUsers;
  private final RolePolicy rolePolicy;
  private final SecurityAuditService securityAudit;
  private final InspectorProfileService inspectorProfiles;
  private final InspectorProfileRepository inspectorProfileRepository;

  public UserAccessService(
      UserRepository users,
      AdminUserService adminUsers,
      RolePolicy rolePolicy,
      SecurityAuditService securityAudit,
      InspectorProfileService inspectorProfiles,
      InspectorProfileRepository inspectorProfileRepository) {
    this.users = users;
    this.adminUsers = adminUsers;
    this.rolePolicy = rolePolicy;
    this.securityAudit = securityAudit;
    this.inspectorProfiles = inspectorProfiles;
    this.inspectorProfileRepository = inspectorProfileRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ActiveUser> findActiveUser(UUID userId) {
    return users.findDetailedById(userId).filter(User::active).map(this::toActiveUser);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActiveUser> listActiveInspectorPartners() {
    return users.findActiveInspectorPartners().stream()
        .filter(User::active)
        .map(this::toActiveUser)
        .filter(ActiveUser::isInspectorPartner)
        .toList();
  }

  private ActiveUser toActiveUser(User user) {
    InspectorProfile profile = inspectorProfileRepository.findById(user.getId()).orElse(null);
    return new ActiveUser(
        user.getId(),
        Set.copyOf(user.roleValues()),
        user.getActorZone().name(),
        user.getFullName(),
        user.getEmail(),
        profile == null ? null : profile.getPilotLicenseNumber(),
        profile == null ? null : profile.getPilotLicenseClass(),
        profile == null ? null : profile.getPilotLicenseExpiresAt(),
        profile == null ? null : profile.getPilotLicenseFileKey(),
        user.getAvatarUrl());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ActiveUser> findUser(UUID userId) {
    return users.findDetailedById(userId).map(this::toActiveUser);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isVisionFallbackEnabled(UUID userId) {
    return users
        .findDetailedById(userId)
        .filter(User::active)
        .map(User::isVisionFallbackEnabled)
        .orElse(true);
  }

  @Override
  public ProvisionedWorkforceMember provisionWorkforceUser(
      UUID actorId,
      String email,
      String fullName,
      Set<String> roles,
      String ipAddress,
      String userAgent,
      String correlationId) {
    requireActiveOrganizationAdmin(actorId);
    if (!isWorkforceRoles(roles)) {
      throw workforceAccessDenied();
    }
    rolePolicy.validate(ActorZone.INSPECTOR, toEnumRoles(roles));
    var provisioned =
        adminUsers.create(
            actorId,
            new CreateUserRequest(email, fullName, ActorZone.INSPECTOR, toEnumRoles(roles)),
            ipAddress,
            userAgent,
            correlationId);
    return new ProvisionedWorkforceMember(
        new WorkforceMember(
            provisioned.user().id(),
            provisioned.user().email(),
            provisioned.user().fullName(),
            Set.copyOf(provisioned.user().roles()),
            UserStatus.ACTIVE.name()),
        provisioned.temporaryPassword());
  }

  @Override
  public void recordSecurityAudit(
      UUID actorUserId,
      UUID aggregateId,
      String action,
      String afterStatus,
      String ipAddress,
      String userAgent,
      String correlationId) {
    securityAudit.record(
        actorUserId, aggregateId, action, afterStatus, ipAddress, userAgent, correlationId);
  }

  @Override
  @Transactional
  public void recordInspectorFlightLog(
      UUID inspectorUserId,
      String flightCode,
      java.time.LocalDate flightDate,
      String missionTitle,
      String droneModel,
      String droneSerialNumber,
      String location,
      int flightDurationMinutes,
      String inspectionType,
      String outcomeNotes) {
    if (inspectorProfiles != null) {
      inspectorProfiles.recordAutomaticFlightSession(
          inspectorUserId,
          flightCode,
          flightDate,
          missionTitle,
          droneModel,
          droneSerialNumber,
          location,
          flightDurationMinutes,
          inspectionType,
          outcomeNotes);
    }
  }

  private static boolean isWorkforceRoles(Set<String> roles) {
    return roles != null && roles.size() == 1 && roles.contains(Roles.INSPECTOR);
  }

  /**
   * A workforce member is a plain Inspector account. Only an active platform administrator or
   * manager may provision or manage workforce users.
   */
  private User requireActiveOrganizationAdmin(UUID actorId) {
    User actor =
        users.findDetailedById(actorId).orElseThrow(UserAccessService::workforceAccessDenied);
    if (!actor.active()
        || actor.getActorZone() != ActorZone.PLATFORM
        || (!actor.roleValues().contains(Roles.ADMIN)
            && !actor.roleValues().contains(Roles.MANAGER))) {
      throw workforceAccessDenied();
    }
    return actor;
  }

  private static AuthException workforceAccessDenied() {
    return new AuthException(
        HttpStatus.FORBIDDEN,
        "WORKFORCE_ACCESS_DENIED",
        "Only an active platform administrator may manage workforce users.");
  }

  private static Set<UserRole> toEnumRoles(Set<String> roles) {
    return roles.stream()
        .map(UserRole::fromName)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }
}

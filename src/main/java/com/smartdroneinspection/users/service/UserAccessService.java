package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.api.dto.request.CreateUserRequest;
import com.smartdroneinspection.users.api.dto.request.UpdateRolesRequest;
import com.smartdroneinspection.users.domain.RolePolicy;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
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
  private final com.smartdroneinspection.users.repository.OrganizationRepository organizations;
  private final AdminUserService adminUsers;
  private final RolePolicy rolePolicy;

  public UserAccessService(
      UserRepository users,
      com.smartdroneinspection.users.repository.OrganizationRepository organizations,
      AdminUserService adminUsers,
      RolePolicy rolePolicy) {
    this.users = users;
    this.organizations = organizations;
    this.adminUsers = adminUsers;
    this.rolePolicy = rolePolicy;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> findOrganizationName(UUID organizationId) {
    return organizations.findById(organizationId).map(organization -> organization.getName());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> findOrganizationCode(UUID organizationId) {
    return organizations.findById(organizationId).map(organization -> organization.getCode());
  }

  @Override
  @Transactional(readOnly = true)
  public boolean organizationExists(UUID organizationId) {
    return organizations.existsById(organizationId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ActiveUser> findActiveUser(UUID userId) {
    return users
        .findDetailedById(userId)
        .filter(User::active)
        .map(
            user ->
                new ActiveUser(
                    user.getId(),
                    Set.copyOf(user.roleValues()),
                    user.getOrganizationId(),
                    user.getFullName(),
                    user.getEmail()));
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
  @Transactional(readOnly = true)
  public Optional<WorkforceMember> findWorkforceMember(UUID userId, UUID organizationId) {
    return users
        .findDetailedById(userId)
        .filter(user -> organizationId.equals(user.getOrganizationId()))
        .map(UserAccessService::toMember);
  }

  @Override
  @Transactional(readOnly = true)
  public List<WorkforceMember> listWorkforceMembers(UUID organizationId) {
    return users.findWorkforceByOrganizationId(organizationId).stream()
        .map(UserAccessService::toMember)
        .toList();
  }

  @Override
  public ProvisionedWorkforceMember provisionWorkforceUser(
      UUID actorId,
      UUID organizationId,
      String email,
      String fullName,
      Set<String> roles,
      String ipAddress,
      String userAgent,
      String correlationId) {
    User actor = requireActiveOrganizationAdmin(actorId);
    if (!actor.getOrganizationId().equals(organizationId) || !isWorkforceRoles(roles)) {
      throw workforceAccessDenied();
    }
    // A globally-unique email checked without an organization scope turns provisioning into an
    // oracle: 409 for "this address exists somewhere on the platform" versus 201 for "it does not"
    // would let one tenant enumerate another's users. A foreign address is answered exactly like a
    // missing user, so nothing about another tenant's roster is disclosed.
    requireEmailFreeWithinOrganization(email, organizationId);
    rolePolicy.validate(ActorZone.CUSTOMER_ORGANIZATION, organizationId, toEnumRoles(roles));
    var provisioned =
        adminUsers.create(
            actorId,
            new CreateUserRequest(
                email,
                fullName,
                ActorZone.CUSTOMER_ORGANIZATION,
                organizationId,
                toEnumRoles(roles)),
            ipAddress,
            userAgent,
            correlationId);
    return new ProvisionedWorkforceMember(
        new WorkforceMember(
            provisioned.user().id(),
            provisioned.user().email(),
            provisioned.user().fullName(),
            Set.copyOf(provisioned.user().roles()),
            UserStatus.ACTIVE.name(),
            provisioned.user().organizationId()),
        provisioned.temporaryPassword());
  }

  @Override
  public WorkforceMember updateWorkforceStatus(
      UUID actorId,
      UUID userId,
      String status,
      String ipAddress,
      String userAgent,
      String correlationId) {
    UUID organizationId = requireActiveOrganizationAdmin(actorId).getOrganizationId();
    requireSameOrganizationAsActor(organizationId, userId);
    requireWorkforceUser(userId, organizationId);
    var response =
        adminUsers.updateStatus(
            actorId, userId, UserStatus.valueOf(status), ipAddress, userAgent, correlationId);
    return new WorkforceMember(
        response.id(),
        response.email(),
        response.fullName(),
        Set.copyOf(response.roles()),
        status,
        response.organizationId());
  }

  @Override
  public WorkforceMember updateWorkforceRoles(
      UUID actorId,
      UUID userId,
      Set<String> roles,
      String ipAddress,
      String userAgent,
      String correlationId) {
    UUID organizationId = requireActiveOrganizationAdmin(actorId).getOrganizationId();
    if (!isWorkforceRoles(roles)) {
      throw workforceAccessDenied();
    }
    requireSameOrganizationAsActor(organizationId, userId);
    User subject = requireWorkforceUser(userId, organizationId);
    var response =
        adminUsers.updateRoles(
            actorId,
            userId,
            new UpdateRolesRequest(
                subject.getActorZone(), subject.getOrganizationId(), toEnumRoles(roles)),
            ipAddress,
            userAgent,
            correlationId);
    return new WorkforceMember(
        response.id(),
        response.email(),
        response.fullName(),
        Set.copyOf(response.roles()),
        subject.getStatus().name(),
        response.organizationId());
  }

  /**
   * Answers a duplicate email without disclosing another tenant's roster. Same-organization
   * duplication is a clear conflict the actor can act on; a foreign-tenant email is answered
   * exactly like a missing user.
   */
  private void requireEmailFreeWithinOrganization(String email, UUID organizationId) {
    String normalizedEmail = User.normalizeEmail(email);
    boolean belongsToAnotherOrganization =
        users
            .findByNormalizedEmail(normalizedEmail)
            .map(user -> !organizationId.equals(user.getOrganizationId()))
            .orElse(false);
    if (belongsToAnotherOrganization) {
      throw new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found.");
    }
  }

  private User requireWorkforceUser(UUID userId, UUID organizationId) {
    return users
        .findDetailedById(userId)
        .filter(user -> organizationId.equals(user.getOrganizationId()))
        .filter(user -> isWorkforceRoles(Set.copyOf(user.roleValues())))
        .orElseThrow(
            () ->
                new AuthException(
                    HttpStatus.NOT_FOUND, "WORKFORCE_USER_NOT_FOUND", "User was not found."));
  }

  private static boolean isWorkforceRoles(Set<String> roles) {
    return roles != null
        && !roles.isEmpty()
        && roles.stream()
            .allMatch(
                role -> Roles.INSPECTOR.equals(role) || Roles.MAINTENANCE_ENGINEER.equals(role));
  }

  /**
   * Fails closed when the target user is outside the acting user's organization, so a lifecycle
   * write can never cross a tenant boundary. The message is identical for a missing user and a
   * foreign one to avoid disclosing whether the foreign account exists.
   */
  private User requireActiveOrganizationAdmin(UUID actorId) {
    User actor =
        users.findDetailedById(actorId).orElseThrow(UserAccessService::workforceAccessDenied);
    if (!actor.active()
        || actor.getOrganizationId() == null
        || !actor.roleValues().contains(Roles.ORG_ADMIN)) {
      throw workforceAccessDenied();
    }
    return actor;
  }

  private static AuthException workforceAccessDenied() {
    return new AuthException(
        HttpStatus.FORBIDDEN,
        "WORKFORCE_ACCESS_DENIED",
        "Only an active organization administrator may manage workforce users.");
  }

  private void requireSameOrganizationAsActor(UUID organizationId, UUID userId) {
    boolean sameOrganization =
        users
            .findDetailedById(userId)
            .map(user -> organizationId.equals(user.getOrganizationId()))
            .orElse(false);
    if (!sameOrganization) {
      throw new AuthException(
          HttpStatus.NOT_FOUND, "WORKFORCE_USER_NOT_FOUND", "User was not found.");
    }
  }

  private static Set<UserRole> toEnumRoles(Set<String> roles) {
    return roles.stream()
        .map(UserRole::valueOf)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static WorkforceMember toMember(User user) {
    return new WorkforceMember(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        Set.copyOf(user.roleValues()),
        user.getStatus().name(),
        user.getOrganizationId());
  }
}

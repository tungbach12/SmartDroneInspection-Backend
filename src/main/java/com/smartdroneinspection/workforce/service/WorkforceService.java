package com.smartdroneinspection.workforce.service;

import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.SubscriptionEntitlement;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.workforce.api.dto.request.ProvisionWorkforceUserRequest;
import com.smartdroneinspection.workforce.api.dto.request.UpdateWorkforceRolesRequest;
import com.smartdroneinspection.workforce.api.dto.request.UpdateWorkforceStatusRequest;
import com.smartdroneinspection.workforce.api.dto.response.ProvisionedWorkforceUserResponse;
import com.smartdroneinspection.workforce.api.dto.response.WorkforceUserResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Customer workforce lifecycle, scoped to a single customer organization.
 *
 * <p>Every operation derives the tenant from the authenticated principal, never from request data,
 * and accepts only the two non-privileged workforce roles. Lifecycle writes are delegated to the
 * users module so password setup, audit attribution and session revocation stay in one place.
 */
@Service
public class WorkforceService {

  /** Customer organizations may provision their own maintenance engineers. */
  static final Set<String> GRANTABLE_ROLES = Set.of(Roles.MAINTENANCE_ENGINEER);

  private static final String NOT_FOUND_MESSAGE = "User was not found.";

  private final UserAccess users;
  private final SubscriptionEntitlement entitlement;

  public WorkforceService(UserAccess users, SubscriptionEntitlement entitlement) {
    this.users = users;
    this.entitlement = entitlement;
  }

  public ProvisionedWorkforceUserResponse provision(
      UUID actorId,
      ProvisionWorkforceUserRequest request,
      String ipAddress,
      String userAgent,
      String correlationId) {
    UUID organizationId = requireActorOrganization(actorId);
    requireGrantableRoles(request.roles());
    requireEntitled(organizationId);
    var provisioned =
        users.provisionWorkforceUser(
            actorId,
            organizationId,
            request.email(),
            request.fullName(),
            request.roles(),
            ipAddress,
            userAgent,
            correlationId);
    return new ProvisionedWorkforceUserResponse(
        WorkforceUserResponse.from(provisioned.member()), provisioned.temporaryPassword());
  }

  public List<WorkforceUserResponse> list(UUID actorId) {
    UUID organizationId = requireActorOrganization(actorId);
    return users.listWorkforceMembers(organizationId).stream()
        .map(WorkforceUserResponse::from)
        .toList();
  }

  public WorkforceUserResponse updateStatus(
      UUID actorId,
      UUID userId,
      UpdateWorkforceStatusRequest request,
      String ipAddress,
      String userAgent,
      String correlationId) {
    UUID organizationId = requireActorOrganization(actorId);
    requireWorkforceMemberOfActorOrganization(userId, organizationId);
    requireEntitled(organizationId);
    return WorkforceUserResponse.from(
        users.updateWorkforceStatus(
            actorId, userId, request.status(), ipAddress, userAgent, correlationId));
  }

  public WorkforceUserResponse updateRoles(
      UUID actorId,
      UUID userId,
      UpdateWorkforceRolesRequest request,
      String ipAddress,
      String userAgent,
      String correlationId) {
    UUID organizationId = requireActorOrganization(actorId);
    requireGrantableRoles(request.roles());
    requireWorkforceMemberOfActorOrganization(userId, organizationId);
    requireEntitled(organizationId);
    return WorkforceUserResponse.from(
        users.updateWorkforceRoles(
            actorId, userId, request.roles(), ipAddress, userAgent, correlationId));
  }

  /**
   * Resolves the acting Customer's organization from the users module. A suspended or disabled
   * actor is absent here and is rejected, and an actor without an organization cannot provision.
   */
  private UUID requireActorOrganization(UUID actorId) {
    UserAccess.ActiveUser actor =
        users
            .findActiveUser(actorId)
            .filter(candidate -> candidate.hasRole(Roles.CUSTOMER))
            .filter(candidate -> "CUSTOMER_ORGANIZATION".equals(candidate.actorZone()))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "WORKFORCE_ACCESS_DENIED",
                        "Only an active Customer may manage workforce users."));
    UUID organizationId = actor.organizationId();
    if (organizationId == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "WORKFORCE_ACCESS_DENIED",
          "Only an active Customer may manage workforce users.");
    }
    return organizationId;
  }

  /**
   * Fails closed for any user outside the actor's organization, and for any same-organization
   * account that is not a grantable workforce role. A Customer must not manage a privileged ADMIN
   * or Customer account here, so those are reported exactly like a missing user rather than
   * disclosing that a privileged account exists.
   */
  private void requireWorkforceMemberOfActorOrganization(UUID userId, UUID organizationId) {
    users
        .findWorkforceMember(userId, organizationId)
        .filter(
            candidate ->
                !candidate.roles().isEmpty() && GRANTABLE_ROLES.containsAll(candidate.roles()))
        .orElseThrow(
            () -> new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", NOT_FOUND_MESSAGE));
  }

  private void requireGrantableRoles(Set<String> roles) {
    if (roles == null || roles.isEmpty()) {
      throw new IllegalArgumentException("At least one role is required.");
    }
    List<String> disallowed =
        roles.stream().filter(role -> !GRANTABLE_ROLES.contains(role)).toList();
    if (!disallowed.isEmpty()) {
      throw new IllegalArgumentException(
          "Role is not grantable by an organization administrator: "
              + String.join(", ", disallowed));
    }
  }

  /**
   * Guards the tenant against writes while the organization is not entitled to MF1 capabilities.
   */
  private void requireEntitled(UUID organizationId) {
    entitlement.requireActive(organizationId, Instant.now());
  }
}

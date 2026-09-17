package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.CreateUserRequest;
import com.smartdroneinspection.users.api.dto.request.UpdateRolesRequest;
import com.smartdroneinspection.users.api.dto.response.ProvisionedUserResponse;
import com.smartdroneinspection.users.api.dto.response.UserResponse;
import com.smartdroneinspection.users.domain.RolePolicy;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.security.AuthCrypto;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

  private final UserRepository users;
  private final RolePolicy rolePolicy;
  private final PasswordEncoder passwords;
  private final AuthService auth;
  private final SecurityAuditService audit;

  public AdminUserService(
      UserRepository users,
      RolePolicy rolePolicy,
      PasswordEncoder passwords,
      AuthService auth,
      SecurityAuditService audit) {
    this.users = users;
    this.rolePolicy = rolePolicy;
    this.passwords = passwords;
    this.auth = auth;
    this.audit = audit;
  }

  @Transactional
  public ProvisionedUserResponse create(
      UUID actorId,
      CreateUserRequest request,
      String ipAddress,
      String userAgent,
      String correlationId) {
    rolePolicy.validate(request.actorZone(), request.organizationId(), request.roles());
    String normalizedEmail = User.normalizeEmail(request.email());
    if (users.findByNormalizedEmail(normalizedEmail).isPresent()) {
      throw new AuthException(
          HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already in use.");
    }
    String temporaryPassword = temporaryPassword();
    User user =
        new User(
            request.email().trim(),
            request.fullName().trim(),
            passwords.encode(temporaryPassword),
            UserStatus.ACTIVE,
            request.actorZone(),
            request.organizationId());
    user.requirePasswordChange();
    for (var role : request.roles()) {
      user.addRole(role);
    }
    user = users.save(user);
    audit.record(
        actorId, user.getId(), "USER_CREATED", "SUCCESS", ipAddress, userAgent, correlationId);
    return new ProvisionedUserResponse(toResponse(user), temporaryPassword);
  }

  @Transactional
  public ProvisionedUserResponse resetPassword(
      UUID actorId, UUID userId, String ipAddress, String userAgent, String correlationId) {
    requireDifferentUser(actorId, userId);
    User user = requireUser(userId);
    String temporaryPassword = temporaryPassword();
    user.setPasswordHash(passwords.encode(temporaryPassword));
    user.requirePasswordChange();
    user.incrementAuthVersion();
    auth.revokeAllSessions(userId, "administrator-password-reset");
    audit.record(
        actorId, userId, "USER_PASSWORD_RESET", "SUCCESS", ipAddress, userAgent, correlationId);
    return new ProvisionedUserResponse(toResponse(user), temporaryPassword);
  }

  @Transactional
  public UserResponse updateRoles(
      UUID actorId,
      UUID userId,
      UpdateRolesRequest request,
      String ipAddress,
      String userAgent,
      String correlationId) {
    requireDifferentUser(actorId, userId);
    rolePolicy.validate(request.actorZone(), request.organizationId(), request.roles());
    User user = requireUser(userId);
    user.updateAccessProfile(
        request.actorZone(), request.organizationId(), new ArrayList<>(request.roles()));
    auth.revokeAllSessions(userId, "roles-changed");
    audit.record(
        actorId, userId, "USER_ROLES_CHANGED", "SUCCESS", ipAddress, userAgent, correlationId);
    return toResponse(user);
  }

  @Transactional
  public UserResponse updateStatus(
      UUID actorId,
      UUID userId,
      UserStatus status,
      String ipAddress,
      String userAgent,
      String correlationId) {
    requireDifferentUser(actorId, userId);
    User user = requireUser(userId);
    user.disable(status);
    auth.revokeAllSessions(userId, "account-status-changed");
    audit.record(
        actorId, userId, "USER_STATUS_CHANGED", "SUCCESS", ipAddress, userAgent, correlationId);
    return toResponse(user);
  }

  private User requireUser(UUID userId) {
    return users
        .findDetailedById(userId)
        .orElseThrow(
            () -> new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found."));
  }

  private void requireDifferentUser(UUID actorId, UUID userId) {
    if (actorId.equals(userId)) {
      throw new AuthException(
          HttpStatus.CONFLICT,
          "SELF_ADMINISTRATION_NOT_ALLOWED",
          "Use the current-user endpoints for your own account.");
    }
  }

  private String temporaryPassword() {
    return AuthCrypto.randomToken();
  }

  private UserResponse toResponse(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        user.roleValues(),
        user.getActorZone().name(),
        user.getOrganizationId());
  }
}

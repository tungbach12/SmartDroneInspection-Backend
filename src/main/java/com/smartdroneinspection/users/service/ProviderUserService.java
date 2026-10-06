package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.ProviderStaffRequest;
import com.smartdroneinspection.users.api.dto.response.ProvisionedUserResponse;
import com.smartdroneinspection.users.api.dto.response.UserResponse;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderUserService {

  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final SecurityAuditService audit;

  public ProviderUserService(
      UserRepository users, PasswordEncoder passwords, SecurityAuditService audit) {
    this.users = users;
    this.passwords = passwords;
    this.audit = audit;
  }

  @Transactional
  public ProvisionedUserResponse createStaffUser(
      UUID managerId,
      ProviderStaffRequest request,
      String ipAddress,
      String userAgent,
      String correlationId) {
    User manager =
        users
            .findDetailedById(managerId)
            .orElseThrow(
                () ->
                    new AuthException(
                        HttpStatus.FORBIDDEN, "NOT_PROVIDER_MANAGER", "Not a provider manager."));
    boolean isManager =
        manager.getStatus() == UserStatus.ACTIVE
            && manager.getActorZone() == ActorZone.SERVICE_WORKFORCE
            && manager.getProviderId() != null
            && manager.roleValues().contains(UserRole.PROVIDER_MANAGER.value());
    if (!isManager) {
      throw new AuthException(
          HttpStatus.FORBIDDEN, "NOT_PROVIDER_MANAGER", "Not a provider manager.");
    }

    if (request.role() != UserRole.INSPECTOR && request.role() != UserRole.MAINTENANCE_ENGINEER) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST,
          "INVALID_STAFF_ROLE",
          "Role must be INSPECTOR or MAINTENANCE_ENGINEER.");
    }

    String normalizedEmail = User.normalizeEmail(request.email());
    if (users.findByNormalizedEmail(normalizedEmail).isPresent()) {
      throw new AuthException(
          HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already in use.");
    }

    String temporaryPassword = com.smartdroneinspection.users.security.AuthCrypto.randomToken();
    User staff =
        new User(
            request.email().trim(),
            request.fullName().trim(),
            passwords.encode(temporaryPassword),
            UserStatus.ACTIVE,
            ActorZone.SERVICE_WORKFORCE,
            null,
            manager.getProviderId());
    staff.requirePasswordChange();
    staff.addRole(request.role());
    staff = users.saveAndFlush(staff);

    audit.record(
        managerId,
        staff.getId(),
        "PROVIDER_STAFF_CREATED",
        "SUCCESS",
        ipAddress,
        userAgent,
        correlationId);

    return new ProvisionedUserResponse(
        new UserResponse(
            staff.getId(),
            staff.getEmail(),
            staff.getFullName(),
            staff.roleValues(),
            staff.getActorZone().name(),
            staff.getOrganizationId(),
            staff.getProviderId()),
        temporaryPassword);
  }
}

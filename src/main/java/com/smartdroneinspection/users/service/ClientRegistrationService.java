package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.ClientRegistrationRequest;
import com.smartdroneinspection.users.api.dto.response.ClientRegistrationResponse;
import com.smartdroneinspection.users.api.dto.response.UserResponse;
import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientRegistrationService {

  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final PasswordPolicy passwordPolicy;
  private final SecurityAuditService audit;

  public ClientRegistrationService(
      OrganizationRepository organizations,
      UserRepository users,
      PasswordEncoder passwords,
      PasswordPolicy passwordPolicy,
      SecurityAuditService audit) {
    this.organizations = organizations;
    this.users = users;
    this.passwords = passwords;
    this.passwordPolicy = passwordPolicy;
    this.audit = audit;
  }

  @Transactional
  public ClientRegistrationResponse register(
      ClientRegistrationRequest request, String ipAddress, String userAgent, String correlationId) {
    String email = User.normalizeEmail(request.email());
    String fullName = request.fullName().trim();
    String organizationName = request.organizationName().trim();
    String organizationCode = Organization.normalizeCode(request.organizationCode());

    if (users.findByNormalizedEmail(email).isPresent()) {
      throw new AuthException(
          HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already in use.");
    }
    if (organizations.findByCodeIgnoreCase(organizationCode).isPresent()) {
      throw new AuthException(
          HttpStatus.CONFLICT,
          "ORGANIZATION_CODE_ALREADY_EXISTS",
          "Organization code is already in use.");
    }

    passwordPolicy.validate(request.password(), email, fullName);

    Organization organization =
        organizations.save(new Organization(organizationName, organizationCode, null));
    User client =
        new User(
            email,
            fullName,
            passwords.encode(request.password()),
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organization.getId());
    client.addRole(UserRole.CLIENT);
    // Flush before the audit insert: SecurityAuditService writes raw SQL with an FK to users.
    client = users.saveAndFlush(client);

    audit.record(
        null,
        client.getId(),
        "CLIENT_REGISTRATION",
        "SUCCESS",
        ipAddress,
        userAgent,
        correlationId);

    return new ClientRegistrationResponse(
        organization.getId(),
        organization.getName(),
        organization.getCode(),
        new UserResponse(
            client.getId(),
            client.getEmail(),
            client.getFullName(),
            client.roleValues(),
            client.getActorZone().name(),
            client.getOrganizationId()));
  }
}

package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.OrganizationRegistrationRequest;
import com.smartdroneinspection.users.api.dto.response.OrganizationRegistrationResponse;
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
public class OrganizationRegistrationService {

  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final PasswordPolicy passwordPolicy;
  private final SecurityAuditService audit;

  public OrganizationRegistrationService(
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
  public OrganizationRegistrationResponse register(
      OrganizationRegistrationRequest request,
      String ipAddress,
      String userAgent,
      String correlationId) {
    String email = User.normalizeEmail(request.email());
    String fullName = request.fullName().trim();
    String organizationName = request.organizationName().trim();
    String organizationCode = Organization.normalizeCode(request.organizationCode());

    if (users.findByNormalizedEmail(email).isPresent()) {
      throw new AuthException(
          HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already in use.");
    }
    if (organizations.findByRegistrationCodeIgnoreCase(organizationCode).isPresent()) {
      throw new AuthException(
          HttpStatus.CONFLICT,
          "ORGANIZATION_CODE_ALREADY_EXISTS",
          "Organization code is already in use.");
    }
    passwordPolicy.validate(request.password(), email, fullName);

    Organization organization =
        organizations.save(new Organization(organizationName, organizationCode, null));
    User orgAdmin =
        new User(
            email,
            fullName,
            passwords.encode(request.password()),
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organization.getId());
    orgAdmin.addRole(UserRole.ORG_ADMIN);
    orgAdmin = users.saveAndFlush(orgAdmin);

    organization.attributeToCreator(orgAdmin.getId());
    organizations.saveAndFlush(organization);

    audit.record(
        null,
        orgAdmin.getId(),
        "ORGANIZATION_REGISTRATION",
        "SUCCESS",
        ipAddress,
        userAgent,
        correlationId);

    return new OrganizationRegistrationResponse(
        organization.getId(),
        organization.getName(),
        organization.getCode(),
        new UserResponse(
            orgAdmin.getId(),
            orgAdmin.getEmail(),
            orgAdmin.getFullName(),
            orgAdmin.roleValues(),
            orgAdmin.getActorZone().name(),
            orgAdmin.getOrganizationId()));
  }
}

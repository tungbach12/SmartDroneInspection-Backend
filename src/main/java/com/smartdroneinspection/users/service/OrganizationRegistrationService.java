package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.api.CustomerAirspaceEvaluation;
import com.smartdroneinspection.shared.api.CustomerAirspacePolicy;
import com.smartdroneinspection.shared.api.CustomerAirspaceStatus;
import com.smartdroneinspection.shared.api.CustomerRegistrationEvent;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.OrganizationRegistrationRequest;
import com.smartdroneinspection.users.api.dto.response.OrganizationRegistrationResponse;
import com.smartdroneinspection.users.api.dto.response.UserResponse;
import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.ProspectiveCustomerRegistration;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.ProspectiveCustomerRegistrationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationRegistrationService {

  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final ProspectiveCustomerRegistrationRepository prospectiveRegistrations;
  private final org.springframework.context.ApplicationEventPublisher events;
  private final CustomerAirspacePolicy airspace;
  private final PasswordEncoder passwords;
  private final PasswordPolicy passwordPolicy;
  private final SecurityAuditService audit;

  public OrganizationRegistrationService(
      OrganizationRepository organizations,
      UserRepository users,
      ProspectiveCustomerRegistrationRepository prospectiveRegistrations,
      org.springframework.context.ApplicationEventPublisher events,
      CustomerAirspacePolicy airspace,
      PasswordEncoder passwords,
      PasswordPolicy passwordPolicy,
      SecurityAuditService audit) {
    this.organizations = organizations;
    this.users = users;
    this.prospectiveRegistrations = prospectiveRegistrations;
    this.events = events;
    this.airspace = airspace;
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
    var firstAsset = request.firstAsset();
    if (firstAsset.latitude() == null || firstAsset.longitude() == null) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST,
          "ASSET_COORDINATES_REQUIRED",
          "The first asset requires latitude and longitude for airspace review.");
    }

    CustomerAirspaceEvaluation evaluation =
        airspace.evaluateCustomerOnboarding(firstAsset.latitude(), firstAsset.longitude());
    if (evaluation.status() == CustomerAirspaceStatus.NO_FLY_ZONE) {
      throw new AuthException(
          HttpStatus.FORBIDDEN,
          "ASSET_IN_NO_FLY_ZONE",
          "Customer registration is not allowed for an asset in a no-fly zone.");
    }
    boolean requiresAirspaceReview = evaluation.status() != CustomerAirspaceStatus.CLEAR_ZONE;

    if (users.findByNormalizedEmail(email).isPresent()) {
      throw new AuthException(
          HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already in use.");
    }
    if (prospectiveRegistrations.existsByOrganizationCodeIgnoreCaseAndStatus(
        organizationCode, "PENDING")) {
      throw new AuthException(
          HttpStatus.CONFLICT,
          "ORGANIZATION_CODE_ALREADY_EXISTS",
          "Organization code is already reserved by a pending registration.");
    }
    if (organizations.findByRegistrationCodeIgnoreCase(organizationCode).isPresent()
        || prospectiveRegistrations.existsByOrganizationCodeIgnoreCaseAndStatus(
            organizationCode, "PENDING")) {
      throw new AuthException(
          HttpStatus.CONFLICT,
          "ORGANIZATION_CODE_ALREADY_EXISTS",
          "Organization code is already in use.");
    }
    passwordPolicy.validate(request.password(), email, fullName);
    if (!requiresAirspaceReview) {
      validateFirstAsset(request.firstAsset());
    }

    if (requiresAirspaceReview) {
      User prospect =
          new User(
              email,
              fullName,
              passwords.encode(request.password()),
              UserStatus.DISABLED,
              ActorZone.PROSPECT_CUSTOMER,
              null);
      prospect.addRole(UserRole.CUSTOMER);
      prospect = users.saveAndFlush(prospect);
      ProspectiveCustomerRegistration registration =
          prospectiveRegistrations.saveAndFlush(
              new ProspectiveCustomerRegistration(
                  prospect.getId(),
                  organizationName,
                  organizationCode,
                  firstAsset.categoryId(),
                  firstAsset.code().trim().toUpperCase(java.util.Locale.ROOT),
                  firstAsset.name().trim(),
                  firstAsset.description(),
                  firstAsset.locationText().trim(),
                  firstAsset.latitude(),
                  firstAsset.longitude(),
                  firstAsset.ownershipInformation(),
                  evaluation.status().name()));
      audit.record(
          null,
          prospect.getId(),
          "CUSTOMER_AIRSPACE_REVIEW_REQUESTED",
          "SUCCESS",
          ipAddress,
          userAgent,
          correlationId);
      return new OrganizationRegistrationResponse(
          null,
          organizationName,
          organizationCode,
          new UserResponse(
              prospect.getId(),
              prospect.getEmail(),
              prospect.getFullName(),
              prospect.roleValues(),
              prospect.getActorZone().name(),
              prospect.getOrganizationId()),
          "PENDING_AIRSPACE_REVIEW",
          registration.getId(),
          evaluation.status().name(),
          evaluation.message());
    }

    Organization organization =
        organizations.save(new Organization(organizationName, organizationCode, null));
    User customer =
        new User(
            email,
            fullName,
            passwords.encode(request.password()),
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organization.getId());
    customer.addRole(UserRole.CUSTOMER);
    customer = users.saveAndFlush(customer);

    organization.attributeToCreator(customer.getId());
    organizations.saveAndFlush(organization);
    events.publishEvent(
        new CustomerRegistrationEvent(
            organization.getId(),
            customer.getId(),
            firstAsset.categoryId(),
            firstAsset.code(),
            firstAsset.name(),
            firstAsset.description(),
            firstAsset.locationText(),
            firstAsset.latitude(),
            firstAsset.longitude(),
            firstAsset.ownershipInformation()));

    audit.record(
        null,
        customer.getId(),
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
            customer.getId(),
            customer.getEmail(),
            customer.getFullName(),
            customer.roleValues(),
            customer.getActorZone().name(),
            customer.getOrganizationId()));
  }

  private void validateFirstAsset(
      com.smartdroneinspection.users.api.dto.request.FirstAssetRegistrationRequest firstAsset) {
    // Validate catalog and asset code before creating identity rows. The asset module repeats the
    // category check in its transaction-bound listener before commit.
    if (firstAsset.code().isBlank() || firstAsset.name().isBlank()) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST, "INVALID_FIRST_ASSET", "First asset code and name are required.");
    }
  }
}

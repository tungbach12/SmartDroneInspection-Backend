package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.api.CustomerAirspaceEvaluation;
import com.smartdroneinspection.shared.api.CustomerAirspacePolicy;
import com.smartdroneinspection.shared.api.CustomerAirspaceStatus;
import com.smartdroneinspection.shared.api.CustomerRegistrationEvent;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.InspectorRegistrationRequest;
import com.smartdroneinspection.users.api.dto.request.OrganizationRegistrationRequest;
import com.smartdroneinspection.users.api.dto.response.OrganizationRegistrationResponse;
import com.smartdroneinspection.users.api.dto.response.UserResponse;
import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.ProspectiveCustomerRegistration;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.InspectorProfileRepository;
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
  private final InspectorProfileRepository inspectorProfiles;
  private final ProspectiveCustomerRegistrationRepository prospectiveRegistrations;
  private final org.springframework.context.ApplicationEventPublisher events;
  private final CustomerAirspacePolicy airspace;
  private final PasswordEncoder passwords;
  private final PasswordPolicy passwordPolicy;
  private final SecurityAuditService audit;
  private final RegistrationOtpService registrationOtp;
  private final PilotLicenseClassService pilotLicenseClasses;
  private final InspectorApplicationService inspectorApplications;

  @org.springframework.beans.factory.annotation.Autowired
  public OrganizationRegistrationService(
      OrganizationRepository organizations,
      UserRepository users,
      InspectorProfileRepository inspectorProfiles,
      ProspectiveCustomerRegistrationRepository prospectiveRegistrations,
      org.springframework.context.ApplicationEventPublisher events,
      CustomerAirspacePolicy airspace,
      PasswordEncoder passwords,
      PasswordPolicy passwordPolicy,
      SecurityAuditService audit,
      RegistrationOtpService registrationOtp,
      PilotLicenseClassService pilotLicenseClasses,
      InspectorApplicationService inspectorApplications) {
    this.organizations = organizations;
    this.users = users;
    this.inspectorProfiles = inspectorProfiles;
    this.prospectiveRegistrations = prospectiveRegistrations;
    this.events = events;
    this.airspace = airspace;
    this.passwords = passwords;
    this.passwordPolicy = passwordPolicy;
    this.audit = audit;
    this.registrationOtp = registrationOtp;
    this.pilotLicenseClasses = pilotLicenseClasses;
    this.inspectorApplications = inspectorApplications;
  }

  public OrganizationRegistrationService(
      OrganizationRepository organizations,
      UserRepository users,
      ProspectiveCustomerRegistrationRepository prospectiveRegistrations,
      org.springframework.context.ApplicationEventPublisher events,
      CustomerAirspacePolicy airspace,
      PasswordEncoder passwords,
      PasswordPolicy passwordPolicy,
      SecurityAuditService audit,
      RegistrationOtpService registrationOtp) {
    this(
        organizations,
        users,
        null,
        prospectiveRegistrations,
        events,
        airspace,
        passwords,
        passwordPolicy,
        audit,
        registrationOtp,
        null,
        null);
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
    boolean requiresAirspaceReview = false;
    CustomerAirspaceEvaluation evaluation = null;

    if (firstAsset != null) {
      if (firstAsset.latitude() == null || firstAsset.longitude() == null) {
        throw new AuthException(
            HttpStatus.BAD_REQUEST,
            "ASSET_COORDINATES_REQUIRED",
            "The first asset requires latitude and longitude for airspace review.");
      }

      evaluation =
          airspace.evaluateCustomerOnboarding(firstAsset.latitude(), firstAsset.longitude());
      if (evaluation.status() == CustomerAirspaceStatus.NO_FLY_ZONE) {
        throw new AuthException(
            HttpStatus.FORBIDDEN,
            "ASSET_IN_NO_FLY_ZONE",
            "Customer registration is not allowed for an asset in a no-fly zone.");
      }
      requiresAirspaceReview = evaluation.status() != CustomerAirspaceStatus.CLEAR_ZONE;
    }

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
    if (registrationOtp.isReady()) {
      if (request.otpChallengeToken() == null
          || request.otpChallengeToken().isBlank()
          || request.otpCode() == null
          || request.otpCode().isBlank()) {
        throw new AuthException(
            HttpStatus.BAD_REQUEST,
            "OTP_REQUIRED",
            "Mã xác thực OTP là bắt buộc để đăng ký tài khoản.");
      }
      registrationOtp.verify(request.otpChallengeToken(), request.otpCode(), email);
    }
    if (!requiresAirspaceReview && firstAsset != null) {
      validateFirstAsset(request.firstAsset());
    }

    if (requiresAirspaceReview) {
      User prospect =
          new User(
              email,
              fullName,
              request.phoneNumber() != null ? request.phoneNumber().trim() : null,
              null,
              passwords.encode(request.password()),
              UserStatus.DISABLED,
              ActorZone.PROSPECT_CUSTOMER,
              null);
      prospect.setOtpVerified(true);
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
            request.phoneNumber() != null ? request.phoneNumber().trim() : null,
            null,
            passwords.encode(request.password()),
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organization.getId());
    customer.setOtpVerified(true);
    customer.addRole(UserRole.CUSTOMER);
    customer = users.saveAndFlush(customer);

    organizations.saveAndFlush(organization);
    if (firstAsset != null) {
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
    }

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

  @Transactional
  public UserResponse registerInspector(
      InspectorRegistrationRequest request,
      String ipAddress,
      String userAgent,
      String correlationId) {
    throw new AuthException(
        HttpStatus.GONE,
        "INSPECTOR_REGISTRATION_REPLACED",
        "Use the Inspector application workflow; accounts are created after Manager approval.");
  }

  @Transactional
  public com.smartdroneinspection.users.api.dto.response.InspectorApplicationResponse
      submitInspectorApplication(
          com.smartdroneinspection.users.api.dto.request.InspectorApplicationSubmissionRequest
              request,
          String ipAddress,
          String userAgent,
          String correlationId) {
    if (inspectorApplications == null) {
      throw new IllegalStateException("Inspector application workflow is not configured");
    }
    return inspectorApplications.submit(request, ipAddress, userAgent, correlationId);
  }

  /* retired direct user creation path
  @Transactional
  public UserResponse registerInspector(
      InspectorRegistrationRequest request,
      String ipAddress,
      String userAgent,
      String correlationId) {
    String email = User.normalizeEmail(request.email());
    String fullName = request.fullName().trim();

    if (users.findByNormalizedEmail(email).isPresent()) {
      throw new AuthException(
          HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already in use.");
    }
    if (pilotLicenseClasses == null) {
      throw new IllegalStateException("Pilot license class catalog is not configured");
    }
    String pilotLicenseClass = pilotLicenseClasses.requireActiveName(request.pilotLicenseClass());
    passwordPolicy.validate(request.password(), email, fullName);
    if (registrationOtp.isReady()) {
      if (request.otpChallengeToken() == null
          || request.otpChallengeToken().isBlank()
          || request.otpCode() == null
          || request.otpCode().isBlank()) {
        throw new AuthException(
            HttpStatus.BAD_REQUEST,
            "OTP_REQUIRED",
            "Mã xác thực OTP là bắt buộc để đăng ký tài khoản.");
      }
      registrationOtp.verify(request.otpChallengeToken(), request.otpCode(), email);
    }

    User inspector =
        new User(
            email,
            fullName,
            request.phoneNumber() != null && !request.phoneNumber().isBlank()
                ? request.phoneNumber().trim()
                : null,
            null,
            passwords.encode(request.password()),
            UserStatus.ACTIVE,
            ActorZone.INSPECTOR_PARTNER,
            null);
    inspector.setOtpVerified(true);
    inspector.addRole(UserRole.INSPECTOR);
    inspector = users.saveAndFlush(inspector);

    if (inspectorProfiles != null) {
      InspectorProfile profile = new InspectorProfile(inspector.getId());
      profile.setOnboardingStatus("PENDING_REVIEW");
      profile.updatePersonalDetails(
          request.citizenId() != null && !request.citizenId().isBlank()
              ? request.citizenId().trim()
              : null,
          null,
          request.address() != null && !request.address().isBlank()
              ? request.address().trim()
              : null,
          null);
      if (request.pilotLicenseNumber() != null && !request.pilotLicenseNumber().isBlank()) {
        profile.updatePilotLicense(
            request.pilotLicenseNumber().trim(),
            pilotLicenseClass,
            request.pilotLicenseExpiresAt(),
            null);
      }
      inspectorProfiles.saveAndFlush(profile);
    }

    audit.record(
        null,
        inspector.getId(),
        "INSPECTOR_PARTNER_SELF_REGISTRATION",
        "SUCCESS",
        ipAddress,
        userAgent,
        correlationId);

    return new UserResponse(
        inspector.getId(),
        inspector.getEmail(),
        inspector.getFullName(),
        inspector.getPhoneNumber(),
        inspector.getAvatarUrl(),
        inspector.roleValues(),
        inspector.getActorZone().name(),
        inspector.getOrganizationId(),
        inspector.isVisionFallbackEnabled(),
        request.pilotLicenseNumber() != null && !request.pilotLicenseNumber().isBlank()
            ? request.pilotLicenseNumber().trim()
            : null,
        pilotLicenseClass,
        request.pilotLicenseExpiresAt(),
        null);
  }

  */

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

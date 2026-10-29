package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.api.CustomerAirspacePolicy;
import com.smartdroneinspection.shared.api.CustomerAirspaceStatus;
import com.smartdroneinspection.shared.api.CustomerRegistrationEvent;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.RejectProspectiveCustomerRequest;
import com.smartdroneinspection.users.api.dto.response.ProspectiveCustomerResponse;
import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.ProspectiveCustomerRegistration;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.ProspectiveCustomerRegistrationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProspectiveCustomerReviewService {
  private final ProspectiveCustomerRegistrationRepository registrations;
  private final UserRepository users;
  private final OrganizationRepository organizations;
  private final org.springframework.context.ApplicationEventPublisher events;
  private final CustomerAirspacePolicy airspace;
  private final SecurityAuditService audit;

  public ProspectiveCustomerReviewService(
      ProspectiveCustomerRegistrationRepository registrations,
      UserRepository users,
      OrganizationRepository organizations,
      org.springframework.context.ApplicationEventPublisher events,
      CustomerAirspacePolicy airspace,
      SecurityAuditService audit) {
    this.registrations = registrations;
    this.users = users;
    this.organizations = organizations;
    this.events = events;
    this.airspace = airspace;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<ProspectiveCustomerResponse> pending(UUID adminId) {
    requireAdmin(adminId);
    return registrations.findAllByStatusOrderByCreatedAtAsc("PENDING").stream()
        .map(ProspectiveCustomerResponse::from)
        .toList();
  }

  @Transactional
  public ProspectiveCustomerResponse approve(
      UUID adminId,
      UUID registrationId,
      com.smartdroneinspection.users.api.dto.request.ApproveProspectiveCustomerRequest request,
      String ip,
      String agent,
      String correlationId) {
    requireAdmin(adminId);
    ProspectiveCustomerRegistration registration = requirePending(registrationId);
    User prospect = users.findDetailedById(registration.getUserId()).orElseThrow(this::notFound);
    if (prospect.getActorZone() != ActorZone.PROSPECT_CUSTOMER
        || prospect.getStatus() != com.smartdroneinspection.users.domain.enums.UserStatus.DISABLED
        || !prospect.roleValues().contains("CUSTOMER")) {
      throw new AuthException(
          HttpStatus.CONFLICT,
          "INVALID_REVIEW_STATE",
          "Prospective user is not awaiting approval.");
    }
    var evaluation =
        airspace.evaluateCustomerOnboarding(
            registration.getLatitude(), registration.getLongitude());
    if (evaluation.status() == CustomerAirspaceStatus.NO_FLY_ZONE) {
      throw new AuthException(
          HttpStatus.FORBIDDEN,
          "ASSET_IN_NO_FLY_ZONE",
          "A no-fly zone registration cannot be approved.");
    }
    if (evaluation.status() != CustomerAirspaceStatus.CLEAR_ZONE
        && (request.reviewNote() == null || request.reviewNote().isBlank())) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST,
          "AIRSPACE_REVIEW_NOTE_REQUIRED",
          "Manual review details are required for non-clear or unavailable airspace data.");
    }
    if (organizations
        .findByRegistrationCodeIgnoreCase(registration.getOrganizationCode())
        .isPresent()) {
      throw new AuthException(
          HttpStatus.CONFLICT,
          "ORGANIZATION_CODE_ALREADY_EXISTS",
          "Organization code is already in use.");
    }
    if (registrations.existsByOrganizationCodeIgnoreCaseAndAssetCodeIgnoreCaseAndStatus(
        registration.getOrganizationCode(), registration.getAssetCode(), "APPROVED")) {
      throw new AuthException(
          HttpStatus.CONFLICT, "ASSET_CODE_ALREADY_EXISTS", "Asset code is already registered.");
    }
    Organization organization =
        organizations.saveAndFlush(
            new Organization(
                registration.getOrganizationName(), registration.getOrganizationCode(), null));
    prospect.approveAsCustomer(organization.getId());
    organization.attributeToCreator(prospect.getId());
    organizations.saveAndFlush(organization);
    events.publishEvent(
        new CustomerRegistrationEvent(
            organization.getId(),
            prospect.getId(),
            registration.getAssetCategoryId(),
            registration.getAssetCode(),
            registration.getAssetName(),
            registration.getAssetDescription(),
            registration.getAssetLocation(),
            registration.getLatitude(),
            registration.getLongitude(),
            registration.getOwnershipInformation()));
    registration.approve(adminId, request.reviewNote());
    registrations.saveAndFlush(registration);
    audit.record(
        adminId,
        prospect.getId(),
        "CUSTOMER_AIRSPACE_REVIEW_APPROVED",
        "SUCCESS",
        ip,
        agent,
        correlationId);
    return ProspectiveCustomerResponse.from(registration);
  }

  @Transactional
  public ProspectiveCustomerResponse reject(
      UUID adminId,
      UUID registrationId,
      RejectProspectiveCustomerRequest request,
      String ip,
      String agent,
      String correlationId) {
    requireAdmin(adminId);
    ProspectiveCustomerRegistration registration = requirePending(registrationId);
    users
        .findDetailedById(registration.getUserId())
        .ifPresent(user -> user.disable(UserStatus.DISABLED));
    registration.reject(adminId, request.reviewNote().trim());
    registrations.saveAndFlush(registration);
    audit.record(
        adminId,
        registration.getUserId(),
        "CUSTOMER_AIRSPACE_REVIEW_REJECTED",
        "SUCCESS",
        ip,
        agent,
        correlationId);
    return ProspectiveCustomerResponse.from(registration);
  }

  private void requireAdmin(UUID adminId) {
    User admin = users.findDetailedById(adminId).filter(User::active).orElseThrow(this::notFound);
    if (admin.getActorZone() != ActorZone.PLATFORM || !admin.roleValues().contains("ADMIN")) {
      throw new AuthException(
          HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Platform Admin access is required.");
    }
  }

  private ProspectiveCustomerRegistration requirePending(UUID id) {
    return registrations.findByIdAndStatus(id, "PENDING").orElseThrow(this::notFound);
  }

  private AuthException notFound() {
    return new AuthException(
        HttpStatus.NOT_FOUND, "REGISTRATION_NOT_FOUND", "Pending registration was not found.");
  }
}

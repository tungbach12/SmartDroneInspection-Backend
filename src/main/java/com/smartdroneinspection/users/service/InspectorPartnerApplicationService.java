package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.ReviewInspectorPartnerApplicationRequest;
import com.smartdroneinspection.users.api.dto.response.InspectorPartnerApplicationResponse;
import com.smartdroneinspection.users.api.dto.response.InspectorPartnerApprovalResponse;
import com.smartdroneinspection.users.domain.InspectorPartnerApplication;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.InspectorPartnerApplicationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.security.AuthCrypto;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectorPartnerApplicationService {
  private final InspectorPartnerApplicationRepository applications;
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final SecurityAuditService audit;
  private final InspectorPartnerLicenseDocumentService licenseDocuments;

  public InspectorPartnerApplicationService(
      InspectorPartnerApplicationRepository applications,
      UserRepository users,
      PasswordEncoder passwords,
      SecurityAuditService audit,
      InspectorPartnerLicenseDocumentService licenseDocuments) {
    this.applications = applications;
    this.users = users;
    this.passwords = passwords;
    this.audit = audit;
    this.licenseDocuments = licenseDocuments;
  }

  @Transactional(readOnly = true)
  public InspectorPartnerLicenseDocumentService.InputStreamContent openLicenseDocument(
      UUID adminId, UUID applicationId) {
    requireAdmin(adminId);
    var application =
        applications
            .findById(applicationId)
            .orElseThrow(
                () ->
                    new AuthException(
                        HttpStatus.NOT_FOUND,
                        "APPLICATION_NOT_FOUND",
                        "Inspector partner application was not found."));
    return licenseDocuments.open(application.getPilotLicenseFileKey());
  }

  @Transactional(readOnly = true)
  public List<InspectorPartnerApplicationResponse> pending(UUID adminId) {
    requireAdmin(adminId);
    return applications.findAllByStatusOrderByCreatedAtAsc("PENDING").stream()
        .map(InspectorPartnerApplicationResponse::from)
        .toList();
  }

  @Transactional
  public InspectorPartnerApprovalResponse approve(
      UUID adminId,
      UUID applicationId,
      ReviewInspectorPartnerApplicationRequest request,
      String ip,
      String agent,
      String correlationId) {
    requireAdmin(adminId);
    var application = requirePending(applicationId);
    if (!application.getPilotLicenseExpiresAt().isAfter(LocalDate.now())) {
      throw new AuthException(
          HttpStatus.CONFLICT,
          "PILOT_LICENSE_EXPIRED",
          "Pilot license must be renewed before approval.");
    }
    if (users.findByNormalizedEmail(application.getNormalizedEmail()).isPresent()) {
      throw new AuthException(
          HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already in use.");
    }
    if (request.partnerContractFileKey() == null || request.partnerContractFileKey().isBlank()) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST,
          "PARTNER_CONTRACT_REQUIRED",
          "A signed partner contract reference is required before approval.");
    }
    application.approve(adminId, request.partnerContractFileKey(), request.reviewNote());
    String temporaryPassword = AuthCrypto.randomToken();
    User inspector =
        new User(
            application.getEmail(),
            application.getFullName(),
            passwords.encode(temporaryPassword),
            UserStatus.ACTIVE,
            ActorZone.INSPECTOR_PARTNER,
            null);
    inspector.addRole(UserRole.INSPECTOR);
    inspector.setPilotLicense(
        application.getPilotLicenseNumber(),
        application.getPilotLicenseClass(),
        application.getPilotLicenseExpiresAt(),
        application.getPilotLicenseFileKey());
    inspector.requirePasswordChange();
    users.saveAndFlush(inspector);
    applications.saveAndFlush(application);
    audit.record(
        adminId,
        inspector.getId(),
        "INSPECTOR_PARTNER_APPLICATION_APPROVED",
        "SUCCESS",
        ip,
        agent,
        correlationId);
    return new InspectorPartnerApprovalResponse(
        InspectorPartnerApplicationResponse.from(application), temporaryPassword);
  }

  @Transactional
  public InspectorPartnerApplicationResponse reject(
      UUID adminId,
      UUID applicationId,
      String reason,
      String ip,
      String agent,
      String correlationId) {
    requireAdmin(adminId);
    var application = requirePending(applicationId);
    application.reject(adminId, reason == null ? null : reason.trim());
    applications.saveAndFlush(application);
    audit.record(
        adminId,
        application.getId(),
        "INSPECTOR_PARTNER_APPLICATION_REJECTED",
        "SUCCESS",
        ip,
        agent,
        correlationId);
    return InspectorPartnerApplicationResponse.from(application);
  }

  private InspectorPartnerApplication requirePending(UUID id) {
    return applications
        .findByIdAndStatus(id, "PENDING")
        .orElseThrow(
            () ->
                new AuthException(
                    HttpStatus.NOT_FOUND,
                    "APPLICATION_NOT_FOUND",
                    "Pending application was not found."));
  }

  private void requireAdmin(UUID adminId) {
    User admin =
        users
            .findDetailedById(adminId)
            .filter(User::active)
            .orElseThrow(
                () ->
                    new AuthException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found."));
    if (admin.getActorZone() != ActorZone.PLATFORM || !admin.roleValues().contains(Roles.ADMIN)) {
      throw new AuthException(
          HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Platform Admin access is required.");
    }
  }
}

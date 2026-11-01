package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.CreateInspectorPartnerApplicationRequest;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectorPartnerApplicationService {
  private static final Logger log =
      LoggerFactory.getLogger(InspectorPartnerApplicationService.class);

  private final InspectorPartnerApplicationRepository applications;
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final SecurityAuditService audit;
  private final InspectorPartnerLicenseDocumentService licenseDocuments;
  private final ObjectProvider<JavaMailSender> mailSender;
  private final LoginOtpProperties properties;

  public InspectorPartnerApplicationService(
      InspectorPartnerApplicationRepository applications,
      UserRepository users,
      PasswordEncoder passwords,
      SecurityAuditService audit,
      InspectorPartnerLicenseDocumentService licenseDocuments,
      ObjectProvider<JavaMailSender> mailSender,
      LoginOtpProperties properties) {
    this.applications = applications;
    this.users = users;
    this.passwords = passwords;
    this.audit = audit;
    this.licenseDocuments = licenseDocuments;
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @Transactional
  public InspectorPartnerApplicationResponse submit(
      CreateInspectorPartnerApplicationRequest request,
      String ip,
      String agent,
      String correlationId) {
    licenseDocuments.validateReference(request.pilotLicenseFileKey());
    String email = User.normalizeEmail(request.email());
    if (request.pilotLicenseExpiresAt().isBefore(LocalDate.now())) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST, "PILOT_LICENSE_EXPIRED", "Pilot license is expired.");
    }
    if (users.findByNormalizedEmail(email).isPresent()
        || applications.existsByNormalizedEmailAndStatus(email, "PENDING")) {
      throw new AuthException(
          HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already registered or pending.");
    }
    var application =
        applications.saveAndFlush(
            new InspectorPartnerApplication(
                request.email(),
                request.fullName(),
                request.phone(),
                request.pilotLicenseNumber(),
                request.pilotLicenseClass(),
                request.pilotLicenseExpiresAt(),
                request.pilotLicenseFileKey()));
    audit.record(
        null,
        application.getId(),
        "INSPECTOR_PARTNER_APPLICATION_SUBMITTED",
        "SUCCESS",
        ip,
        agent,
        correlationId);
    return InspectorPartnerApplicationResponse.from(application);
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
    sendProvisionedCredentialsEmail(inspector, temporaryPassword);
    return new InspectorPartnerApprovalResponse(
        InspectorPartnerApplicationResponse.from(application), temporaryPassword);
  }

  private void sendProvisionedCredentialsEmail(User user, String temporaryPassword) {
    if (properties == null
        || properties.getEmailFrom() == null
        || properties.getEmailFrom().isBlank()) {
      log.warn(
          "Cannot send provisioned credentials email to {}: email-from is not configured",
          user.getEmail());
      return;
    }
    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null) {
      log.warn(
          "Cannot send provisioned credentials email to {}: JavaMailSender is not available",
          user.getEmail());
      return;
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(properties.getEmailFrom());
      message.setTo(user.getEmail());
      message.setSubject("[SmartDroneInspection] Thông tin tài khoản đăng nhập của bạn");
      message.setText(
          "Xin chào "
              + user.getFullName()
              + ",\n\n"
              + "Hồ sơ đối tác của bạn đã được phê duyệt thành công trên hệ thống SmartDroneInspection với vai trò: Kiểm tra viên đối tác (Inspector Partner).\n\n"
              + "Thông tin đăng nhập tài khoản của bạn:\n"
              + "- Địa chỉ Email: "
              + user.getEmail()
              + "\n"
              + "- Mật khẩu tạm thời: "
              + temporaryPassword
              + "\n\n"
              + "Đường dẫn đăng nhập: http://localhost:3002/login\n\n"
              + "Lưu ý an toàn:\n"
              + "- Bạn phải đổi sang mật khẩu mới trong lần đầu tiên đăng nhập.\n"
              + "- Tài khoản Inspector sẽ cần xác thực mã OTP gửi về email này trong lần đăng nhập đầu tiên.\n\n"
              + "Trân trọng,\n"
              + "Ban Quản trị SmartDroneInspection");
      log.info("Sending provisioned credentials email to {}", user.getEmail());
      sender.send(message);
      log.info("Successfully sent provisioned credentials email to {}", user.getEmail());
    } catch (Exception ex) {
      log.error(
          "Failed to send provisioned credentials email to {}: {}",
          user.getEmail(),
          ex.getMessage(),
          ex);
    }
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
    if (admin.getActorZone() != ActorZone.PLATFORM
        || (!admin.roleValues().contains(Roles.ADMIN)
            && !admin.roleValues().contains(Roles.MANAGER))) {
      throw new AuthException(
          HttpStatus.FORBIDDEN, "REVIEWER_REQUIRED", "Platform Reviewer access is required.");
    }
  }
}

package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.api.dto.request.InspectorApplicationSubmissionRequest;
import com.smartdroneinspection.users.api.dto.response.InspectorApplicationResponse;
import com.smartdroneinspection.users.api.dto.response.InspectorProfileResponse;
import com.smartdroneinspection.users.api.dto.response.UserResponse;
import com.smartdroneinspection.users.domain.InspectorApplication;
import com.smartdroneinspection.users.domain.InspectorProfile;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.InspectorApplicationRepository;
import com.smartdroneinspection.users.repository.InspectorProfileRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InspectorApplicationService {

  private final InspectorApplicationRepository applications;
  private final UserRepository users;
  private final InspectorProfileRepository profiles;
  private final PilotLicenseClassService licenseClasses;
  private final RegistrationOtpService registrationOtp;
  private final SecurityAuditService audit;
  private final InspectorPartnerLicenseDocumentService documents;
  private final ObjectProvider<EvidenceObjectStore> objectStore;
  private final ApplicationEventPublisher events;
  private final ObjectProvider<JavaMailSender> mailSender;
  private final LoginOtpProperties mailProperties;
  private final PasswordEncoder passwords;
  private final PasswordPolicy passwordPolicy;
  private final InspectorProfileService inspectorProfileService;

  public InspectorApplicationService(
      InspectorApplicationRepository applications,
      UserRepository users,
      InspectorProfileRepository profiles,
      PilotLicenseClassService licenseClasses,
      RegistrationOtpService registrationOtp,
      SecurityAuditService audit,
      InspectorPartnerLicenseDocumentService documents,
      ObjectProvider<EvidenceObjectStore> objectStore,
      ApplicationEventPublisher events,
      ObjectProvider<JavaMailSender> mailSender,
      LoginOtpProperties mailProperties,
      PasswordEncoder passwords,
      PasswordPolicy passwordPolicy,
      InspectorProfileService inspectorProfileService) {
    this.applications = applications;
    this.users = users;
    this.profiles = profiles;
    this.licenseClasses = licenseClasses;
    this.registrationOtp = registrationOtp;
    this.audit = audit;
    this.documents = documents;
    this.objectStore = objectStore;
    this.events = events;
    this.mailSender = mailSender;
    this.mailProperties = mailProperties;
    this.passwords = passwords;
    this.passwordPolicy = passwordPolicy;
    this.inspectorProfileService = inspectorProfileService;
  }

  @Transactional
  public InspectorApplicationResponse submit(
      InspectorApplicationSubmissionRequest request,
      String ip,
      String userAgent,
      String correlationId) {
    String email = User.normalizeEmail(request.email());
    if (users.findByNormalizedEmail(email).isPresent()) {
      throw new AuthException(
          HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already registered.");
    }
    applications.findByNormalizedEmail(email).ifPresent(this::purgePreviousRejectedApplication);
    if (applications.existsByNormalizedEmail(email)) {
      throw new AuthException(
          HttpStatus.CONFLICT,
          "APPLICATION_ALREADY_PENDING",
          "A reviewable application already exists for this email.");
    }
    String className = licenseClasses.requireActiveName(request.pilotLicenseClass());
    registrationOtp.verify(request.otpChallengeToken(), request.otpCode(), email);
    documents.validateApplicationLicenseReference(request.licenseScanFileKey());
    var scan = documents.openApplicationLicenseScan(request.licenseScanFileKey());
    try (var stream = scan.stream()) {
      if (stream.read() < 0) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST, "INVALID_LICENSE_SCAN", "License scan is empty");
      }
    } catch (IOException exception) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_LICENSE_SCAN", "License scan could not be read");
    }
    passwordPolicy.validate(request.password(), email, request.fullName());
    InspectorApplication application =
        new InspectorApplication(
            request.email().trim(),
            email,
            request.fullName().trim(),
            trim(request.phoneNumber()),
            trim(request.citizenId()),
            trim(request.address()),
            trim(request.pilotLicenseNumber()),
            className,
            request.pilotLicenseExpiresAt(),
            request.licenseScanFileKey(),
            correlationId,
            passwords.encode(request.password()));
    application = applications.saveAndFlush(application);
    events.publishEvent(
        new com.smartdroneinspection.users.events.InspectorApplicationSubmittedEvent(
            application.getId()));
    sendApplicationReceipt(application);
    audit.record(
        null, null, "INSPECTOR_APPLICATION_SUBMITTED", "PENDING_REVIEW", null, null, correlationId);
    return toResponse(application);
  }

  @Transactional(readOnly = true)
  public List<InspectorApplicationResponse> listPending() {
    return applications.findAllByStatusOrderByCreatedAtAsc("PENDING_REVIEW").stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<InspectorApplicationResponse> listApprovedAwaitingContract() {
    return applications.findAllByStatusOrderByCreatedAtAsc("APPROVED").stream()
        .filter(app -> app.getApprovedUserId() != null)
        .filter(
            app ->
                profiles
                    .findById(app.getApprovedUserId())
                    .map(
                        profile ->
                            profile.getPartnerContractFileKey() == null
                                || profile.getPartnerContractFileKey().isBlank())
                    .orElse(true))
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public InspectorApplicationResponse get(UUID id) {
    return toResponse(find(id));
  }

  @Transactional
  public UserResponse approve(
      UUID id, UUID managerId, String ip, String userAgent, String correlationId) {
    InspectorApplication application = find(id);
    if (!"PENDING_REVIEW".equals(application.getStatus())) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "APPLICATION_NOT_PENDING", "Application is not pending review");
    }
    if (users.findByNormalizedEmail(application.getNormalizedEmail()).isPresent()) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EMAIL_ALREADY_EXISTS",
          "An account already exists for this application email");
    }
    requireMailTransport();
    User inspector =
        new User(
            application.getEmail(),
            application.getFullName(),
            application.getPhoneNumber(),
            null,
            application.getPasswordHash(),
            UserStatus.ACTIVE,
            ActorZone.INSPECTOR_PARTNER,
            null);
    inspector.setOtpVerified(true);
    inspector.requirePasswordChange();
    inspector.addRole(UserRole.INSPECTOR);
    inspector = users.saveAndFlush(inspector);
    InspectorProfile profile = new InspectorProfile(inspector.getId());
    profile.setOnboardingStatus("PENDING_REVIEW");
    profile.updatePersonalDetails(application.getCitizenId(), null, application.getAddress(), null);
    profile.updatePilotLicense(
        application.getPilotLicenseNumber(),
        application.getPilotLicenseClass(),
        application.getPilotLicenseExpiresAt(),
        application.getLicenseScanFileKey());
    profiles.saveAndFlush(profile);
    application.approve(managerId, inspector.getId());
    applications.saveAndFlush(application);
    audit.record(
        managerId,
        null,
        "INSPECTOR_APPLICATION_APPROVED",
        "APPROVED",
        null,
        null,
        correlationId != null ? correlationId : application.getCorrelationId());
    sendDecisionEmail(
        application.getEmail(),
        application.getFullName(),
        "[SmartDroneInspection] Inspector application approved",
        "Hồ sơ Inspector của bạn đã được chấp thuận. Manager sẽ liên hệ để hẹn gặp trực tiếp ký hợp đồng. Sau buổi ký, Manager tải bản hợp đồng đã ký lên hệ thống.");
    sendAccountSetupEmail(inspector);
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
        application.getPilotLicenseNumber(),
        application.getPilotLicenseClass(),
        application.getPilotLicenseExpiresAt(),
        null);
  }

  @Transactional
  public void reject(
      UUID id, UUID managerId, String reason, String ip, String userAgent, String correlationId) {
    InspectorApplication application = find(id);
    if (!"PENDING_REVIEW".equals(application.getStatus())) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "APPLICATION_NOT_PENDING", "Application is not pending review");
    }
    application.reject(managerId, reason.trim());
    applications.saveAndFlush(application);
    deliverRejectionThenPurge(application);
  }

  @Transactional(readOnly = true)
  public InspectorPartnerLicenseDocumentService.InputStreamContent openLicenseScan(UUID id) {
    return documents.openApplicationLicenseScan(find(id).getLicenseScanFileKey());
  }

  @Transactional
  public InspectorProfileResponse uploadSignedContract(
      UUID id, MultipartFile file, UUID managerId) {
    InspectorApplication application = find(id);
    if (!"APPROVED".equals(application.getStatus()) || application.getApprovedUserId() == null) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "APPLICATION_NOT_APPROVED",
          "Approve the application before uploading its signed agreement");
    }
    var stored = documents.uploadContract(file);
    InspectorProfileResponse response =
        inspectorProfileService.recordSignedPartnerContract(
            application.getApprovedUserId(), stored.fileKey(), stored.sha256Hex(), managerId);
    audit.record(
        managerId,
        application.getApprovedUserId(),
        "INSPECTOR_SIGNED_CONTRACT_UPLOADED",
        "APPROVED",
        null,
        null,
        application.getCorrelationId());
    return response;
  }

  public void retryRejectionCleanup(UUID id) {
    InspectorApplication application = find(id);
    if (!"REJECTED".equals(application.getStatus())
        || !"SENT".equals(application.getNotificationState())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "REJECTION_CLEANUP_NOT_READY",
          "Rejection email must be sent before cleanup retry");
    }
    purgePreviousRejectedApplication(application);
  }

  public String uploadApplicationLicenseScan(MultipartFile file) {
    return documents.uploadApplicationLicenseScan(file);
  }

  private void sendApplicationReceipt(InspectorApplication application) {
    sendDecisionEmail(
        application.getEmail(),
        application.getFullName(),
        "[SmartDroneInspection] Đã nhận hồ sơ Inspector",
        "Chúng tôi đã nhận hồ sơ của bạn và gửi Manager xem xét. Vui lòng chờ email kết quả. Tài khoản chỉ được tạo sau khi hồ sơ được chấp thuận.");
  }

  private void deliverRejectionThenPurge(InspectorApplication application) {
    sendDecisionEmail(
        application.getEmail(),
        application.getFullName(),
        "[SmartDroneInspection] Kết quả hồ sơ Inspector",
        "Hồ sơ của bạn chưa được chấp thuận. Lý do: "
            + application.getRejectionReason()
            + "\n\nBạn có thể gửi hồ sơ mới sau khi bổ sung thông tin.");
    application.markNotificationSent();
    applications.saveAndFlush(application);
    purgePreviousRejectedApplication(application);
  }

  private void purgePreviousRejectedApplication(InspectorApplication application) {
    if (!"REJECTED".equals(application.getStatus())
        || !"SENT".equals(application.getNotificationState())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "APPLICATION_NOT_PURGEABLE",
          "Only emailed rejected applications may be purged");
    }
    String fileKey = application.getLicenseScanFileKey();
    if (fileKey != null && !fileKey.isBlank()) {
      try {
        documents.deleteApplicationLicenseScan(fileKey);
      } catch (IOException cleanupFailure) {
        application.recordCleanupAttempt(cleanupFailure.getMessage());
        applications.saveAndFlush(application);
        throw new BusinessException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "APPLICATION_FILE_CLEANUP_FAILED",
            "Rejection email was delivered but file cleanup failed; retry cleanup.");
      }
    }
    UUID appId = application.getId();
    UUID reviewerId = application.getReviewedByUserId();
    applications.delete(application);
    applications.flush();
    audit.record(
        reviewerId,
        null,
        "INSPECTOR_APPLICATION_REJECTED_PURGED",
        "REJECTED",
        null,
        null,
        "application:" + appId);
  }

  private void sendAccountSetupEmail(User inspector) {
    sendDecisionEmail(
        inspector.getEmail(),
        inspector.getFullName(),
        "[SmartDroneInspection] Thiết lập tài khoản Inspector",
        "Tài khoản đã được tạo. Vui lòng dùng chức năng Quên mật khẩu tại trang đăng nhập để thiết lập mật khẩu mới.");
  }

  private void sendDecisionEmail(String email, String fullName, String subject, String body) {
    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null
        || mailProperties.getEmailFrom() == null
        || mailProperties.getEmailFrom().isBlank()) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "APPLICATION_EMAIL_UNAVAILABLE",
          "Decision email transport is not configured");
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailProperties.getEmailFrom());
    message.setTo(email);
    message.setSubject(subject);
    message.setText(
        "Xin chào " + fullName + ",\n\n" + body + "\n\nTrân trọng,\nSmartDroneInspection");
    try {
      sender.send(message);
    } catch (RuntimeException ex) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "APPLICATION_EMAIL_FAILED",
          "Email delivery failed; application data was retained");
    }
  }

  private void requireMailTransport() {
    if (mailSender.getIfAvailable() == null
        || mailProperties.getEmailFrom() == null
        || mailProperties.getEmailFrom().isBlank()) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "APPLICATION_EMAIL_UNAVAILABLE",
          "Configure email before approving Inspector applications");
    }
  }

  private InspectorApplication find(UUID id) {
    return applications
        .findById(id)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "APPLICATION_NOT_FOUND",
                    "Inspector application not found"));
  }

  private InspectorApplicationResponse toResponse(InspectorApplication app) {
    return new InspectorApplicationResponse(
        app.getId(),
        app.getEmail(),
        app.getFullName(),
        app.getPhoneNumber(),
        app.getCitizenId(),
        app.getAddress(),
        app.getPilotLicenseNumber(),
        app.getPilotLicenseClass(),
        app.getPilotLicenseExpiresAt(),
        app.getLicenseScanFileKey() != null,
        app.getCleanupAttempts() > 0,
        app.getStatus(),
        app.getApprovedUserId(),
        app.getReviewedByUserId(),
        app.getReviewedAt(),
        app.getRejectionReason(),
        app.getCreatedAt());
  }

  private static String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}

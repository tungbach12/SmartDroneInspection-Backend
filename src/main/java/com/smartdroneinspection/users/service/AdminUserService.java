package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.CreateUserRequest;
import com.smartdroneinspection.users.api.dto.request.UpdateRolesRequest;
import com.smartdroneinspection.users.api.dto.response.ProvisionedUserResponse;
import com.smartdroneinspection.users.api.dto.response.UserResponse;
import com.smartdroneinspection.users.domain.RolePolicy;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.security.AuthCrypto;
import java.util.ArrayList;
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
public class AdminUserService {

  private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

  private final UserRepository users;
  private final RolePolicy rolePolicy;
  private final PasswordEncoder passwords;
  private final AuthService auth;
  private final SecurityAuditService audit;
  private final ObjectProvider<JavaMailSender> mailSender;
  private final LoginOtpProperties properties;

  public AdminUserService(
      UserRepository users,
      RolePolicy rolePolicy,
      PasswordEncoder passwords,
      AuthService auth,
      SecurityAuditService audit,
      ObjectProvider<JavaMailSender> mailSender,
      LoginOtpProperties properties) {
    this.users = users;
    this.rolePolicy = rolePolicy;
    this.passwords = passwords;
    this.auth = auth;
    this.audit = audit;
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public List<UserResponse> list() {
    return users.findAllWithRoles().stream().map(user -> toResponse(user)).toList();
  }

  @Transactional
  public ProvisionedUserResponse create(
      UUID actorId,
      CreateUserRequest request,
      String ipAddress,
      String userAgent,
      String correlationId) {
    if (actorId != null) {
      users
          .findDetailedById(actorId)
          .ifPresent(
              actor -> {
                if (actor.roleValues().contains(com.smartdroneinspection.shared.auth.Roles.MANAGER)
                    && !actor
                        .roleValues()
                        .contains(com.smartdroneinspection.shared.auth.Roles.ADMIN)) {
                  if (request.actorZone()
                          != com.smartdroneinspection.users.domain.enums.ActorZone.INSPECTOR_PARTNER
                      || request.organizationId() != null
                      || request.roles() == null
                      || request.roles().size() != 1
                      || !request
                          .roles()
                          .contains(
                              com.smartdroneinspection.users.domain.enums.UserRole.INSPECTOR)) {
                    throw new AuthException(
                        HttpStatus.FORBIDDEN,
                        "FORBIDDEN",
                        "A Platform Manager may only create partner Inspector accounts.");
                  }
                }
              });
    }
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
            request.phoneNumber() != null ? request.phoneNumber().trim() : null,
            request.avatarUrl() != null ? request.avatarUrl().trim() : null,
            passwords.encode(temporaryPassword),
            UserStatus.ACTIVE,
            request.actorZone(),
            request.organizationId());
    user.requirePasswordChange();
    for (var role : request.roles()) {
      user.addRole(role);
    }
    // Flush before the audit insert: SecurityAuditService writes raw SQL with an FK to users.
    user = users.saveAndFlush(user);
    audit.record(
        actorId, user.getId(), "USER_CREATED", "SUCCESS", ipAddress, userAgent, correlationId);
    sendProvisionedCredentialsEmail(user, temporaryPassword);
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
    sendPasswordResetEmail(user, temporaryPassword);
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
        user.getPhoneNumber(),
        user.getAvatarUrl(),
        user.roleValues(),
        user.getActorZone().name(),
        user.getOrganizationId(),
        user.isVisionFallbackEnabled());
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

      boolean isInspector =
          user.roleValues().contains(com.smartdroneinspection.shared.auth.Roles.INSPECTOR);
      String roleTitle =
          isInspector
              ? "Kiểm tra viên đối tác (Inspector Partner)"
              : "Thành viên hệ thống (" + String.join(", ", user.roleValues()) + ")";

      message.setSubject("[SmartDroneInspection] Thông tin tài khoản đăng nhập của bạn");
      message.setText(
          "Xin chào "
              + user.getFullName()
              + ",\n\n"
              + "Tài khoản của bạn đã được khởi tạo thành công trên hệ thống SmartDroneInspection với vai trò: "
              + roleTitle
              + ".\n\n"
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
              + (isInspector
                  ? "- Tài khoản Inspector sẽ cần xác thực mã OTP gửi về email này trong lần đăng nhập đầu tiên.\n\n"
                  : "\n")
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

  private void sendPasswordResetEmail(User user, String temporaryPassword) {
    if (properties == null
        || properties.getEmailFrom() == null
        || properties.getEmailFrom().isBlank()) {
      log.warn(
          "Cannot send password reset email to {}: email-from is not configured", user.getEmail());
      return;
    }
    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null) {
      log.warn(
          "Cannot send password reset email to {}: JavaMailSender is not available",
          user.getEmail());
      return;
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(properties.getEmailFrom());
      message.setTo(user.getEmail());
      message.setSubject("[SmartDroneInspection] Mật khẩu đăng nhập của bạn đã được đặt lại");
      message.setText(
          "Xin chào "
              + user.getFullName()
              + ",\n\n"
              + "Mật khẩu cho tài khoản "
              + user.getEmail()
              + " đã được quản trị viên đặt lại.\n\n"
              + "Mật khẩu tạm thời mới của bạn là: "
              + temporaryPassword
              + "\n\n"
              + "Đường dẫn đăng nhập: http://localhost:3002/login\n\n"
              + "Vui lòng đăng nhập và đổi mật khẩu mới ngay sau khi nhận được thông báo này.\n\n"
              + "Trân trọng,\n"
              + "Ban Quản trị SmartDroneInspection");
      log.info("Sending password reset email to {}", user.getEmail());
      sender.send(message);
      log.info("Successfully sent password reset email to {}", user.getEmail());
    } catch (Exception ex) {
      log.error(
          "Failed to send password reset email to {}: {}", user.getEmail(), ex.getMessage(), ex);
    }
  }
}

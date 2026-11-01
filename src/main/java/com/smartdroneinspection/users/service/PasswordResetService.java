package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.response.SendPasswordResetOtpResponse;
import com.smartdroneinspection.users.domain.OtpChallenge;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.OtpChallengePurpose;
import com.smartdroneinspection.users.repository.AuthSessionRepository;
import com.smartdroneinspection.users.repository.OtpChallengeRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.security.AuthCrypto;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

  private static final long CHALLENGE_TTL_SECONDS = 600; // 10 minutes
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(PasswordResetService.class);

  private final OtpChallengeRepository challenges;
  private final UserRepository users;
  private final AuthSessionRepository sessions;
  private final LoginOtpProperties properties;
  private final ObjectProvider<JavaMailSender> mailSender;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final SecurityAuditService audit;

  public PasswordResetService(
      OtpChallengeRepository challenges,
      UserRepository users,
      AuthSessionRepository sessions,
      LoginOtpProperties properties,
      ObjectProvider<JavaMailSender> mailSender,
      PasswordEncoder passwordEncoder,
      PasswordPolicy passwordPolicy,
      SecurityAuditService audit) {
    this.challenges = challenges;
    this.users = users;
    this.sessions = sessions;
    this.properties = properties;
    this.mailSender = mailSender;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.audit = audit;
  }

  @Transactional
  public SendPasswordResetOtpResponse sendOtp(String email, String clientIp) {
    if (email == null || email.isBlank()) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST, "EMAIL_REQUIRED", "Địa chỉ email là bắt buộc.");
    }
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    User user =
        users
            .findByNormalizedEmail(normalized)
            .orElseThrow(
                () ->
                    new AuthException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "Không tìm thấy tài khoản với địa chỉ email này."));

    Instant now = Instant.now();
    Instant since = now.minusSeconds(15 * 60L);
    if (challenges.countByPurposeAndDestinationAndCreatedAtAfter(
            OtpChallengePurpose.PASSWORD_RESET, normalized, since)
        >= 5) {
      throw new AuthException(
          HttpStatus.TOO_MANY_REQUESTS,
          "OTP_SEND_RATE_LIMITED",
          "Bạn đã yêu cầu gửi OTP quá nhiều lần. Vui lòng chờ vài phút rồi thử lại.");
    }

    String code = String.format("%06d", RANDOM.nextInt(1_000_000));
    String rawChallenge = AuthCrypto.randomToken();

    OtpChallenge challenge =
        OtpChallenge.passwordReset(
            hash("pwd-reset-challenge:" + rawChallenge),
            normalized,
            hash("pwd-reset-otp:" + code),
            now.plusSeconds(CHALLENGE_TTL_SECONDS));

    challenges.save(challenge);

    sendEmail(user.getEmail(), user.getFullName(), code);

    return new SendPasswordResetOtpResponse(rawChallenge, CHALLENGE_TTL_SECONDS);
  }

  @Transactional(noRollbackFor = AuthException.class)
  public void confirmPasswordReset(
      String email,
      String rawChallenge,
      String code,
      String newPassword,
      String clientIp,
      String userAgent,
      String correlationId) {
    if (email == null || email.isBlank()) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST, "EMAIL_REQUIRED", "Địa chỉ email là bắt buộc.");
    }
    if (rawChallenge == null || rawChallenge.isBlank()) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST, "CHALLENGE_REQUIRED", "Mã phiên xác thực không hợp lệ.");
    }
    if (code == null || code.isBlank()) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST, "OTP_CODE_REQUIRED", "Mã xác thực OTP là bắt buộc.");
    }
    if (newPassword == null || newPassword.isBlank()) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST, "PASSWORD_REQUIRED", "Mật khẩu mới không được để trống.");
    }

    String normalized = email.trim().toLowerCase(Locale.ROOT);
    User user =
        users
            .findByNormalizedEmail(normalized)
            .orElseThrow(
                () ->
                    new AuthException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "Không tìm thấy tài khoản với địa chỉ email này."));

    OtpChallenge challenge =
        challenges
            .findForUpdateByChallengeHashAndPurpose(
                hash("pwd-reset-challenge:" + rawChallenge), OtpChallengePurpose.PASSWORD_RESET)
            .orElseThrow(
                () ->
                    new AuthException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_CHALLENGE",
                        "Phiên xác thực đã hết hạn hoặc không tồn tại."));

    if (!challenge.getDestination().equalsIgnoreCase(normalized)) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST, "INVALID_CHALLENGE", "Email không khớp với phiên xác thực.");
    }

    if (!challenge.verify(hash("pwd-reset-otp:" + code.trim()), Instant.now())) {
      challenges.saveAndFlush(challenge);
      throw new AuthException(
          HttpStatus.BAD_REQUEST, "INVALID_OTP", "Mã xác thực OTP không đúng hoặc đã hết hạn.");
    }
    challenges.saveAndFlush(challenge);

    try {
      passwordPolicy.validate(newPassword, user.getEmail(), user.getFullName());
    } catch (IllegalArgumentException ex) {
      throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", ex.getMessage());
    }

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.incrementAuthVersion();
    user.recordSuccessfulLogin(clientIp);
    users.saveAndFlush(user);

    sessions
        .findByUser_IdAndRevokedAtIsNull(user.getId())
        .forEach(
            session -> {
              session.revoke("password-reset");
              sessions.save(session);
            });

    audit.record(
        user.getId(),
        user.getId(),
        "AUTH_PASSWORD_RESET_COMPLETED",
        "SUCCESS",
        clientIp,
        userAgent,
        correlationId);
  }

  private void sendEmail(String toEmail, String fullName, String code) {
    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null) {
      log.warn("JavaMailSender is not available. Skipping email dispatch to {}", toEmail);
      return;
    }
    try {
      log.info("Sending password reset OTP email to {} via configured SMTP", toEmail);
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(properties.getEmailFrom());
      message.setTo(toEmail);
      message.setSubject("[SmartDroneInspection] Mã OTP đặt lại mật khẩu của bạn");
      message.setText(
          "Xin chào "
              + (fullName != null ? fullName : "bạn")
              + ",\n\n"
              + "Hệ thống SmartDroneInspection đã nhận được yêu cầu đặt lại mật khẩu cho tài khoản "
              + toEmail
              + ".\n\n"
              + "Mã OTP xác thực của bạn là: "
              + code
              + "\n\nMã có hiệu lực trong vòng 10 phút. Tuyệt đối không chia sẻ mã này cho bất kỳ ai.\n\n"
              + "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này.\n\n"
              + "Trân trọng,\n"
              + "Đội ngũ SmartDroneInspection");
      sender.send(message);
      log.info("Successfully sent password reset OTP email to {}", toEmail);
    } catch (Exception ex) {
      log.error("Failed to send password reset OTP email to {}: {}", toEmail, ex.getMessage(), ex);
    }
  }

  private String hash(String input) {
    return AuthCrypto.hmacSha256Hex(input, properties.getChallengePepper());
  }
}

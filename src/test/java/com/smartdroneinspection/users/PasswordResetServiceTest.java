package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.domain.AuthSession;
import com.smartdroneinspection.users.domain.OtpChallenge;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ClientType;
import com.smartdroneinspection.users.domain.enums.OtpChallengePurpose;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.AuthSessionRepository;
import com.smartdroneinspection.users.repository.OtpChallengeRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.security.AuthCrypto;
import com.smartdroneinspection.users.service.LoginOtpProperties;
import com.smartdroneinspection.users.service.PasswordPolicy;
import com.smartdroneinspection.users.service.PasswordResetService;
import com.smartdroneinspection.users.service.SecurityAuditService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

  private static final String EMAIL = "pilot@example.test";
  private static final String PEPPER = "unit-test-pepper";
  private static final String RAW_CHALLENGE = "raw-challenge-token";
  private static final String CODE = "123456";

  @Mock OtpChallengeRepository challenges;
  @Mock UserRepository users;
  @Mock AuthSessionRepository sessions;
  @Mock ObjectProvider<JavaMailSender> mailSender;
  @Mock PasswordEncoder passwordEncoder;
  @Mock SecurityAuditService audit;

  private LoginOtpProperties properties;
  private PasswordResetService service;

  @BeforeEach
  void setUp() {
    properties = new LoginOtpProperties();
    properties.setChallengePepper(PEPPER);
    service =
        new PasswordResetService(
            challenges,
            users,
            sessions,
            properties,
            mailSender,
            passwordEncoder,
            new PasswordPolicy(),
            audit);
  }

  @Test
  void sendOtpRejectsBlankEmail() {
    assertAuthCode(() -> service.sendOtp("  ", null), "EMAIL_REQUIRED");
  }

  @Test
  void sendOtpRejectsUnknownEmail() {
    when(users.findByNormalizedEmail(EMAIL)).thenReturn(Optional.empty());

    assertAuthCode(() -> service.sendOtp(EMAIL, null), "USER_NOT_FOUND");
  }

  @Test
  void sendOtpRejectsWhenRateLimitIsReached() {
    when(users.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(user()));
    when(challenges.countByPurposeAndDestinationAndCreatedAtAfter(
            eq(OtpChallengePurpose.PASSWORD_RESET), eq(EMAIL), any(Instant.class)))
        .thenReturn(5L);

    assertAuthCode(() -> service.sendOtp("Pilot@Example.Test", null), "OTP_SEND_RATE_LIMITED");
    verify(challenges, never()).save(any(OtpChallenge.class));
  }

  @Test
  void sendOtpPersistsChallengeAndEmailsTheCode() {
    User user = user();
    JavaMailSender sender = mock(JavaMailSender.class);
    when(users.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(user));
    when(challenges.countByPurposeAndDestinationAndCreatedAtAfter(
            eq(OtpChallengePurpose.PASSWORD_RESET), eq(EMAIL), any(Instant.class)))
        .thenReturn(0L);
    when(mailSender.getIfAvailable()).thenReturn(sender);

    var response = service.sendOtp(" Pilot@Example.Test ", "127.0.0.1");

    assertThat(response.challengeToken()).isNotBlank();
    assertThat(response.expiresInSeconds()).isEqualTo(600);

    ArgumentCaptor<OtpChallenge> challengeCaptor = ArgumentCaptor.forClass(OtpChallenge.class);
    verify(challenges).save(challengeCaptor.capture());
    OtpChallenge challenge = challengeCaptor.getValue();
    assertThat(challenge.getPurpose()).isEqualTo(OtpChallengePurpose.PASSWORD_RESET);
    assertThat(challenge.getDestination()).isEqualTo(EMAIL);
    assertThat(challenge.getChannel())
        .isEqualTo(com.smartdroneinspection.users.domain.enums.LoginOtpChannel.EMAIL);

    ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender).send(mailCaptor.capture());
    assertThat(mailCaptor.getValue().getTo()).containsExactly(EMAIL);
    assertThat(mailCaptor.getValue().getSubject()).contains("OTP");
  }

  @Test
  void sendOtpSkipsEmailWhenNoMailSenderIsConfigured() {
    when(users.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(user()));
    when(challenges.countByPurposeAndDestinationAndCreatedAtAfter(
            eq(OtpChallengePurpose.PASSWORD_RESET), eq(EMAIL), any(Instant.class)))
        .thenReturn(0L);
    when(mailSender.getIfAvailable()).thenReturn(null);

    var response = service.sendOtp(EMAIL, null);

    assertThat(response.challengeToken()).isNotBlank();
    verify(challenges).save(any(OtpChallenge.class));
  }

  @Test
  void sendOtpSwallowsMailTransportFailures() {
    JavaMailSender sender = mock(JavaMailSender.class);
    when(users.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(user()));
    when(challenges.countByPurposeAndDestinationAndCreatedAtAfter(
            eq(OtpChallengePurpose.PASSWORD_RESET), eq(EMAIL), any(Instant.class)))
        .thenReturn(0L);
    when(mailSender.getIfAvailable()).thenReturn(sender);
    doThrow(new MailSendException("smtp down")).when(sender).send(any(SimpleMailMessage.class));

    assertThatCode(() -> service.sendOtp(EMAIL, null)).doesNotThrowAnyException();
    verify(challenges).save(any(OtpChallenge.class));
  }

  @Test
  void confirmPasswordResetValidatesAllRequiredFields() {
    assertAuthCode(
        () ->
            service.confirmPasswordReset(null, RAW_CHALLENGE, CODE, "NewValid9!", null, null, null),
        "EMAIL_REQUIRED");
    assertAuthCode(
        () -> service.confirmPasswordReset(EMAIL, " ", CODE, "NewValid9!", null, null, null),
        "CHALLENGE_REQUIRED");
    assertAuthCode(
        () ->
            service.confirmPasswordReset(
                EMAIL, RAW_CHALLENGE, null, "NewValid9!", null, null, null),
        "OTP_CODE_REQUIRED");
    assertAuthCode(
        () -> service.confirmPasswordReset(EMAIL, RAW_CHALLENGE, CODE, " ", null, null, null),
        "PASSWORD_REQUIRED");
  }

  @Test
  void confirmPasswordResetRejectsUnknownUser() {
    when(users.findByNormalizedEmail(EMAIL)).thenReturn(Optional.empty());

    assertAuthCode(
        () ->
            service.confirmPasswordReset(
                EMAIL, RAW_CHALLENGE, CODE, "NewValid9!", null, null, null),
        "USER_NOT_FOUND");
  }

  @Test
  void confirmPasswordResetRejectsUnknownChallenge() {
    when(users.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(user()));
    when(challenges.findForUpdateByChallengeHashAndPurpose(
            challengeHash(), OtpChallengePurpose.PASSWORD_RESET))
        .thenReturn(Optional.empty());

    assertAuthCode(
        () ->
            service.confirmPasswordReset(
                EMAIL, RAW_CHALLENGE, CODE, "NewValid9!", null, null, null),
        "INVALID_CHALLENGE");
  }

  @Test
  void confirmPasswordResetRejectsChallengeForAnotherEmail() {
    when(users.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(user()));
    when(challenges.findForUpdateByChallengeHashAndPurpose(
            challengeHash(), OtpChallengePurpose.PASSWORD_RESET))
        .thenReturn(
            Optional.of(challenge("other@example.test", CODE, Instant.now().plusSeconds(600))));

    assertAuthCode(
        () ->
            service.confirmPasswordReset(
                EMAIL, RAW_CHALLENGE, CODE, "NewValid9!", null, null, null),
        "INVALID_CHALLENGE");
  }

  @Test
  void confirmPasswordResetRejectsWrongOtpAndPersistsAttempt() {
    when(users.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(user()));
    OtpChallenge challenge = challenge(EMAIL, CODE, Instant.now().plusSeconds(600));
    when(challenges.findForUpdateByChallengeHashAndPurpose(
            challengeHash(), OtpChallengePurpose.PASSWORD_RESET))
        .thenReturn(Optional.of(challenge));

    assertAuthCode(
        () ->
            service.confirmPasswordReset(
                EMAIL, RAW_CHALLENGE, "000000", "NewValid9!", null, null, null),
        "INVALID_OTP");
    verify(challenges).saveAndFlush(challenge);
    assertThat(challenge.getFailedAttempts()).isEqualTo(1);
  }

  @Test
  void confirmPasswordResetRejectsWeakPassword() {
    when(users.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(user()));
    OtpChallenge challenge = challenge(EMAIL, CODE, Instant.now().plusSeconds(600));
    when(challenges.findForUpdateByChallengeHashAndPurpose(
            challengeHash(), OtpChallengePurpose.PASSWORD_RESET))
        .thenReturn(Optional.of(challenge));

    assertAuthCode(
        () -> service.confirmPasswordReset(EMAIL, RAW_CHALLENGE, CODE, "weak", null, null, null),
        "INVALID_PASSWORD");
    verify(challenges).saveAndFlush(challenge);
  }

  @Test
  void confirmPasswordResetUpdatesPasswordRevokesSessionsAndAudits() {
    User user = user();
    AuthSession first = new AuthSession(user, ClientType.WEB, Instant.now().plusSeconds(3600));
    AuthSession second = new AuthSession(user, ClientType.MOBILE, Instant.now().plusSeconds(3600));
    OtpChallenge challenge = challenge(EMAIL, CODE, Instant.now().plusSeconds(600));
    when(users.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(user));
    when(challenges.findForUpdateByChallengeHashAndPurpose(
            challengeHash(), OtpChallengePurpose.PASSWORD_RESET))
        .thenReturn(Optional.of(challenge));
    when(passwordEncoder.encode("NewValid9!")).thenReturn("encoded-password");
    when(sessions.findByUser_IdAndRevokedAtIsNull(null)).thenReturn(List.of(first, second));

    service.confirmPasswordReset(
        " Pilot@Example.Test ",
        RAW_CHALLENGE,
        " " + CODE + " ",
        "NewValid9!",
        "127.0.0.1",
        "JUnit",
        "correlation-1");

    assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
    assertThat(user.getAuthVersion()).isEqualTo(1);
    assertThat(first.active(Instant.now())).isFalse();
    assertThat(second.active(Instant.now())).isFalse();
    assertThat(challenge.getConsumedAt()).isNotNull();
    verify(challenges).saveAndFlush(challenge);
    verify(users).saveAndFlush(user);
    verify(sessions).save(first);
    verify(sessions).save(second);
    verify(audit)
        .record(
            null,
            null,
            "AUTH_PASSWORD_RESET_COMPLETED",
            "SUCCESS",
            "127.0.0.1",
            "JUnit",
            "correlation-1");
  }

  private static User user() {
    return new User(EMAIL, "Pilot", "old-hash", UserStatus.ACTIVE);
  }

  private static OtpChallenge challenge(String destination, String code, Instant expiresAt) {
    return OtpChallenge.passwordReset(
        challengeHash(),
        destination,
        AuthCrypto.hmacSha256Hex("pwd-reset-otp:" + code, PEPPER),
        expiresAt);
  }

  private static String challengeHash() {
    return AuthCrypto.hmacSha256Hex("pwd-reset-challenge:" + RAW_CHALLENGE, PEPPER);
  }

  private static void assertAuthCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String expectedCode) {
    assertThatThrownBy(callable)
        .isInstanceOfSatisfying(
            AuthException.class, exception -> assertThat(exception.code()).isEqualTo(expectedCode));
  }
}

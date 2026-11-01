package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.response.AuthFlowResponse;
import com.smartdroneinspection.users.api.dto.response.AuthStep;
import com.smartdroneinspection.users.domain.LoginOtpChallenge;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.LoginOtpChannel;
import com.smartdroneinspection.users.repository.LoginOtpChallengeRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.security.AuthCrypto;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginOtpService {

  private static final long CHALLENGE_TTL_SECONDS = 300;
  private static final SecureRandom RANDOM = new SecureRandom();
  private final LoginOtpChallengeRepository challenges;
  private final UserRepository users;
  private final LoginOtpProperties properties;
  private final LoginOtpDelivery delivery;

  public LoginOtpService(
      LoginOtpChallengeRepository challenges,
      UserRepository users,
      LoginOtpProperties properties,
      LoginOtpDelivery delivery) {
    this.challenges = challenges;
    this.users = users;
    this.properties = properties;
    this.delivery = delivery;
  }

  public boolean isReady() {
    boolean emailReady =
        properties.getSmtpHost() != null
            && !properties.getSmtpHost().isBlank()
            && properties.getEmailFrom() != null
            && !properties.getEmailFrom().isBlank();
    boolean smsReady =
        properties.getSmsAccountSid() != null
            && !properties.getSmsAccountSid().isBlank()
            && properties.getSmsAuthToken() != null
            && !properties.getSmsAuthToken().isBlank()
            && properties.getSmsFromNumber() != null
            && !properties.getSmsFromNumber().isBlank();
    return properties.isEnabled()
        && properties.getChallengePepper() != null
        && !properties.getChallengePepper().isBlank()
        && (emailReady || smsReady);
  }

  public boolean hasDeliveryChannel(User user) {
    return (emailConfigured() && user.getEmail() != null && !user.getEmail().isBlank())
        || (smsConfigured() && user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank());
  }

  @Transactional
  public AuthFlowResponse createChallenge(User user, String clientIp) {
    Instant since = Instant.now().minusSeconds(15 * 60L);
    if (challenges.countByUserIdAndCreatedAtAfter(user.getId(), since) >= 5
        || (clientIp != null
            && challenges.countByClientIpAndCreatedAtAfter(clientIp, since) >= 20)) {
      throw new AuthException(
          HttpStatus.TOO_MANY_REQUESTS,
          "OTP_LOGIN_RATE_LIMITED",
          "Too many verification attempts. Please try again later.");
    }
    if (!isReady()) {
      throw new AuthException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "OTP_NOT_CONFIGURED",
          "Inspector web verification is not configured.");
    }
    List<String> channels = new ArrayList<>();
    if (emailConfigured() && user.getEmail() != null && !user.getEmail().isBlank()) {
      channels.add("EMAIL");
    }
    if (smsConfigured() && user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) {
      channels.add("SMS");
    }
    if (channels.isEmpty()) {
      throw new AuthException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "OTP_CHANNEL_UNAVAILABLE",
          "No sign-in verification method is configured for this account.");
    }
    String rawChallenge = AuthCrypto.randomToken();
    challenges.save(
        new LoginOtpChallenge(
            hash("challenge:" + rawChallenge),
            user.getId(),
            clientIp,
            Instant.now().plusSeconds(CHALLENGE_TTL_SECONDS)));
    return new AuthFlowResponse(AuthStep.OTP_REQUIRED, null, null, 0, null, rawChallenge, channels);
  }

  @Transactional
  public void send(String rawChallenge, LoginOtpChannel channel, String clientIp) {
    LoginOtpChallenge challenge = requireChallenge(rawChallenge);
    if (!challengeIpMatches(challenge, clientIp)) {
      throw AuthException.invalidCredentials();
    }
    Instant now = Instant.now();
    if (challenge.getSendCount() == 0) {
      Instant since = now.minusSeconds(15 * 60L);
      if (challenges.countByUserIdAndCreatedAtAfter(challenge.getUserId(), since) > 5
          || (challenge.getClientIp() != null
              && challenges.countByClientIpAndCreatedAtAfter(challenge.getClientIp(), since)
                  > 20)) {
        throw new AuthException(
            HttpStatus.TOO_MANY_REQUESTS,
            "OTP_SEND_RATE_LIMITED",
            "Too many verification attempts. Please try again later.");
      }
    }
    if (challenge.getSendCount() >= 5) {
      throw new AuthException(
          HttpStatus.TOO_MANY_REQUESTS,
          "OTP_SEND_LIMIT_REACHED",
          "The sign-in code request limit has been reached.");
    }
    if (challenge.getLastSentAt() != null
        && challenge.getLastSentAt().plusSeconds(60).isAfter(now)) {
      throw new AuthException(
          HttpStatus.TOO_MANY_REQUESTS,
          "OTP_RESEND_TOO_SOON",
          "Please wait before requesting another sign-in code.");
    }
    User user =
        users
            .findDetailedById(challenge.getUserId())
            .filter(User::active)
            .orElseThrow(AuthException::invalidCredentials);
    String destination = destination(user, channel);
    if ((channel == LoginOtpChannel.EMAIL && !emailConfigured())
        || (channel == LoginOtpChannel.SMS && !smsConfigured())) {
      throw new AuthException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "OTP_CHANNEL_UNAVAILABLE",
          "The selected sign-in verification method is not configured.");
    }
    String code = String.format("%06d", RANDOM.nextInt(1_000_000));
    challenge.deliver(channel, hash("otp:" + code), now);
    try {
      delivery.deliver(channel, destination, code);
    } catch (RuntimeException exception) {
      throw new AuthException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "OTP_DELIVERY_FAILED",
          "The sign-in code could not be sent. Please try again later.");
    }
  }

  @Transactional(noRollbackFor = AuthException.class)
  public User verify(String rawChallenge, String code, String clientIp) {
    LoginOtpChallenge challenge = requireChallenge(rawChallenge);
    if (!challengeIpMatches(challenge, clientIp)) {
      throw AuthException.invalidCredentials();
    }
    if (!challenge.verify(hash("otp:" + code), Instant.now())) {
      challenges.saveAndFlush(challenge);
      throw AuthException.invalidCredentials();
    }
    return users
        .findDetailedById(challenge.getUserId())
        .filter(User::active)
        .orElseThrow(AuthException::invalidCredentials);
  }

  private boolean emailConfigured() {
    return properties.getSmtpHost() != null
        && !properties.getSmtpHost().isBlank()
        && properties.getEmailFrom() != null
        && !properties.getEmailFrom().isBlank();
  }

  private boolean smsConfigured() {
    return properties.getSmsAccountSid() != null
        && !properties.getSmsAccountSid().isBlank()
        && properties.getSmsAuthToken() != null
        && !properties.getSmsAuthToken().isBlank()
        && properties.getSmsFromNumber() != null
        && !properties.getSmsFromNumber().isBlank();
  }

  private boolean challengeIpMatches(LoginOtpChallenge challenge, String clientIp) {
    return challenge.getClientIp() == null || challenge.getClientIp().equals(clientIp);
  }

  private LoginOtpChallenge requireChallenge(String rawChallenge) {
    if (rawChallenge == null || rawChallenge.isBlank()) {
      throw AuthException.invalidCredentials();
    }
    return challenges
        .findForUpdateByChallengeHash(hash("challenge:" + rawChallenge))
        .filter(challenge -> challenge.activeAt(Instant.now()))
        .orElseThrow(AuthException::invalidCredentials);
  }

  private String destination(User user, LoginOtpChannel channel) {
    String value = channel == LoginOtpChannel.EMAIL ? user.getEmail() : user.getPhoneNumber();
    if (value == null || value.isBlank()) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST,
          "OTP_CHANNEL_UNAVAILABLE",
          "The selected sign-in verification method is not available.");
    }
    return value;
  }

  private String hash(String value) {
    String pepper = properties.getChallengePepper();
    if (pepper == null || pepper.isBlank()) {
      throw new IllegalStateException("AUTH_OTP_CHALLENGE_PEPPER is required when OTP is enabled");
    }
    return AuthCrypto.hmacSha256Hex(value, pepper);
  }
}

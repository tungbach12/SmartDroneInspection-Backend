package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.domain.OtpChallenge;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.LoginOtpChannel;
import com.smartdroneinspection.users.domain.enums.OtpChallengePurpose;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.OtpChallengeRepository;
import com.smartdroneinspection.users.repository.ProspectiveCustomerRegistrationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.security.AuthCrypto;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationOtpService {

  private static final long CHALLENGE_TTL_SECONDS = 600; // 10 minutes
  private static final SecureRandom RANDOM = new SecureRandom();

  private final OtpChallengeRepository challenges;
  private final UserRepository users;
  private final Optional<ProspectiveCustomerRegistrationRepository> prospectiveRegistrations;
  private final LoginOtpProperties properties;
  private final LoginOtpDelivery delivery;

  public RegistrationOtpService(
      OtpChallengeRepository challenges,
      UserRepository users,
      LoginOtpProperties properties,
      LoginOtpDelivery delivery) {
    this(challenges, users, Optional.empty(), properties, delivery);
  }

  @Autowired
  public RegistrationOtpService(
      OtpChallengeRepository challenges,
      UserRepository users,
      Optional<ProspectiveCustomerRegistrationRepository> prospectiveRegistrations,
      LoginOtpProperties properties,
      LoginOtpDelivery delivery) {
    this.challenges = challenges;
    this.users = users;
    this.prospectiveRegistrations = prospectiveRegistrations;
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

  @Transactional
  public RegistrationOtpSendResponse send(
      String destination, LoginOtpChannel channel, String clientIp) {
    if (destination == null || destination.isBlank()) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST, "DESTINATION_REQUIRED", "Email or phone number is required.");
    }
    String normalizedDest = destination.trim().toLowerCase(java.util.Locale.ROOT);

    if (channel == LoginOtpChannel.EMAIL) {
      users
          .findByNormalizedEmail(normalizedDest)
          .ifPresent(
              existing -> {
                boolean isRejectedProspect =
                    existing.getActorZone() == ActorZone.PROSPECT_CUSTOMER
                        && existing.getStatus() == UserStatus.DISABLED
                        && existing.getOrganizationId() == null
                        && prospectiveRegistrations
                            .flatMap(repo -> repo.findByUserId(existing.getId()))
                            .filter(reg -> "REJECTED".equals(reg.getStatus()))
                            .isPresent();
                if (!isRejectedProspect) {
                  throw new AuthException(
                      HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already registered.");
                }
              });
    }

    Instant now = Instant.now();
    Instant since = now.minusSeconds(15 * 60L);
    if (challenges.countByPurposeAndDestinationAndCreatedAtAfter(
            OtpChallengePurpose.REGISTRATION, normalizedDest, since)
        >= 5) {
      throw new AuthException(
          HttpStatus.TOO_MANY_REQUESTS,
          "OTP_SEND_RATE_LIMITED",
          "Too many OTP requests for this address. Please wait a few minutes.");
    }

    if (!isReady()) {
      throw new AuthException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "OTP_NOT_CONFIGURED",
          "Verification delivery service is not configured.");
    }

    String code = String.format("%06d", RANDOM.nextInt(1_000_000));
    String rawChallenge = AuthCrypto.randomToken();

    OtpChallenge challenge =
        OtpChallenge.registration(
            hash("reg-challenge:" + rawChallenge),
            normalizedDest,
            channel,
            hash("reg-otp:" + code),
            now.plusSeconds(CHALLENGE_TTL_SECONDS));

    challenges.save(challenge);

    try {
      delivery.deliver(channel, normalizedDest, code);
    } catch (RuntimeException ex) {
      throw new AuthException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "OTP_DELIVERY_FAILED",
          "Could not send OTP to the destination. Please try again.");
    }

    return new RegistrationOtpSendResponse(rawChallenge, channel.name(), CHALLENGE_TTL_SECONDS);
  }

  @Transactional(noRollbackFor = AuthException.class)
  public void verify(String rawChallenge, String code, String destination) {
    if (rawChallenge == null || rawChallenge.isBlank() || code == null || code.isBlank()) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST,
          "INVALID_OTP",
          "Verification code and challenge token are required.");
    }
    OtpChallenge challenge =
        challenges
            .findForUpdateByChallengeHashAndPurpose(
                hash("reg-challenge:" + rawChallenge), OtpChallengePurpose.REGISTRATION)
            .orElseThrow(
                () ->
                    new AuthException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_OTP",
                        "The verification session was not found or has expired."));

    String normalizedDest =
        destination == null ? "" : destination.trim().toLowerCase(java.util.Locale.ROOT);
    if (!challenge.getDestination().equalsIgnoreCase(normalizedDest)) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST, "INVALID_OTP", "Verification destination mismatch.");
    }

    if (!challenge.verify(hash("reg-otp:" + code.trim()), Instant.now())) {
      challenges.saveAndFlush(challenge);
      throw new AuthException(
          HttpStatus.BAD_REQUEST, "INVALID_OTP", "Mã xác thực OTP không đúng hoặc đã hết hạn.");
    }
    challenges.saveAndFlush(challenge);
  }

  private String hash(String input) {
    return AuthCrypto.hmacSha256Hex(input, properties.getChallengePepper());
  }

  public record RegistrationOtpSendResponse(
      String challengeToken, String channel, long expiresInSeconds) {}
}

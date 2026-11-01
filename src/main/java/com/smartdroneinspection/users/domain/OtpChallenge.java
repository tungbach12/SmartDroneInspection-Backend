package com.smartdroneinspection.users.domain;

import com.smartdroneinspection.users.domain.enums.LoginOtpChannel;
import com.smartdroneinspection.users.domain.enums.OtpChallengePurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {

  @Id @GeneratedValue private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", nullable = false, length = 32)
  private OtpChallengePurpose purpose;

  @Column(name = "challenge_hash", nullable = false, unique = true, length = 64)
  private String challengeHash;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "destination", length = 255)
  private String destination;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", length = 16)
  private LoginOtpChannel channel;

  @Column(name = "client_ip", length = 45)
  private String clientIp;

  @Column(name = "code_hash", length = 64)
  private String codeHash;

  @Column(name = "code_expires_at")
  private Instant codeExpiresAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "failed_attempts", nullable = false)
  private int failedAttempts;

  @Column(name = "send_count", nullable = false)
  private int sendCount;

  @Column(name = "last_sent_at")
  private Instant lastSentAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected OtpChallenge() {}

  private OtpChallenge(
      OtpChallengePurpose purpose,
      String challengeHash,
      UUID userId,
      String destination,
      LoginOtpChannel channel,
      String clientIp,
      String codeHash,
      Instant codeExpiresAt,
      Instant expiresAt) {
    this.purpose = purpose;
    this.challengeHash = challengeHash;
    this.userId = userId;
    this.destination = destination;
    this.channel = channel;
    this.clientIp = clientIp;
    this.codeHash = codeHash;
    this.codeExpiresAt = codeExpiresAt;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }

  public static OtpChallenge login(
      String challengeHash, UUID userId, String clientIp, Instant expiresAt) {
    return new OtpChallenge(
        OtpChallengePurpose.LOGIN,
        challengeHash,
        userId,
        null,
        null,
        clientIp,
        null,
        null,
        expiresAt);
  }

  public static OtpChallenge registration(
      String challengeHash,
      String destination,
      LoginOtpChannel channel,
      String codeHash,
      Instant expiresAt) {
    return new OtpChallenge(
        OtpChallengePurpose.REGISTRATION,
        challengeHash,
        null,
        normalizeDestination(destination),
        channel,
        null,
        codeHash,
        null,
        expiresAt);
  }

  public static OtpChallenge passwordReset(
      String challengeHash, String email, String codeHash, Instant expiresAt) {
    return new OtpChallenge(
        OtpChallengePurpose.PASSWORD_RESET,
        challengeHash,
        null,
        normalizeDestination(email),
        LoginOtpChannel.EMAIL,
        null,
        codeHash,
        null,
        expiresAt);
  }

  private static String normalizeDestination(String destination) {
    return destination.trim().toLowerCase(java.util.Locale.ROOT);
  }

  public boolean activeAt(Instant now) {
    boolean notExpired =
        purpose == OtpChallengePurpose.LOGIN ? expiresAt.isAfter(now) : !expiresAt.isBefore(now);
    return consumedAt == null && notExpired && failedAttempts < 5;
  }

  public boolean wasCreatedAfter(Instant instant) {
    return createdAt.isAfter(instant);
  }

  public void deliver(LoginOtpChannel channel, String codeHash, Instant now) {
    if (purpose != OtpChallengePurpose.LOGIN) {
      throw new IllegalStateException("Only login challenges support resend delivery");
    }
    if (sendCount >= 5 || (lastSentAt != null && lastSentAt.plusSeconds(60).isAfter(now))) {
      throw new IllegalStateException("OTP resend limit reached");
    }
    this.channel = channel;
    this.codeHash = codeHash;
    this.codeExpiresAt = now.plusSeconds(300);
    this.lastSentAt = now;
    this.sendCount++;
  }

  public boolean verify(String candidateCodeHash, Instant now) {
    if (!activeAt(now) || codeHash == null || candidateCodeHash == null) {
      return false;
    }
    if (codeExpiresAt != null && !codeExpiresAt.isAfter(now)) {
      return false;
    }
    if (purpose == OtpChallengePurpose.LOGIN
        && (codeExpiresAt == null || !codeExpiresAt.isAfter(now))) {
      return false;
    }
    boolean matches =
        purpose == OtpChallengePurpose.LOGIN
            ? MessageDigest.isEqual(
                codeHash.getBytes(StandardCharsets.UTF_8),
                candidateCodeHash.getBytes(StandardCharsets.UTF_8))
            : codeHash.equals(candidateCodeHash);
    if (!matches) {
      failedAttempts++;
      return false;
    }
    consumedAt = now;
    return true;
  }

  public UUID getId() {
    return id;
  }

  public OtpChallengePurpose getPurpose() {
    return purpose;
  }

  public String getChallengeHash() {
    return challengeHash;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getDestination() {
    return destination;
  }

  public LoginOtpChannel getChannel() {
    return channel;
  }

  public String getClientIp() {
    return clientIp;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getConsumedAt() {
    return consumedAt;
  }

  public int getFailedAttempts() {
    return failedAttempts;
  }

  public int getSendCount() {
    return sendCount;
  }

  public Instant getLastSentAt() {
    return lastSentAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

package com.smartdroneinspection.users.domain;

import com.smartdroneinspection.users.domain.enums.LoginOtpChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "registration_otp_challenges")
public class RegistrationOtpChallenge {

  @Id @GeneratedValue private UUID id;

  @Column(name = "challenge_hash", nullable = false, unique = true, length = 64)
  private String challengeHash;

  @Column(name = "destination", nullable = false, length = 255)
  private String destination;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false, length = 16)
  private LoginOtpChannel channel;

  @Column(name = "code_hash", nullable = false, length = 64)
  private String codeHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "failed_attempts", nullable = false)
  private int failedAttempts;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected RegistrationOtpChallenge() {}

  public RegistrationOtpChallenge(
      String challengeHash,
      String destination,
      LoginOtpChannel channel,
      String codeHash,
      Instant expiresAt) {
    this.challengeHash = challengeHash;
    this.destination = destination.trim().toLowerCase(java.util.Locale.ROOT);
    this.channel = channel;
    this.codeHash = codeHash;
    this.expiresAt = expiresAt;
    this.failedAttempts = 0;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getChallengeHash() {
    return challengeHash;
  }

  public String getDestination() {
    return destination;
  }

  public LoginOtpChannel getChannel() {
    return channel;
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

  public boolean isConsumed() {
    return consumedAt != null;
  }

  public boolean verify(String codeHash, Instant now) {
    if (consumedAt != null || expiresAt.isBefore(now)) {
      return false;
    }
    if (failedAttempts >= 5) {
      return false;
    }
    if (!this.codeHash.equals(codeHash)) {
      this.failedAttempts++;
      return false;
    }
    this.consumedAt = now;
    return true;
  }
}

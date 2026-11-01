package com.smartdroneinspection.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_otp_challenges")
public class PasswordResetOtpChallenge {

  @Id @GeneratedValue private UUID id;

  @Column(name = "challenge_hash", nullable = false, unique = true, length = 64)
  private String challengeHash;

  @Column(name = "email", nullable = false, length = 255)
  private String email;

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

  protected PasswordResetOtpChallenge() {}

  public PasswordResetOtpChallenge(
      String challengeHash, String email, String codeHash, Instant expiresAt) {
    this.challengeHash = challengeHash;
    this.email = email.trim().toLowerCase(java.util.Locale.ROOT);
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

  public String getEmail() {
    return email;
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

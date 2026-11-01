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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_otp_challenges")
public class LoginOtpChallenge {

  @Id @GeneratedValue private UUID id;

  @Column(name = "challenge_hash", nullable = false, unique = true, length = 64)
  private String challengeHash;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "client_ip", length = 45)
  private String clientIp;

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

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", length = 16)
  private LoginOtpChannel channel;

  @Column(name = "code_hash", length = 64)
  private String codeHash;

  @Column(name = "code_expires_at")
  private Instant codeExpiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected LoginOtpChallenge() {}

  public LoginOtpChallenge(String challengeHash, UUID userId, String clientIp, Instant expiresAt) {
    this.challengeHash = challengeHash;
    this.userId = userId;
    this.clientIp = clientIp;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }

  public boolean activeAt(Instant now) {
    return consumedAt == null && expiresAt.isAfter(now) && failedAttempts < 5;
  }

  public boolean wasCreatedAfter(Instant instant) {
    return createdAt.isAfter(instant);
  }

  public void deliver(LoginOtpChannel channel, String codeHash, Instant now) {
    if (sendCount >= 5 || (lastSentAt != null && lastSentAt.plusSeconds(60).isAfter(now))) {
      throw new IllegalStateException("OTP resend limit reached");
    }
    this.channel = channel;
    this.codeHash = codeHash;
    this.codeExpiresAt = now.plusSeconds(300);
    this.lastSentAt = now;
    this.sendCount++;
  }

  public boolean verify(String codeHash, Instant now) {
    if (!activeAt(now)
        || this.codeHash == null
        || this.codeExpiresAt == null
        || !codeExpiresAt.isAfter(now)) {
      return false;
    }
    if (!MessageDigest.isEqual(
        this.codeHash.getBytes(StandardCharsets.UTF_8),
        codeHash.getBytes(StandardCharsets.UTF_8))) {
      failedAttempts++;
      return false;
    }
    consumedAt = now;
    return true;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getClientIp() {
    return clientIp;
  }

  public LoginOtpChannel getChannel() {
    return channel;
  }

  public Instant getLastSentAt() {
    return lastSentAt;
  }

  public int getSendCount() {
    return sendCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

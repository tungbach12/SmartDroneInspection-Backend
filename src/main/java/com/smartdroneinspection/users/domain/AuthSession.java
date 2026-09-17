package com.smartdroneinspection.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "client_type", nullable = false, length = 16)
  private ClientType clientType;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoked_reason", length = 128)
  private String revokedReason;

  protected AuthSession() {}

  public AuthSession(User user, ClientType clientType, Instant expiresAt) {
    this.user = user;
    this.clientType = clientType;
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public ClientType getClientType() {
    return clientType;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean active(Instant now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }

  public void revoke(String reason) {
    if (revokedAt == null) {
      revokedAt = Instant.now();
      revokedReason = reason;
    }
  }
}

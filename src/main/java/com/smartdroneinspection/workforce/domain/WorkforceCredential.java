package com.smartdroneinspection.workforce.domain;

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

/**
 * One professional credential held by a workforce member.
 *
 * <p>Report 3 MF4-04 requires that work requiring a qualification is only assigned to someone whose
 * credential is currently valid. The verification lifecycle below is deliberately minimal: this
 * slice reads and checks credential status, it does not yet record review evidence.
 */
@Entity
@Table(name = "workforce_credentials")
public class WorkforceCredential {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "credential_type", nullable = false, length = 64)
  private String credentialType;

  @Column(length = 200)
  private String issuer;

  @Column(name = "credential_reference", length = 200)
  private String credentialReference;

  @Column(name = "issued_at")
  private Instant issuedAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private CredentialStatus status;

  @Column(name = "verified_by_user_id")
  private UUID verifiedByUserId;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "verification_reason", length = 2000)
  private String verificationReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected WorkforceCredential() {}

  public WorkforceCredential(
      UUID organizationId,
      UUID userId,
      String credentialType,
      String issuer,
      String credentialReference,
      Instant issuedAt,
      Instant expiresAt,
      CredentialStatus status) {
    this.organizationId = require(organizationId, "organizationId");
    this.userId = require(userId, "userId");
    this.credentialType = requireText(credentialType, "credentialType");
    this.issuer = issuer;
    this.credentialReference = credentialReference;
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
    this.status = status == null ? CredentialStatus.DRAFT : status;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  /**
   * Whether this credential currently authorises assignment at the given moment.
   *
   * <p>MF4-04 treats a missing credential as unverified rather than disqualifying, so callers must
   * distinguish "no record" from "record that does not qualify". A record that exists but is not
   * ACTIVE, or whose expiry has passed, does not qualify.
   */
  public boolean qualifiesAt(Instant moment) {
    if (status != CredentialStatus.ACTIVE) {
      return false;
    }
    return expiresAt == null || expiresAt.isAfter(moment);
  }

  private static UUID require(UUID value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getCredentialType() {
    return credentialType;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public CredentialStatus getStatus() {
    return status;
  }
}

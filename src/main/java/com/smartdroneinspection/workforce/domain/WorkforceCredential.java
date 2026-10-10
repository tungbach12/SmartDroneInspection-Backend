package com.smartdroneinspection.workforce.domain;

import com.smartdroneinspection.workforce.domain.enums.WorkforceCredentialStatus;
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

/** Persistence mapping for the V25 organization-owned workforce credential table. */
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
  private WorkforceCredentialStatus status;

  @Column(name = "evidence_id")
  private UUID evidenceId;

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

  public String getIssuer() {
    return issuer;
  }

  public String getCredentialReference() {
    return credentialReference;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public WorkforceCredentialStatus getStatus() {
    return status;
  }

  public UUID getEvidenceId() {
    return evidenceId;
  }

  public UUID getVerifiedByUserId() {
    return verifiedByUserId;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  public String getVerificationReason() {
    return verificationReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getRowVersion() {
    return rowVersion;
  }
}

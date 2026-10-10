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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Organization-owned credential record shared by Workforce management and MF4 eligibility checks.
 *
 * <p>Recording or reviewing a credential is an internal organization action; it does not assert
 * government, registry or professional-body verification.
 */
@Entity
@Table(name = "workforce_credentials")
public class WorkforceCredential {

  /** Default warning horizon used by compatibility callers without deployment configuration. */
  public static final Duration DEFAULT_EXPIRING_SOON_WINDOW = Duration.ofDays(60);

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "credential_type", nullable = false, length = 64)
  private String credentialType;

  @Column(name = "issuer", length = 200)
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

  /** Creates an organization-recorded credential awaiting its first internal review. */
  public WorkforceCredential(
      UUID organizationId,
      UUID userId,
      String credentialType,
      String issuer,
      String credentialReference,
      Instant issuedAt,
      Instant expiresAt,
      UUID evidenceId,
      UUID verifiedByUserId,
      Instant verifiedAt,
      String verificationReason) {
    this(
        organizationId,
        userId,
        credentialType,
        issuer,
        credentialReference,
        issuedAt,
        expiresAt,
        WorkforceCredentialStatus.PENDING_REVIEW,
        evidenceId,
        verifiedByUserId,
        verifiedAt,
        verificationReason);
  }

  /** MF4 constructor retaining the persisted status explicitly. */
  public WorkforceCredential(
      UUID organizationId,
      UUID userId,
      String credentialType,
      String issuer,
      String credentialReference,
      Instant issuedAt,
      Instant expiresAt,
      WorkforceCredentialStatus status,
      UUID evidenceId,
      UUID verifiedByUserId,
      Instant verifiedAt,
      String verificationReason) {
    this.organizationId = organizationId;
    this.userId = userId;
    this.credentialType = credentialType;
    this.issuer = issuer;
    this.credentialReference = credentialReference;
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
    this.status = status;
    this.evidenceId = evidenceId;
    this.verifiedByUserId = verifiedByUserId;
    this.verifiedAt = verifiedAt;
    this.verificationReason = verificationReason;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  /** Records an internal review decision and its audit attribution. */
  public void review(Status target, UUID reviewerId, Instant reviewedAt, String reason) {
    this.status = WorkforceCredentialStatus.valueOf(target.name());
    this.verifiedByUserId = reviewerId;
    this.verifiedAt = reviewedAt;
    this.verificationReason = reason;
    this.updatedAt = Instant.now();
  }

  /** Derives display status using the compatible default warning horizon. */
  public Status currentStatus(Instant at) {
    return currentStatus(at, DEFAULT_EXPIRING_SOON_WINDOW);
  }

  /** Derives display status from the recorded review state and configured expiry horizon. */
  public Status currentStatus(Instant at, Duration expiringSoonWindow) {
    if (status == WorkforceCredentialStatus.REJECTED
        || status == WorkforceCredentialStatus.SUSPENDED) {
      return Status.valueOf(status.name());
    }
    if (expiresAt != null && !expiresAt.isAfter(at)) {
      return Status.EXPIRED;
    }
    if (expiresAt != null
        && !expiresAt.isAfter(at.plus(expiringSoonWindow))
        && (status == WorkforceCredentialStatus.ACTIVE
            || status == WorkforceCredentialStatus.EXPIRING_SOON)) {
      return Status.EXPIRING_SOON;
    }
    return Status.valueOf(status.name());
  }

  /** Existing MF4 interim eligibility contract: only an ACTIVE, unexpired credential qualifies. */
  public boolean qualifiesAt(Instant moment) {
    return status == WorkforceCredentialStatus.ACTIVE
        && (expiresAt == null || expiresAt.isAfter(moment));
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

  /** API vocabulary for internal credential record management. */
  public enum Status {
    DRAFT,
    PENDING_REVIEW,
    ACTIVE,
    EXPIRING_SOON,
    EXPIRED,
    REJECTED,
    SUSPENDED
  }
}

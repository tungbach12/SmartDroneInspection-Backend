package com.smartdroneinspection.assets.domain;

import com.smartdroneinspection.assets.domain.enums.FlightPermitStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An aviation permit, approval or equivalent authorization record (MF1-07).
 *
 * <p>This records what an authority issued or refused. The platform never grants permission: an
 * organization attaches the permit reference it holds, and MF2-05 checks the reference's status and
 * validity window rather than deciding whether flight is lawful.
 */
@Entity
@Table(name = "flight_permits")
public class FlightPermit {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "asset_id")
  private UUID assetId;

  @Column(name = "area_reference", length = 200)
  private String areaReference;

  @Column(name = "permit_type", nullable = false, length = 64)
  private String permitType;

  @Column(name = "issuing_authority", length = 200)
  private String issuingAuthority;

  @Column(name = "permit_reference", length = 200)
  private String permitReference;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "geographic_scope", columnDefinition = "jsonb")
  private String geographicScope;

  @Column(name = "valid_from")
  private Instant validFrom;

  @Column(name = "valid_until")
  private Instant validUntil;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "conditions", columnDefinition = "jsonb")
  private String conditions;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private FlightPermitStatus status;

  @Column(name = "source_evidence_id")
  private UUID sourceEvidenceId;

  @Column(name = "reviewed_by_user_id")
  private UUID reviewedByUserId;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "review_reason", length = 2000)
  private String reviewReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected FlightPermit() {}

  public FlightPermit(
      UUID organizationId,
      UUID assetId,
      String permitType,
      String permitReference,
      String issuingAuthority) {
    this.organizationId = Objects.requireNonNull(organizationId, "Permit organization is required");
    if (permitType == null || permitType.isBlank()) {
      throw new IllegalArgumentException("Permit type must not be blank");
    }
    this.assetId = assetId;
    this.permitType = permitType;
    this.permitReference = permitReference;
    this.issuingAuthority = issuingAuthority;
    this.status = FlightPermitStatus.APPLICATION;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getAssetId() {
    return assetId;
  }

  public String getAreaReference() {
    return areaReference;
  }

  public String getPermitType() {
    return permitType;
  }

  public String getIssuingAuthority() {
    return issuingAuthority;
  }

  public String getPermitReference() {
    return permitReference;
  }

  public String getGeographicScope() {
    return geographicScope;
  }

  public Instant getValidFrom() {
    return validFrom;
  }

  public Instant getValidUntil() {
    return validUntil;
  }

  public String getConditions() {
    return conditions;
  }

  public FlightPermitStatus getStatus() {
    return status;
  }

  public UUID getSourceEvidenceId() {
    return sourceEvidenceId;
  }

  public UUID getReviewedByUserId() {
    return reviewedByUserId;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public String getReviewReason() {
    return reviewReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void defineValidity(Instant from, Instant until) {
    if (from != null && until != null && !until.isAfter(from)) {
      throw new IllegalArgumentException("Permit validity must end after it starts");
    }
    this.validFrom = from;
    this.validUntil = until;
    this.updatedAt = Instant.now();
  }

  public void grant(String reference, String authority, Instant validFrom, Instant validUntil) {
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("Granted permit requires a reference");
    }
    this.permitReference = reference;
    this.issuingAuthority = authority;
    defineValidity(validFrom, validUntil);
    this.status = FlightPermitStatus.ACTIVE;
    this.updatedAt = Instant.now();
  }

  public void markNotApplicable(String legalBasis) {
    this.status = FlightPermitStatus.NOT_APPLICABLE;
    this.reviewReason = legalBasis;
    this.updatedAt = Instant.now();
  }

  public void refuse(FlightPermitStatus outcome, String reason, Instant decidedAt) {
    if (outcome != FlightPermitStatus.REJECTED && outcome != FlightPermitStatus.REVOKED) {
      throw new IllegalArgumentException("A permit may only be rejected or revoked here");
    }
    this.status = outcome;
    this.reviewReason = reason;
    this.updatedAt = decidedAt;
  }

  /** Whether this record satisfies an MF2-05 authorization check at the given instant. */
  public boolean authorizesAt(Instant instant) {
    if (status == FlightPermitStatus.NOT_APPLICABLE) {
      return true;
    }
    if (status != FlightPermitStatus.ACTIVE
        || permitReference == null
        || permitReference.isBlank()) {
      return false;
    }
    if (validFrom != null && instant.isBefore(validFrom)) {
      return false;
    }
    return validUntil == null || !instant.isAfter(validUntil);
  }
}

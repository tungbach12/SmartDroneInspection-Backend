package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.ReadinessDecisionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An immutable record of the qualified review for one inspection preparation (MF2-07/08).
 *
 * <p>The snapshots and source hash bind this decision to the preparation and compliance evidence
 * that the reviewer actually saw. This entity records a decision; the service that creates it is
 * responsible for producing canonical snapshot data and a hash over the complete source basis.
 * Merely storing a non-empty hash here does not itself prove that the hash is correct.
 *
 * <p>The database CHECK constraint is the authority for decision vocabulary. This entity keeps the
 * same three values, records reviewer attribution and decision time, and does not let a returned
 * decision without a reason be stored as an unexplained rejection.
 */
@Entity
@Table(name = "inspection_readiness_decisions")
public class InspectionReadinessDecision {

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_id", nullable = false)
  private UUID inspectionId;

  @Column(name = "preparation_id")
  private UUID preparationId;

  @Column(name = "preparation_version")
  private Integer preparationVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ReadinessDecisionType decision;

  @Column(name = "reviewed_by_user_id", nullable = false)
  private UUID reviewedByUserId;

  @Column(name = "decided_at", nullable = false, updatable = false)
  private Instant decidedAt;

  @Column(length = 2000)
  private String reason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "permit_snapshot", columnDefinition = "jsonb")
  private String permitSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "credential_snapshot", columnDefinition = "jsonb")
  private String credentialSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "drone_document_snapshot", columnDefinition = "jsonb")
  private String droneDocumentSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "permit_snapshot_ids", columnDefinition = "jsonb")
  private String permitSnapshotIds;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "credential_snapshot_ids", columnDefinition = "jsonb")
  private String credentialSnapshotIds;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "drone_document_snapshot_ids", columnDefinition = "jsonb")
  private String droneDocumentSnapshotIds;

  @Column(name = "source_hash", nullable = false, length = 128)
  private String sourceHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected InspectionReadinessDecision() {}

  public InspectionReadinessDecision(
      UUID inspectionId,
      UUID preparationId,
      Integer preparationVersion,
      ReadinessDecisionType decision,
      UUID reviewedByUserId,
      String reason,
      String permitSnapshot,
      String credentialSnapshot,
      String droneDocumentSnapshot,
      String permitSnapshotIds,
      String credentialSnapshotIds,
      String droneDocumentSnapshotIds,
      String sourceHash) {
    this.inspectionId = Objects.requireNonNull(inspectionId, "Decision inspection is required");
    this.decision = Objects.requireNonNull(decision, "Readiness decision is required");
    this.reviewedByUserId =
        Objects.requireNonNull(reviewedByUserId, "Decision reviewer is required");
    if (preparationVersion != null && preparationVersion < 1) {
      throw new IllegalArgumentException("Preparation version must be positive");
    }
    if (sourceHash == null || sourceHash.isBlank()) {
      throw new IllegalArgumentException("Readiness decision source hash is required");
    }
    if (decision == ReadinessDecisionType.RETURNED && (reason == null || reason.isBlank())) {
      throw new IllegalArgumentException("Returning a preparation requires a reason");
    }

    this.preparationId = preparationId;
    this.preparationVersion = preparationVersion;
    this.reason = reason;
    this.permitSnapshot = permitSnapshot;
    this.credentialSnapshot = credentialSnapshot;
    this.droneDocumentSnapshot = droneDocumentSnapshot;
    this.permitSnapshotIds = permitSnapshotIds;
    this.credentialSnapshotIds = credentialSnapshotIds;
    this.droneDocumentSnapshotIds = droneDocumentSnapshotIds;
    this.sourceHash = sourceHash.trim();
    this.decidedAt = Instant.now();
    this.createdAt = decidedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getPreparationId() {
    return preparationId;
  }

  public Integer getPreparationVersion() {
    return preparationVersion;
  }

  public ReadinessDecisionType getDecision() {
    return decision;
  }

  public UUID getReviewedByUserId() {
    return reviewedByUserId;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public String getReason() {
    return reason;
  }

  public String getPermitSnapshot() {
    return permitSnapshot;
  }

  public String getCredentialSnapshot() {
    return credentialSnapshot;
  }

  public String getDroneDocumentSnapshot() {
    return droneDocumentSnapshot;
  }

  public String getPermitSnapshotIds() {
    return permitSnapshotIds;
  }

  public String getCredentialSnapshotIds() {
    return credentialSnapshotIds;
  }

  public String getDroneDocumentSnapshotIds() {
    return droneDocumentSnapshotIds;
  }

  public String getSourceHash() {
    return sourceHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

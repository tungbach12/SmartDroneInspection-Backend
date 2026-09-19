package com.smartdroneinspection.inspections.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_finding_candidates")
public class AiFindingCandidate {

  @Id @GeneratedValue private UUID id;

  @Column(name = "evidence_id", nullable = false)
  private UUID evidenceId;

  @Column(name = "model_name", nullable = false, length = 160)
  private String modelName;

  @Column(name = "model_version", nullable = false, length = 80)
  private String modelVersion;

  @Column(name = "predicted_label", nullable = false, length = 160)
  private String predictedLabel;

  @Column(nullable = false, precision = 6, scale = 5)
  private BigDecimal confidence;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "bounding_box", nullable = false, columnDefinition = "jsonb")
  private String boundingBox;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AiFindingCandidateStatus status;

  @Column(name = "reviewed_by_user_id")
  private UUID reviewedByUserId;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "rejection_reason", length = 2000)
  private String rejectionReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AiFindingCandidate() {}

  public AiFindingCandidate(
      UUID evidenceId,
      String modelName,
      String modelVersion,
      String predictedLabel,
      BigDecimal confidence,
      String boundingBox) {
    this.evidenceId = evidenceId;
    this.modelName = modelName;
    this.modelVersion = modelVersion;
    this.predictedLabel = predictedLabel;
    this.confidence = confidence;
    this.boundingBox = boundingBox;
    this.status = AiFindingCandidateStatus.PENDING;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getEvidenceId() {
    return evidenceId;
  }

  public AiFindingCandidateStatus getStatus() {
    return status;
  }
}

package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.AiFindingCandidateStatus;
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

/**
 * A model suggestion, never an official finding. Model name and version are stored with every
 * candidate so the provenance of a suggestion stays visible, and a candidate only becomes a finding
 * through an explicit human decision.
 */
@Entity
@Table(name = "ai_finding_candidates")
public class AiFindingCandidate {

  @Id @GeneratedValue private UUID id;

  @Column(name = "evidence_id", nullable = false)
  private UUID evidenceId;

  @Column(name = "inspection_id")
  private UUID inspectionId;

  @Column(name = "model_name", nullable = false, length = 160)
  private String modelName;

  @Column(name = "model_version", nullable = false, length = 80)
  private String modelVersion;

  @Column(name = "model_provider", length = 96)
  private String modelProvider;

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

  @Column(name = "raw_result_reference", length = 1000)
  private String rawResultReference;

  @Column(name = "processing_status", length = 24)
  private String processingStatus;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AiFindingCandidate() {}

  public AiFindingCandidate(
      UUID evidenceId,
      UUID inspectionId,
      String modelName,
      String modelVersion,
      String modelProvider,
      String predictedLabel,
      BigDecimal confidence,
      String boundingBox) {
    this.evidenceId = evidenceId;
    this.inspectionId = inspectionId;
    this.modelName = modelName;
    this.modelVersion = modelVersion;
    this.modelProvider = modelProvider;
    this.predictedLabel = predictedLabel;
    this.confidence = confidence;
    this.boundingBox = boundingBox;
    this.status = AiFindingCandidateStatus.PENDING;
    this.createdAt = Instant.now();
  }

  /** A rejection carries a reason so the decision stays attributable. */
  public void review(AiFindingCandidateStatus decision, UUID reviewerId, String reason) {
    if (status != AiFindingCandidateStatus.PENDING) {
      throw new IllegalStateException("Only pending candidates can be reviewed");
    }
    if (decision == AiFindingCandidateStatus.PENDING || reviewerId == null) {
      throw new IllegalArgumentException("A review decision and reviewer are required");
    }
    if (decision == AiFindingCandidateStatus.REJECTED && (reason == null || reason.isBlank())) {
      throw new IllegalArgumentException("A rejection reason is required");
    }
    status = decision;
    reviewedByUserId = reviewerId;
    reviewedAt = Instant.now();
    rejectionReason = reason;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEvidenceId() {
    return evidenceId;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public String getModelName() {
    return modelName;
  }

  public String getModelVersion() {
    return modelVersion;
  }

  public String getModelProvider() {
    return modelProvider;
  }

  public String getPredictedLabel() {
    return predictedLabel;
  }

  public BigDecimal getConfidence() {
    return confidence;
  }

  public String getBoundingBox() {
    return boundingBox;
  }

  public AiFindingCandidateStatus getStatus() {
    return status;
  }

  public UUID getReviewedByUserId() {
    return reviewedByUserId;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

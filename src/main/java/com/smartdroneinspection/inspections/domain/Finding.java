package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.domain.enums.FindingStatus;
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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * One observation about an inspection, whether recorded by an Inspector or proposed by a detector.
 */
@Entity
@Table(name = "findings")
@SQLRestriction("is_deleted = false")
public class Finding {

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_id", nullable = false)
  private UUID inspectionId;

  @Column(name = "evidence_id")
  private UUID evidenceId;

  @Column(name = "finding_code", nullable = false, length = 64)
  private String findingCode;

  @Column(name = "defect_label", nullable = false, length = 160)
  private String defectLabel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private FindingSeverity severity;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "bounding_box", columnDefinition = "jsonb")
  private String boundingBox;

  @Column(name = "technical_notes", nullable = false, length = 4000)
  private String technicalNotes;

  @Column(name = "recommended_action", length = 4000)
  private String recommendedAction;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private FindingStatus status;

  @Column(name = "model_name", length = 160)
  private String modelName;

  @Column(name = "confidence", precision = 6, scale = 5)
  private BigDecimal confidence;

  @Column(name = "forwarded_to_maintenance_at")
  private Instant forwardedToMaintenanceAt;

  @Column(name = "is_deleted", nullable = false)
  private boolean isDeleted;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Finding() {}

  public Finding(
      UUID inspectionId,
      UUID evidenceId,
      String findingCode,
      String defectLabel,
      FindingSeverity severity,
      String technicalNotes,
      String recommendedAction,
      String boundingBox,
      String modelName,
      BigDecimal confidence) {
    this.inspectionId = inspectionId;
    this.evidenceId = evidenceId;
    this.findingCode = findingCode;
    this.defectLabel = defectLabel;
    this.severity = severity;
    this.technicalNotes = technicalNotes;
    this.recommendedAction = recommendedAction;
    this.boundingBox = boundingBox;
    this.modelName = modelName;
    this.confidence = confidence;
    this.status = FindingStatus.PENDING;
    this.isDeleted = false;
    this.createdAt = Instant.now();
  }

  public void revise(
      UUID evidenceId,
      String defectLabel,
      FindingSeverity severity,
      String technicalNotes,
      String recommendedAction) {
    this.evidenceId = evidenceId;
    this.defectLabel = defectLabel;
    this.severity = severity;
    this.technicalNotes = technicalNotes;
    this.recommendedAction = recommendedAction;
    this.status = FindingStatus.PENDING;
  }

  public boolean isPendingAiSuggestion() {
    return status == FindingStatus.PENDING && modelName != null;
  }

  /** Keeps a tombstone while the evidence/report foreign-key identity remains stable. */
  public void delete() {
    if (this.isDeleted) {
      throw new IllegalStateException("This finding is already removed");
    }
    this.isDeleted = true;
  }

  public void restore() {
    if (!this.isDeleted) {
      throw new IllegalStateException("This finding was not removed");
    }
    this.isDeleted = false;
  }

  public void clearInspectorConfirmation() {
    status = FindingStatus.PENDING;
  }

  /** Marks this confirmed finding as handed to the maintenance workflow. */
  public void forwardToMaintenance() {
    if (!isOfficial()) {
      throw new IllegalStateException(
          "Only an Inspector-confirmed finding can be forwarded to maintenance");
    }
    if (forwardedToMaintenanceAt != null) {
      throw new IllegalStateException("This finding was already forwarded to maintenance");
    }
    forwardedToMaintenanceAt = Instant.now();
  }

  public void confirmByInspector() {
    this.status = FindingStatus.CONFIRMED;
  }

  public boolean isOfficial() {
    return status == FindingStatus.CONFIRMED && !isDeleted;
  }

  public boolean isDeleted() {
    return isDeleted;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getEvidenceId() {
    return evidenceId;
  }

  public FindingStatus getStatus() {
    return status;
  }

  public String getFindingCode() {
    return findingCode;
  }

  public String getDefectLabel() {
    return defectLabel;
  }

  public FindingSeverity getSeverity() {
    return severity;
  }

  public String getTechnicalNotes() {
    return technicalNotes;
  }

  public String getRecommendedAction() {
    return recommendedAction;
  }

  public String getBoundingBox() {
    return boundingBox;
  }

  public String getModelName() {
    return modelName;
  }

  public BigDecimal getConfidence() {
    return confidence;
  }

  public Instant getForwardedToMaintenanceAt() {
    return forwardedToMaintenanceAt;
  }

  public boolean isForwardedToMaintenance() {
    return forwardedToMaintenanceAt != null;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

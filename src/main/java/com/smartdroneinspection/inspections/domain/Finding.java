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
@SQLRestriction("deleted_at is null")
public class Finding {

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_id", nullable = false)
  private UUID inspectionId;

  @Column(name = "evidence_id")
  private UUID evidenceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private FindingStatus status;

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

  @Column(name = "model_name", length = 160)
  private String modelName;

  @Column(name = "model_version", length = 80)
  private String modelVersion;

  @Column(name = "model_provider", length = 96)
  private String modelProvider;

  @Column(name = "confidence", precision = 6, scale = 5)
  private BigDecimal confidence;

  @Column(name = "inspector_confirmed_at")
  private Instant inspectorConfirmedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "forwarded_to_maintenance_at")
  private Instant forwardedToMaintenanceAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

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
      String modelVersion,
      String modelProvider,
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
    this.modelVersion = modelVersion;
    this.modelProvider = modelProvider;
    this.confidence = confidence;
    this.status = FindingStatus.PENDING;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
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
    this.inspectorConfirmedAt = null;
    this.status = FindingStatus.PENDING;
    this.updatedAt = Instant.now();
  }

  public boolean isPendingAiSuggestion() {
    return status == FindingStatus.PENDING && modelName != null;
  }

  /** Keeps a tombstone while the evidence/report foreign-key identity remains stable. */
  public void delete() {
    if (deletedAt != null) {
      throw new IllegalStateException("This finding is already removed");
    }
    deletedAt = Instant.now();
    updatedAt = deletedAt;
  }

  public void restore() {
    if (deletedAt == null) {
      throw new IllegalStateException("This finding was not removed");
    }
    deletedAt = null;
    updatedAt = Instant.now();
  }

  public void clearInspectorConfirmation() {
    inspectorConfirmedAt = null;
    status = FindingStatus.PENDING;
    updatedAt = Instant.now();
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
    updatedAt = forwardedToMaintenanceAt;
  }

  public void confirmByInspector() {
    if (inspectorConfirmedAt != null) return;
    inspectorConfirmedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    status = FindingStatus.CONFIRMED;
    updatedAt = inspectorConfirmedAt;
  }

  public boolean isOfficial() {
    return inspectorConfirmedAt != null && status == FindingStatus.CONFIRMED;
  }

  public boolean isDeleted() {
    return deletedAt != null;
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

  public String getModelVersion() {
    return modelVersion;
  }

  public String getModelProvider() {
    return modelProvider;
  }

  public BigDecimal getConfidence() {
    return confidence;
  }

  public Instant getInspectorConfirmedAt() {
    return inspectorConfirmedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
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

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

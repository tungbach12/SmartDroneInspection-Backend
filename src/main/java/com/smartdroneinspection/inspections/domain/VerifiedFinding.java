package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.domain.enums.VerifiedFindingSource;
import com.smartdroneinspection.inspections.domain.enums.VerifiedFindingStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "verified_findings")
public class VerifiedFinding {

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_id", nullable = false)
  private UUID inspectionId;

  @Column(name = "evidence_id")
  private UUID evidenceId;

  @Column(name = "ai_candidate_id")
  private UUID aiCandidateId;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private VerifiedFindingSource source;

  @Column(name = "finding_code", nullable = false, length = 64)
  private String findingCode;

  @Column(name = "defect_label", nullable = false, length = 160)
  private String defectLabel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private FindingSeverity severity;

  @Column(name = "location_description", nullable = false, length = 1000)
  private String locationDescription;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "bounding_box", columnDefinition = "jsonb")
  private String boundingBox;

  @Column(name = "technical_notes", nullable = false, length = 4000)
  private String technicalNotes;

  @Column(name = "recommended_action", length = 4000)
  private String recommendedAction;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private VerifiedFindingStatus status;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected VerifiedFinding() {}

  public VerifiedFinding(
      UUID inspectionId,
      UUID evidenceId,
      UUID aiCandidateId,
      UUID createdByUserId,
      VerifiedFindingSource source,
      String findingCode,
      String defectLabel,
      FindingSeverity severity,
      String locationDescription,
      String technicalNotes,
      String recommendedAction) {
    this.inspectionId = inspectionId;
    this.evidenceId = evidenceId;
    this.aiCandidateId = aiCandidateId;
    this.createdByUserId = createdByUserId;
    this.source = source;
    this.findingCode = findingCode;
    this.defectLabel = defectLabel;
    this.severity = severity;
    this.locationDescription = locationDescription;
    this.technicalNotes = technicalNotes;
    this.recommendedAction = recommendedAction;
    this.status = VerifiedFindingStatus.OPEN;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public VerifiedFindingStatus getStatus() {
    return status;
  }
}

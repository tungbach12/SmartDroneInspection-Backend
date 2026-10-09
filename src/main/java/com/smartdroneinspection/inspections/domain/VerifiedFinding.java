package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.FindingDecision;
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
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A human-confirmed finding. Only findings that carry a human decision are official; an unreviewed
 * AI candidate never becomes one. {@code repairRequired} is what MF3 hands to MF4.
 */
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

  @Column(length = 200)
  private String component;

  @Column(length = 4000)
  private String description;

  @Column(name = "observed_condition", length = 4000)
  private String observedCondition;

  @Column(length = 24)
  private String priority;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "measurement", columnDefinition = "jsonb")
  private Map<String, String> measurement;

  @Enumerated(EnumType.STRING)
  @Column(length = 24)
  private FindingDecision decision;

  @Column(name = "decided_by_user_id")
  private UUID decidedByUserId;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(length = 4000)
  private String rationale;

  @Column(name = "repair_required", nullable = false)
  private boolean repairRequired;

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
      String recommendedAction,
      String boundingBox,
      String component,
      String description,
      String observedCondition,
      String priority,
      String measurement,
      boolean repairRequired) {
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
    this.boundingBox = boundingBox;
    this.component = component;
    this.description = description;
    this.observedCondition = observedCondition;
    this.priority = priority;
    // measurement is a jsonb column while the Inspector writes prose, so the text is stored as a
    // single summary entry and handed back unchanged through the API.
    this.measurement =
        measurement == null || measurement.isBlank() ? null : Map.of("summary", measurement);
    this.repairRequired = repairRequired;
    this.status = VerifiedFindingStatus.OPEN;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  /** MF3-09: the qualified reviewer records the final decision on this finding. */
  public void recordDecision(FindingDecision newDecision, UUID decidedByUserId, String rationale) {
    if (newDecision == null || decidedByUserId == null) {
      throw new IllegalArgumentException("A decision and a deciding reviewer are required");
    }
    this.decision = newDecision;
    this.decidedByUserId = decidedByUserId;
    this.decidedAt = Instant.now();
    this.rationale = rationale;
    this.updatedAt = decidedAt;
  }

  /** Only a confirmed finding enters official statistics and corrective-work scope. */
  public boolean isOfficial() {
    return decision == FindingDecision.CONFIRMED || decision == FindingDecision.MODIFIED;
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

  public UUID getAiCandidateId() {
    return aiCandidateId;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public VerifiedFindingSource getSource() {
    return source;
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

  public String getLocationDescription() {
    return locationDescription;
  }

  public String getTechnicalNotes() {
    return technicalNotes;
  }

  public String getRecommendedAction() {
    return recommendedAction;
  }

  public String getComponent() {
    return component;
  }

  public String getDescription() {
    return description;
  }

  public String getObservedCondition() {
    return observedCondition;
  }

  public String getPriority() {
    return priority;
  }

  public String getMeasurement() {
    return measurement == null ? null : measurement.get("summary");
  }

  public FindingDecision getDecision() {
    return decision;
  }

  public UUID getDecidedByUserId() {
    return decidedByUserId;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public boolean isRepairRequired() {
    return repairRequired;
  }

  public VerifiedFindingStatus getStatus() {
    return status;
  }
}

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

  @Column(name = "location_description", length = 1000)
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

  @Column(name = "corrective_action_required", nullable = false)
  private boolean correctiveActionRequired = true;

  @Column(name = "corrective_action_priority", length = 24)
  private String correctiveActionPriority;

  @Column(name = "corrective_action_due_at")
  private Instant correctiveActionDueAt;

  @Column(name = "corrective_action_reviewed_by_user_id")
  private UUID correctiveActionReviewedByUserId;

  @Column(name = "corrective_action_reviewed_at")
  private Instant correctiveActionReviewedAt;

  @Column(name = "corrective_action_rationale", length = 4000)
  private String correctiveActionRationale;

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
    this.decision = null;
    this.decidedByUserId = null;
    this.createdAt = Instant.now();
    this.decidedAt = null;
    this.updatedAt = createdAt;
  }

  /** Updates Inspector-provided fields while preserving any reviewer decision and audit. */
  public void reviseCandidateDetails(
      VerifiedFindingSource source,
      String defectLabel,
      FindingSeverity severity,
      String locationDescription,
      String technicalNotes,
      String recommendedAction,
      String component,
      String description,
      String observedCondition,
      String priority,
      String measurement,
      boolean repairRequired) {
    if (decision != null) {
      throw new IllegalStateException("A reviewed finding cannot be revised");
    }
    this.source = source;
    this.defectLabel = defectLabel;
    this.severity = severity;
    this.locationDescription = locationDescription;
    this.technicalNotes = technicalNotes;
    this.recommendedAction = recommendedAction;
    this.component = component;
    this.description = description;
    this.observedCondition = observedCondition;
    this.priority = priority;
    this.measurement =
        measurement == null || measurement.isBlank() ? null : Map.of("summary", measurement);
    this.repairRequired = repairRequired;
    this.updatedAt = Instant.now();
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
    this.correctiveActionRequired = newDecision != FindingDecision.REJECTED;
    this.correctiveActionReviewedByUserId = null;
    this.correctiveActionReviewedAt = null;
    this.repairRequired = false;
    this.updatedAt = decidedAt;
  }

  public void updateCorrectiveAction(
      UUID reviewerId,
      boolean correctiveActionRequired,
      String priority,
      Instant dueAt,
      String rationale) {
    if (reviewerId == null) {
      throw new IllegalArgumentException("A reviewer is required");
    }
    if (!hasReviewerDecision()
        || decision == FindingDecision.REJECTED
        || !decidedByUserId.equals(reviewerId)) {
      throw new IllegalStateException(
          "The finding decision reviewer must record its corrective action");
    }
    if (correctiveActionReviewedByUserId != null) {
      throw new IllegalStateException("Corrective action has already been recorded");
    }
    if (rationale == null || rationale.isBlank()) {
      throw new IllegalArgumentException("A corrective-action outcome rationale is required");
    }
    if (correctiveActionRequired && (priority == null || priority.isBlank())) {
      throw new IllegalArgumentException("MF3-12 requires a priority for corrective work");
    }
    if (!correctiveActionRequired && (priority != null || dueAt != null)) {
      throw new IllegalArgumentException(
          "Priority and due date cannot be set for a no-repair outcome");
    }
    if (dueAt != null && dueAt.isBefore(Instant.now())) {
      throw new IllegalArgumentException("The corrective-action due date must be in the future");
    }
    this.correctiveActionRequired = correctiveActionRequired;
    this.repairRequired = correctiveActionRequired;
    this.correctiveActionPriority = correctiveActionRequired ? priority : null;
    this.correctiveActionDueAt = correctiveActionRequired ? dueAt : null;
    this.correctiveActionReviewedByUserId = reviewerId;
    this.correctiveActionReviewedAt = Instant.now();
    this.correctiveActionRationale = rationale;
    this.updatedAt = correctiveActionReviewedAt;
  }

  /**
   * Inspector has reviewed this proposal; the independent reviewer still decides its disposition.
   */
  public void recordInspectorProposal(
      VerifiedFindingSource source,
      String defectLabel,
      FindingSeverity severity,
      String locationDescription,
      String technicalNotes,
      String recommendedAction,
      String component,
      String description,
      String observedCondition,
      String priority,
      String measurement,
      boolean repairRequired) {
    reviseCandidateDetails(
        source,
        defectLabel,
        severity,
        locationDescription,
        technicalNotes,
        recommendedAction,
        component,
        description,
        observedCondition,
        priority,
        measurement,
        repairRequired);
    decision = null;
    decidedByUserId = null;
    decidedAt = null;
    rationale = null;
    correctiveActionRequired = true;
  }

  /** Only an independent reviewer decision makes the finding official. */
  public boolean isOfficial() {
    return decidedByUserId != null
        && (decision == FindingDecision.CONFIRMED || decision == FindingDecision.MODIFIED);
  }

  public String getCorrectiveActionPriority() {
    return correctiveActionPriority;
  }

  public Instant getCorrectiveActionDueAt() {
    return correctiveActionDueAt;
  }

  public UUID getCorrectiveActionReviewedByUserId() {
    return correctiveActionReviewedByUserId;
  }

  public Instant getCorrectiveActionReviewedAt() {
    return correctiveActionReviewedAt;
  }

  public String getCorrectiveActionRationale() {
    return correctiveActionRationale;
  }

  public boolean isCorrectiveActionRequired() {
    return correctiveActionRequired;
  }

  public boolean isCorrectiveActionReviewed() {
    return correctiveActionReviewedByUserId != null && correctiveActionReviewedAt != null;
  }

  public boolean hasReviewerDecision() {
    return decidedByUserId != null && decision != null;
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
    return repairRequired && correctiveActionRequired;
  }

  public VerifiedFindingStatus getStatus() {
    return status;
  }
}

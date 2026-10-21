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
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * One observation about an inspection, whoever made it.
 *
 * <p>A finding is either a detector suggestion or something the Inspector saw. The difference
 * matters only for provenance - the model that produced it, its confidence and its box - so those
 * columns are nullable and a manual finding simply leaves them empty. What the workflow asks is
 * narrower: has the Inspector confirmed this or not. That is {@link FindingStatus}, and it is the
 * only thing that decides whether a finding is official.
 *
 * <p>Explicitly deleting an Inspector working-set finding is a tombstone rather than a state, so a
 * confirmed finding that was deleted still records that it was confirmed. Rejecting a pending AI
 * suggestion removes it; it is not retained as a finding-level audit row.
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

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

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

  @Column(name = "location_description", length = 1000)
  private String locationDescription;

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

  @Column(name = "confirmed_by_user_id")
  private UUID confirmedByUserId;

  @Column(name = "component", length = 200)
  private String component;

  @Column(name = "description", length = 4000)
  private String description;

  @Column(name = "observed_condition", length = 4000)
  private String observedCondition;

  @Column(name = "priority", length = 24)
  private String priority;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "measurement", columnDefinition = "jsonb")
  private Map<String, String> measurement;

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

  @Column(name = "repair_required", nullable = false)
  private boolean repairRequired;

  @Column(name = "deleted_by_user_id")
  private UUID deletedByUserId;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "deletion_reason", length = 2000)
  private String deletionReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected Finding() {}

  /**
   * A finding the detector produced.
   *
   * <p>The model provenance travels with it so the suggestion that was judged stays traceable, and
   * the finding starts {@code PENDING}: the Inspector has not looked at it yet.
   */
  public Finding(
      UUID inspectionId,
      UUID evidenceId,
      UUID createdByUserId,
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
      boolean repairRequired,
      String modelName,
      String modelVersion,
      String modelProvider,
      BigDecimal confidence) {
    this.inspectionId = inspectionId;
    this.evidenceId = evidenceId;
    this.createdByUserId = createdByUserId;
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
    this.measurement = normalizeMeasurement(measurement);
    this.repairRequired = repairRequired;
    this.modelName = modelName;
    this.modelVersion = modelVersion;
    this.modelProvider = modelProvider;
    this.confidence = confidence;
    this.status = FindingStatus.PENDING;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  /**
   * A finding the Inspector recorded from what they saw.
   *
   * <p>It has no model behind it, so the provenance columns stay empty rather than carrying a
   * placeholder.
   */
  public Finding(
      UUID inspectionId,
      UUID evidenceId,
      UUID createdByUserId,
      String findingCode,
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
    this(
        inspectionId,
        evidenceId,
        createdByUserId,
        findingCode,
        defectLabel,
        severity,
        locationDescription,
        technicalNotes,
        recommendedAction,
        null,
        component,
        description,
        observedCondition,
        priority,
        measurement,
        repairRequired,
        null,
        null,
        null,
        null);
  }

  /** Updates Inspector-provided fields while preserving the confirmation and the audit trail. */
  public void setLocationDescription(String locationDescription) {
    String normalized =
        locationDescription == null || locationDescription.isBlank()
            ? null
            : locationDescription.trim();
    if (java.util.Objects.equals(this.locationDescription, normalized)) return;
    this.locationDescription = normalized;
    this.inspectorConfirmedAt = null;
    this.updatedAt = Instant.now();
  }

  /**
   * The Inspector's edit of a finding.
   *
   * <p>Editing changes the content, so the earlier acceptance no longer speaks for it: the finding
   * goes back to {@code PENDING} and the Inspector has to accept it again. That is what keeps a
   * confirmation a statement about the content it was given for.
   */
  public void revise(
      UUID evidenceId,
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
    this.evidenceId = evidenceId;
    this.defectLabel = defectLabel;
    this.severity = severity;
    this.locationDescription = locationDescription;
    this.technicalNotes = technicalNotes;
    this.recommendedAction = recommendedAction;
    this.component = component;
    this.description = description;
    this.observedCondition = observedCondition;
    this.priority = priority;
    this.measurement = normalizeMeasurement(measurement);
    this.repairRequired = repairRequired;
    this.inspectorConfirmedAt = null;
    this.status = FindingStatus.PENDING;
    this.updatedAt = Instant.now();
  }

  /** Whether this unconfirmed finding came from AI and should be rejected, not audit-deleted. */
  public boolean isPendingAiSuggestion() {
    return status == FindingStatus.PENDING && modelName != null;
  }

  /**
   * MF3-06: removing a finding keeps who removed it, when and why.
   *
   * <p>The row stays for audit and reads filter it out. A removed finding can be restored, which
   * clears the tombstone and returns it to the Inspector as unconfirmed.
   */
  public void delete(UUID actorId, String reason) {
    if (actorId == null) {
      throw new IllegalArgumentException("An actor is required to delete a finding");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("A deletion reason is required");
    }
    this.deletedByUserId = actorId;
    this.deletedAt = Instant.now();
    this.deletionReason = reason;
    this.updatedAt = deletedAt;
  }

  /** Puts a removed finding back in front of the Inspector, as it was before it was removed. */
  public void restore() {
    if (deletedAt == null) {
      throw new IllegalStateException("This finding was not removed");
    }
    this.deletedByUserId = null;
    this.deletedAt = null;
    this.deletionReason = null;
    this.updatedAt = Instant.now();
  }

  public void clearInspectorConfirmation() {
    this.inspectorConfirmedAt = null;
    this.confirmedByUserId = null;
    this.status = FindingStatus.PENDING;
    this.updatedAt = Instant.now();
  }

  /**
   * MF3-12/13: the Organization Admin forwards this official finding to Maintenance as a repair
   * candidate.
   */
  public void forwardToMaintenance(UUID reviewerId) {
    if (reviewerId == null) {
      throw new IllegalArgumentException("A reviewer is required to forward to maintenance");
    }
    if (!isOfficial()) {
      throw new IllegalStateException(
          "Only an Inspector-confirmed finding can be forwarded to maintenance");
    }
    this.correctiveActionRequired = true;
    this.repairRequired = true;
    this.correctiveActionReviewedByUserId = reviewerId;
    this.correctiveActionReviewedAt = Instant.now();
    this.updatedAt = this.correctiveActionReviewedAt;
  }

  /** The Inspector confirms the finding: it becomes official and may enter a report. */
  public void confirmByInspector(UUID actorId) {
    if (this.inspectorConfirmedAt != null) return;
    this.inspectorConfirmedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    this.confirmedByUserId = actorId;
    this.status = FindingStatus.CONFIRMED;
    this.updatedAt = inspectorConfirmedAt;
  }

  public UUID getConfirmedByUserId() {
    return confirmedByUserId;
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
    if (!isOfficial()) {
      throw new IllegalStateException(
          "The Organization Admin can only record corrective action for an Inspector-confirmed finding");
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
   * Official: the Inspector confirmed it, so it may enter a report and carry a corrective outcome.
   */
  public boolean isOfficial() {
    return inspectorConfirmedAt != null && status == FindingStatus.CONFIRMED;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public boolean isCorrectiveActionRequired() {
    return correctiveActionRequired;
  }

  public boolean isCorrectiveActionReviewed() {
    return correctiveActionReviewedByUserId != null;
  }

  /** Clears a corrective outcome so a republished report does not inherit the previous one. */
  public void clearCorrectiveActionOutcome() {
    this.correctiveActionRequired = true;
    this.correctiveActionPriority = null;
    this.correctiveActionDueAt = null;
    this.correctiveActionReviewedByUserId = null;
    this.correctiveActionReviewedAt = null;
    this.correctiveActionRationale = null;
    this.repairRequired = false;
    this.updatedAt = Instant.now();
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

  public UUID getCreatedByUserId() {
    return createdByUserId;
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

  public String getLocationDescription() {
    return locationDescription;
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

  public Map<String, String> getMeasurement() {
    return measurement;
  }

  /** The measurement as the Inspector wrote it, for the API and the report. */
  public String getMeasurementSummary() {
    return measurement == null ? null : measurement.get("summary");
  }

  public boolean isRepairRequired() {
    return repairRequired;
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

  public UUID getDeletedByUserId() {
    return deletedByUserId;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public String getDeletionReason() {
    return deletionReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /**
   * measurement is a jsonb column while the Inspector writes prose, so the text is stored as a
   * single summary entry and handed back unchanged through the API.
   */
  private static Map<String, String> normalizeMeasurement(String measurement) {
    return measurement == null || measurement.isBlank() ? null : Map.of("summary", measurement);
  }
}

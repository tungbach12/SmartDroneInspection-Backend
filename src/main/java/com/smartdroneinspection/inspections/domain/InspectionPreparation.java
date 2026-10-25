package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.InspectionPreparationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One reviewable version of an inspector's mission preparation (MF2-03, MF2-06).
 *
 * <p>Versions are immutable once submitted rather than edited in place, because MF2-07 approves a
 * specific preparation. Editing a submitted row in place would leave an approval pointing at
 * content nobody reviewed, so a returned version is reopened as a draft and the next submission
 * becomes a new version. The uniqueness of {@code (inspection_id, preparation_version)} is what
 * makes that history readable.
 *
 * <p>There is deliberately no organization column: the table reaches tenant scope through {@code
 * inspection_id}, and every repository query for it joins or filters on the inspection. The JSON
 * columns hold raw documents rather than mapped types, matching the rest of the schema, because the
 * shot-list shape is a contract with the mobile client rather than a domain invariant.
 */
@Entity
@Table(
    name = "inspection_preparations",
    uniqueConstraints = @UniqueConstraint(columnNames = {"inspection_id", "preparation_version"}))
public class InspectionPreparation {

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_id", nullable = false)
  private UUID inspectionId;

  @Column(name = "preparation_version", nullable = false)
  private int preparationVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "shot_list", nullable = false, columnDefinition = "jsonb")
  private String shotList = "[]";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "evidence_types", columnDefinition = "jsonb")
  private String evidenceTypes;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "access_constraints", columnDefinition = "jsonb")
  private String accessConstraints;

  @Column(name = "safety_observations", length = 4000)
  private String safetyObservations;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "permit_document_references", columnDefinition = "jsonb")
  private String permitDocumentReferences;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "reviewed_by_user_id")
  private UUID reviewedByUserId;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "review_reason", length = 2000)
  private String reviewReason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private InspectionPreparationStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected InspectionPreparation() {}

  public InspectionPreparation(UUID inspectionId, int preparationVersion) {
    this.inspectionId = Objects.requireNonNull(inspectionId, "Preparation inspection is required");
    if (preparationVersion < 1) {
      throw new IllegalArgumentException("Preparation version must start at 1");
    }
    this.preparationVersion = preparationVersion;
    this.status = InspectionPreparationStatus.DRAFT;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public InspectionPreparation nextDraftVersion(int nextVersion) {
    InspectionPreparation next = new InspectionPreparation(inspectionId, nextVersion);
    next.shotList = shotList;
    next.evidenceTypes = evidenceTypes;
    next.accessConstraints = accessConstraints;
    next.safetyObservations = safetyObservations;
    return next;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public int getPreparationVersion() {
    return preparationVersion;
  }

  public String getShotList() {
    return shotList;
  }

  public String getEvidenceTypes() {
    return evidenceTypes;
  }

  public String getAccessConstraints() {
    return accessConstraints;
  }

  public String getSafetyObservations() {
    return safetyObservations;
  }

  public String getPermitDocumentReferences() {
    return permitDocumentReferences;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
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

  public InspectionPreparationStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public boolean belongsTo(UUID candidateInspectionId) {
    return inspectionId.equals(candidateInspectionId);
  }

  /**
   * Whether this version may still be edited.
   *
   * <p>Only {@code DRAFT}. A returned preparation has to be reopened with {@link #startRevision()}
   * first, because otherwise {@code RETURNED} would behave exactly like {@code DRAFT} and the
   * reviewer could no longer tell "sent back, waiting for rework" from "never submitted".
   */
  public boolean isEditable() {
    return status == InspectionPreparationStatus.DRAFT;
  }

  /** Records the component shot-list (MF2-03). */
  public void recordShotList(String shotListJson) {
    requireEditable();
    this.shotList = shotListJson == null || shotListJson.isBlank() ? "[]" : shotListJson.trim();
    touch();
  }

  /** Records the evidence modalities the inspection needs. */
  public void recordEvidenceTypes(String evidenceTypesJson) {
    requireEditable();
    this.evidenceTypes = blankToNull(evidenceTypesJson);
    touch();
  }

  /** Records access limitations the field session must work around (MF2-03). */
  public void recordAccessConstraints(String accessConstraintsJson) {
    requireEditable();
    this.accessConstraints = blankToNull(accessConstraintsJson);
    touch();
  }

  /** Records known hazards (MF2-03). Required before submission by MF2-06. */
  public void recordSafetyObservations(String safetyObservationText) {
    requireEditable();
    this.safetyObservations = blankToNull(safetyObservationText);
    touch();
  }

  /**
   * Links the issued permit and credential documents an administrator attaches (MF2-04).
   *
   * <p>Unlike the inspector's fields this does not require a draft: an administrator may still
   * complete a missing link while the preparation is with the reviewer. It is refused once the
   * preparation is {@code READY}, because readiness is a decision about a particular compliance
   * basis and silently changing that basis afterwards would leave the decision unearned.
   */
  public void recordPermitDocumentReferences(String permitReferencesJson) {
    if (status == InspectionPreparationStatus.READY) {
      throw new IllegalStateException("An approved preparation cannot take new permit references");
    }
    this.permitDocumentReferences = blankToNull(permitReferencesJson);
    touch();
  }

  /**
   * Attributable submission of the preparation (MF2-06).
   *
   * <p>Both a shot-list and safety observations are required. MF2-06 makes the inspector read the
   * restrictions and record the acknowledgment, and MF2-07 may only approve a preparation that
   * carries them; refusing here keeps an incomplete version from ever reaching the reviewer.
   */
  public void submit() {
    if (!isEditable()) {
      throw new IllegalStateException("Only a draft or returned preparation may be submitted");
    }
    if (isBlankJsonArray(shotList)) {
      throw new IllegalStateException("A component shot-list is required before submitting");
    }
    if (safetyObservations == null || safetyObservations.isBlank()) {
      throw new IllegalStateException("Safety observations are required before submitting");
    }
    this.status = InspectionPreparationStatus.SUBMITTED;
    this.submittedAt = Instant.now();
    this.updatedAt = submittedAt;
  }

  /** The reviewer accepted this submission (MF2-07). */
  public void approve(UUID reviewerUserId) {
    Objects.requireNonNull(reviewerUserId, "Reviewer is required");
    requireSubmitted("marked ready");
    this.status = InspectionPreparationStatus.READY;
    this.reviewedByUserId = reviewerUserId;
    this.reviewedAt = Instant.now();
    this.reviewReason = null;
    this.updatedAt = reviewedAt;
  }

  public void markReady() {
    requireSubmitted("marked ready");
    this.status = InspectionPreparationStatus.READY;
    touch();
  }

  /** The prior approval no longer governs after the inspection requests another field visit. */
  public void supersede(String reason) {
    if (status != InspectionPreparationStatus.READY) {
      throw new IllegalStateException("Only a ready preparation can be superseded");
    }
    this.status = InspectionPreparationStatus.SUPERSEDED;
    if (reason != null && !reason.isBlank()) {
      this.reviewReason = reason.trim();
    }
    touch();
  }

  public void markSuperseded() {
    supersede(null);
  }

  /** The reviewer sent this submission back for rework (MF2-07). */
  public void returnForRevision(UUID reviewerUserId, String reason) {
    Objects.requireNonNull(reviewerUserId, "Reviewer is required");
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("A return decision requires a reason");
    }
    String trimmedReason = reason.trim();
    if (trimmedReason.length() > 2000) {
      throw new IllegalArgumentException("Review reason must not exceed 2000 characters");
    }
    requireSubmitted("returned");
    this.status = InspectionPreparationStatus.RETURNED;
    this.reviewedByUserId = reviewerUserId;
    this.reviewedAt = Instant.now();
    this.reviewReason = trimmedReason;
    this.updatedAt = reviewedAt;
  }

  public void markReturned() {
    requireSubmitted("returned");
    this.status = InspectionPreparationStatus.RETURNED;
    touch();
  }

  /** Reopens a returned version so the inspector can revise and submit it again. */
  public void startRevision() {
    if (status != InspectionPreparationStatus.RETURNED) {
      throw new IllegalStateException("Only a returned preparation may be revised");
    }
    this.status = InspectionPreparationStatus.DRAFT;
    this.submittedAt = null;
    this.reviewedByUserId = null;
    this.reviewedAt = null;
    this.reviewReason = null;
    touch();
  }

  private void requireSubmitted(String action) {
    if (status != InspectionPreparationStatus.SUBMITTED) {
      throw new IllegalStateException("Only a submitted preparation may be " + action);
    }
  }

  private void requireEditable() {
    if (!isEditable()) {
      throw new IllegalStateException("Only a draft preparation may be edited");
    }
  }

  private void touch() {
    this.updatedAt = Instant.now();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /**
   * Whether a JSON document holds no shots.
   *
   * <p>The shot-list shape is agreed with the mobile client, so this checks the two cases that mean
   * "nothing captured" without pretending to parse every shape: a missing document, or an array
   * that is empty or only whitespace.
   */
  private static boolean isBlankJsonArray(String json) {
    if (json == null || json.isBlank()) {
      return true;
    }
    String trimmed = json.trim();
    return "[]".equals(trimmed) || "[ ]".equals(trimmed);
  }
}

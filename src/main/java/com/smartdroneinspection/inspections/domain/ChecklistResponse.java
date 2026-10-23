package com.smartdroneinspection.inspections.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * MF2-11: the Inspector's recorded answer to one checklist item.
 *
 * <p>The response is the Inspector's own observation. Its {@code notes} are kept exactly as
 * written, in whatever language the Inspector used, because a note is evidence of what was seen on
 * site rather than system copy. Only the checklist item's own text is localized, and that happens
 * when the checklist is read.
 */
@Entity
@Table(name = "checklist_responses")
public class ChecklistResponse {

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_id", nullable = false)
  private UUID inspectionId;

  @Column(name = "checklist_item_id", nullable = false)
  private UUID checklistItemId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_value", nullable = false, columnDefinition = "jsonb")
  private String responseValue;

  @Column(length = 4000)
  private String notes;

  @Column(name = "completed_by_user_id", nullable = false)
  private UUID completedByUserId;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ChecklistResponse() {}

  public ChecklistResponse(
      UUID inspectionId,
      UUID checklistItemId,
      String responseValue,
      String notes,
      UUID completedByUserId) {
    this.inspectionId = inspectionId;
    this.checklistItemId = checklistItemId;
    this.responseValue = responseValue;
    this.notes = notes;
    this.completedByUserId = completedByUserId;
    this.completedAt = Instant.now();
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getChecklistItemId() {
    return checklistItemId;
  }

  public String getResponseValue() {
    return responseValue;
  }

  public String getNotes() {
    return notes;
  }

  public UUID getCompletedByUserId() {
    return completedByUserId;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  /** Replaces the recorded answer, keeping the note exactly as the Inspector rewrote it. */
  public void update(String responseValue, String notes, UUID completedByUserId) {
    this.responseValue = responseValue;
    this.notes = notes;
    this.completedByUserId = completedByUserId;
    this.completedAt = Instant.now();
    this.updatedAt = Instant.now();
  }
}

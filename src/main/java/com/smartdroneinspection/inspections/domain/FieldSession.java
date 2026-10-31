package com.smartdroneinspection.inspections.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A field session under one inspection. MF3 evidence may be attributed to the session it came from.
 *
 * <p>The status vocabulary is a {@code String} rather than an enum so this class does not widen the
 * migration contract that {@code ck_field_sessions_status} already fixed; the values below are the
 * only ones the database accepts.
 */
@Entity
@Table(name = "field_sessions")
public class FieldSession {

  public static final String PLANNED = "PLANNED";
  public static final String IN_PROGRESS = "IN_PROGRESS";
  public static final String FIELD_COMPLETED = "FIELD_COMPLETED";

  /** Deprecated: field sessions are aborted rather than postponed. */
  @Deprecated public static final String POSTPONED = "POSTPONED";

  public static final String ABORTED = "ABORTED";

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_id", nullable = false)
  private UUID inspectionId;

  @Column(nullable = false, length = 24)
  private String status;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "postponement_reason", length = 2000)
  private String postponementReason;

  @Column(name = "abort_reason", length = 2000)
  private String abortReason;

  @Column(name = "outcome_notes", length = 4000)
  private String outcomeNotes;

  @Column(name = "pre_flight_checklist_note", length = 2000)
  private String preFlightChecklistNote;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected FieldSession() {}

  public FieldSession(UUID inspectionId, String status, Instant startedAt) {
    this.inspectionId = inspectionId;
    this.status = status;
    this.startedAt = startedAt;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public void recordPreFlightChecklistNote(String note) {
    if (note == null || note.isBlank()) {
      throw new IllegalArgumentException("Pre-flight checklist note is required");
    }
    this.preFlightChecklistNote = note.trim();
  }

  public void start(Instant startedAt) {
    if (!IN_PROGRESS.equals(status) && !PLANNED.equals(status)) {
      throw new IllegalStateException("Only a planned or in-progress session can be started");
    }
    this.startedAt = Objects.requireNonNull(startedAt, "Session start time is required");
    this.status = IN_PROGRESS;
    this.updatedAt = startedAt;
  }

  public void abort(String abortReason) {
    requireOpenSession();
    this.abortReason = requireReason(abortReason);
    this.status = ABORTED;
    this.endedAt = Instant.now();
    this.updatedAt = endedAt;
  }

  public void complete(String outcomeNotes) {
    if (!IN_PROGRESS.equals(status)) {
      throw new IllegalStateException("Only an in-progress session can be completed");
    }
    this.outcomeNotes = requireReason(outcomeNotes);
    this.status = FIELD_COMPLETED;
    this.endedAt = Instant.now();
    this.updatedAt = endedAt;
  }

  private void requireOpenSession() {
    if (!IN_PROGRESS.equals(status)) {
      throw new IllegalStateException("Only a session in progress can be closed");
    }
  }

  private static String requireReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("A reason is required");
    }
    return reason.trim();
  }

  public static FieldSession opening(UUID inspectionId) {
    return new FieldSession(inspectionId, PLANNED, null);
  }

  public boolean isOpen() {
    return PLANNED.equals(status) || IN_PROGRESS.equals(status);
  }

  public boolean isInProgress() {
    return IN_PROGRESS.equals(status);
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public String getStatus() {
    return status;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public String getPostponementReason() {
    return postponementReason;
  }

  public String getAbortReason() {
    return abortReason;
  }

  public String getOutcomeNotes() {
    return outcomeNotes;
  }

  public String getPreFlightChecklistNote() {
    return preFlightChecklistNote;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

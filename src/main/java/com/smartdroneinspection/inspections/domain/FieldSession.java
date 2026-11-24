package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.api.dto.FieldSessionChecklistDto.ChecklistItemSnapshot;
import com.smartdroneinspection.inspections.api.dto.FieldSessionChecklistDto.PreFlightConditions;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

  @Column(name = "abort_reason", length = 2000)
  private String abortReason;

  @Column(name = "outcome_notes", length = 4000)
  private String outcomeNotes;

  @Column(name = "pre_flight_checklist_note", length = 2000)
  private String preFlightChecklistNote;

  @Column(name = "session_date")
  private LocalDate sessionDate;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "pre_flight_checklist_snapshot", columnDefinition = "jsonb", nullable = false)
  private List<ChecklistItemSnapshot> preFlightChecklistSnapshot = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "pre_flight_conditions", columnDefinition = "jsonb")
  private PreFlightConditions preFlightConditions;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "post_flight_checklist_snapshot", columnDefinition = "jsonb", nullable = false)
  private List<ChecklistItemSnapshot> postFlightChecklistSnapshot = new ArrayList<>();

  @OneToOne(
      mappedBy = "fieldSession",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private FieldSessionTelemetry telemetry;

  @Column(name = "hung_notified_at")
  private Instant hungNotifiedAt;

  @Column(name = "aborted_by_user_id")
  private UUID abortedByUserId;

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

  public void recordPreFlightDetails(
      LocalDate sessionDate, List<ChecklistItemSnapshot> snapshot, PreFlightConditions conditions) {
    this.sessionDate = sessionDate != null ? sessionDate : LocalDate.now();
    this.preFlightChecklistSnapshot =
        snapshot == null ? new ArrayList<>() : new ArrayList<>(snapshot);
    this.preFlightConditions = conditions;
  }

  public void recordPostFlightSnapshot(List<ChecklistItemSnapshot> snapshot) {
    this.postFlightChecklistSnapshot =
        snapshot == null ? new ArrayList<>() : new ArrayList<>(snapshot);
  }

  public void start(Instant startedAt) {
    if (!IN_PROGRESS.equals(status) && !PLANNED.equals(status)) {
      throw new IllegalStateException("Only a planned or in-progress session can be started");
    }
    this.startedAt = Objects.requireNonNull(startedAt, "Session start time is required");
    if (this.sessionDate == null) {
      this.sessionDate = startedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate();
    }
    this.status = IN_PROGRESS;
    this.updatedAt = startedAt;
  }

  public void abort(String abortReason) {
    abort(abortReason, null);
  }

  public void abort(String abortReason, UUID actorUserId) {
    requireOpenSession();
    this.abortReason = requireReason(abortReason);
    this.abortedByUserId = actorUserId;
    this.status = ABORTED;
    this.endedAt = Instant.now();
    this.updatedAt = endedAt;
  }

  public void markHungNotified(Instant at) {
    this.hungNotifiedAt = Objects.requireNonNull(at, "Timestamp is required");
    this.updatedAt = Instant.now();
  }

  public Instant getHungNotifiedAt() {
    return hungNotifiedAt;
  }

  public UUID getAbortedByUserId() {
    return abortedByUserId;
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

  /** Retained for response compatibility; postponement is no longer a field-session state. */
  public String getPostponementReason() {
    return null;
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

  public LocalDate getSessionDate() {
    return sessionDate;
  }

  public List<ChecklistItemSnapshot> getPreFlightChecklistSnapshot() {
    return preFlightChecklistSnapshot == null ? List.of() : List.copyOf(preFlightChecklistSnapshot);
  }

  public PreFlightConditions getPreFlightConditions() {
    return preFlightConditions;
  }

  public List<ChecklistItemSnapshot> getPostFlightChecklistSnapshot() {
    return postFlightChecklistSnapshot == null
        ? List.of()
        : List.copyOf(postFlightChecklistSnapshot);
  }

  public boolean hasFlightReport() {
    return hasPersistedFlightReport();
  }

  public void recordFlightReport(
      String telemetrySource,
      Integer durationSeconds,
      Double distanceFlownMeters,
      Double maxAltitudeMeters,
      Integer startBatteryPercent,
      Integer endBatteryPercent,
      String finalState,
      String note,
      String flightTraceJson) {
    ensureTelemetry()
        .recordReport(
            telemetrySource,
            durationSeconds,
            distanceFlownMeters,
            maxAltitudeMeters,
            startBatteryPercent,
            endBatteryPercent,
            finalState,
            note,
            flightTraceJson);
    this.updatedAt = Instant.now();
  }

  public void recordFlightVideoEvidenceId(UUID evidenceId) {
    ensureTelemetry().recordVideoEvidenceId(evidenceId);
    this.updatedAt = Instant.now();
  }

  private FieldSessionTelemetry ensureTelemetry() {
    if (this.telemetry == null) {
      this.telemetry = new FieldSessionTelemetry(this);
    }
    return this.telemetry;
  }

  public boolean hasPersistedFlightReport() {
    return telemetry != null && telemetry.hasPersistedReport();
  }

  public String getTelemetrySource() {
    return telemetry == null ? null : telemetry.getTelemetrySource();
  }

  public Integer getFlightDurationSeconds() {
    return telemetry == null ? null : telemetry.getFlightDurationSeconds();
  }

  public java.math.BigDecimal getDistanceFlownMeters() {
    return telemetry == null ? null : telemetry.getDistanceFlownMeters();
  }

  public java.math.BigDecimal getMaxAltitudeMeters() {
    return telemetry == null ? null : telemetry.getMaxAltitudeMeters();
  }

  public Integer getStartBatteryPercent() {
    return telemetry == null ? null : telemetry.getStartBatteryPercent();
  }

  public Integer getEndBatteryPercent() {
    return telemetry == null ? null : telemetry.getEndBatteryPercent();
  }

  public String getFlightFinalState() {
    return telemetry == null ? null : telemetry.getFlightFinalState();
  }

  public String getFlightReportNote() {
    return telemetry == null ? null : telemetry.getFlightReportNote();
  }

  public String getFlightTraceJson() {
    return telemetry == null ? null : telemetry.getFlightTraceJson();
  }

  public UUID getFlightVideoEvidenceId() {
    return telemetry == null ? null : telemetry.getFlightVideoEvidenceId();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

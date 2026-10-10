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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A field session under one inspection. MF3 evidence may be attributed to the session it came from.
 *
 * <p>The status vocabulary is a {@code String} rather than an enum so this class does not widen the
 * migration contract that {@code ck_field_sessions_status} already fixed; the values below are the
 * only ones the database accepts.
 *
 * <p>A session is opened against one readiness decision and keeps that decision id, because MF2-10
 * re-checks readiness at the moment of the start. Recording which decision was used is what makes a
 * later audit answer "which approval did this flight rely on" instead of "some approval, at some
 * point".
 */
@Entity
@Table(name = "field_sessions")
public class FieldSession {

  static final String PLANNED = "PLANNED";
  static final String IN_PROGRESS = "IN_PROGRESS";
  static final String POSTPONED = "POSTPONED";
  static final String ABORTED = "ABORTED";

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_id", nullable = false)
  private UUID inspectionId;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "inspector_user_id", nullable = false)
  private UUID inspectorUserId;

  @Column(name = "drone_id")
  private UUID droneId;

  @Column(name = "readiness_decision_id")
  private UUID readinessDecisionId;

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

  @Column(name = "checklist_template_id")
  private UUID checklistTemplateId;

  @Column(name = "checklist_version")
  private Integer checklistVersion;

  @Column(name = "readiness_version")
  private Integer readinessVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String limitations;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected FieldSession() {}

  public FieldSession(
      UUID inspectionId,
      UUID organizationId,
      UUID inspectorUserId,
      UUID droneId,
      UUID readinessDecisionId,
      String status,
      Instant startedAt,
      Integer checklistVersion,
      Integer readinessVersion) {
    this.inspectionId = inspectionId;
    this.organizationId = organizationId;
    this.inspectorUserId = inspectorUserId;
    this.droneId = droneId;
    this.readinessDecisionId = readinessDecisionId;
    this.status = status;
    this.startedAt = startedAt;
    this.checklistVersion = checklistVersion;
    this.readinessVersion = readinessVersion;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  /**
   * Records that the session is under way (MF2-10).
   *
   * <p>Starting stamps the instant rather than accepting one, so a caller cannot backdate a session
   * to sit inside an earlier planned window.
   */
  public void start(Instant startedAt) {
    if (!IN_PROGRESS.equals(status) && !PLANNED.equals(status)) {
      throw new IllegalStateException("Only a planned or in-progress session can be started");
    }
    this.startedAt = Objects.requireNonNull(startedAt, "Session start time is required");
    this.status = IN_PROGRESS;
    this.updatedAt = startedAt;
  }

  /**
   * Weather or site safety stopped the session before any flight time (MF2-09/11).
   *
   * <p>A postponement ends the attempt but leaves the inspection able to try again: the readiness
   * decision was not consumed, so the same inspection may be started once conditions allow.
   */
  public void postpone(String postponementReason) {
    requireOpenSession();
    this.postponementReason = requireReason(postponementReason);
    this.status = POSTPONED;
    this.endedAt = Instant.now();
    this.updatedAt = endedAt;
  }

  /** The session cannot continue and will not resume under this record (MF2-11). */
  public void abort(String abortReason) {
    requireOpenSession();
    this.abortReason = requireReason(abortReason);
    this.status = ABORTED;
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

  /**
   * A session opened against a readiness decision but not yet started on site.
   *
   * <p>The session is created {@code PLANNED} and becomes {@code IN_PROGRESS} only when the
   * Inspector actually starts, so a row never claims a start instant it did not have.
   *
   * <p>The checklist template and its version are recorded together, because a session that ran
   * against one checklist version cannot be read as evidence against another.
   */
  public static FieldSession opening(
      UUID inspectionId,
      UUID organizationId,
      UUID inspectorUserId,
      UUID droneId,
      UUID checklistTemplateId,
      UUID readinessDecisionId,
      Integer readinessVersion) {
    FieldSession session =
        new FieldSession(
            inspectionId,
            organizationId,
            inspectorUserId,
            droneId,
            readinessDecisionId,
            PLANNED,
            null,
            null,
            readinessVersion);
    session.checklistTemplateId = checklistTemplateId;
    return session;
  }

  /**
   * Whether this session still occupies the inspection.
   *
   * <p>Only a planned or in-progress session does. A postponed or aborted one is history, and the
   * inspection may be started again.
   */
  public boolean isOpen() {
    return PLANNED.equals(status) || IN_PROGRESS.equals(status);
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getInspectorUserId() {
    return inspectorUserId;
  }

  public UUID getDroneId() {
    return droneId;
  }

  public UUID getReadinessDecisionId() {
    return readinessDecisionId;
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

  public UUID getChecklistTemplateId() {
    return checklistTemplateId;
  }

  public Integer getChecklistVersion() {
    return checklistVersion;
  }

  public Integer getReadinessVersion() {
    return readinessVersion;
  }
}

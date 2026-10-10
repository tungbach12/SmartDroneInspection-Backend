package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.WorkLogStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Task-level execution record made by the engineer who performed the work.
 *
 * <p>Hours are recorded here, not inferred from a report. Recording work is not accepting it:
 * MF4-14 treats {@code WORK_COMPLETED} as a team declaration, and MF4-18 requires an independent
 * reviewer before closure.
 *
 * <p>The target schema has no free-text narrative column for this table: {@code work_summary} was
 * dropped by the V26 runtime cutover. Narrative for MF4-11 is therefore carried inside the {@code
 * test_readings} JSON document under a {@code narrative} key, which is the same shape the MF3
 * verified finding uses for its measurement summary. Restoring a dedicated column needs a forward
 * migration and is not done here.
 */
@Entity
@Table(name = "maintenance_work_logs")
public class MaintenanceWorkLog {

  @Id @GeneratedValue private UUID id;

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Column(name = "task_id", nullable = false)
  private UUID taskId;

  @Column(name = "engineer_user_id", nullable = false)
  private UUID engineerUserId;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(nullable = false, precision = 18, scale = 6)
  private BigDecimal hours;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "actual_cost_references", columnDefinition = "jsonb")
  private String actualCostReferences;

  @Column(name = "as_left_condition", length = 4000)
  private String asLeftCondition;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "test_readings", columnDefinition = "jsonb")
  private String testReadings;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private WorkLogStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected MaintenanceWorkLog() {}

  public MaintenanceWorkLog(
      UUID workOrderId, UUID taskId, UUID engineerUserId, Instant startedAt, BigDecimal hours) {
    this.workOrderId = require(workOrderId, "workOrderId");
    this.taskId = require(taskId, "taskId");
    this.engineerUserId = require(engineerUserId, "engineerUserId");
    this.startedAt = startedAt == null ? Instant.now() : startedAt;
    this.hours = requireHours(hours);
    this.status = WorkLogStatus.IN_PROGRESS;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  /** MF4-09/10: the engineer records how long the work took and what it produced. */
  public void recordProgress(
      Instant startedAt,
      Instant endedAt,
      BigDecimal hours,
      String actualCostReferences,
      String asLeftCondition,
      String testReadings) {
    if (status != WorkLogStatus.IN_PROGRESS) {
      throw new IllegalStateException(
          "Work log is " + status + "; only IN_PROGRESS logs are editable");
    }
    Instant from = startedAt == null ? this.startedAt : startedAt;
    if (endedAt != null && endedAt.isBefore(from)) {
      throw new IllegalArgumentException("ended_at must not be before started_at");
    }
    this.startedAt = from;
    this.endedAt = endedAt;
    this.hours = requireHours(hours);
    this.actualCostReferences = actualCostReferences;
    this.asLeftCondition = asLeftCondition;
    this.testReadings = testReadings;
    this.updatedAt = Instant.now();
  }

  /** MF4-11: the engineer submits their own timesheet. Submission is not verification. */
  public void submit() {
    if (status != WorkLogStatus.IN_PROGRESS) {
      throw new IllegalStateException(
          "Work log is " + status + "; only IN_PROGRESS logs can be submitted");
    }
    if (endedAt == null) {
      throw new IllegalStateException("A submitted work log requires an end time");
    }
    this.status = WorkLogStatus.SUBMITTED;
    this.updatedAt = Instant.now();
  }

  /** MF4-11: the lead verifies the submitted work log; verification is not acceptance. */
  public void verify() {
    requireStatus(WorkLogStatus.SUBMITTED);
    this.status = WorkLogStatus.VERIFIED;
    this.updatedAt = Instant.now();
  }

  /** MF4-11: a change request pauses the log until the change is decided. */
  public void pauseForChange() {
    requireStatus(WorkLogStatus.IN_PROGRESS);
    this.status = WorkLogStatus.PAUSED_FOR_CHANGE;
    this.updatedAt = Instant.now();
  }

  public void resumeAfterChange() {
    requireStatus(WorkLogStatus.PAUSED_FOR_CHANGE);
    this.status = WorkLogStatus.IN_PROGRESS;
    this.updatedAt = Instant.now();
  }

  private void requireStatus(WorkLogStatus expected) {
    if (status != expected) {
      throw new IllegalStateException("Work log is " + status + "; expected " + expected);
    }
  }

  private static BigDecimal requireHours(BigDecimal value) {
    if (value == null || value.signum() < 0) {
      throw new IllegalArgumentException("hours must be present and not negative");
    }
    return value;
  }

  private static UUID require(UUID value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkOrderId() {
    return workOrderId;
  }

  public UUID getTaskId() {
    return taskId;
  }

  public UUID getEngineerUserId() {
    return engineerUserId;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public String getAsLeftCondition() {
    return asLeftCondition;
  }

  public BigDecimal getHours() {
    return hours;
  }

  public String getActualCostReferences() {
    return actualCostReferences;
  }

  public String getTestReadings() {
    return testReadings;
  }

  public WorkLogStatus getStatus() {
    return status;
  }
}

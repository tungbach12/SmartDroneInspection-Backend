package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.TaskStatus;
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

/**
 * One planned unit of corrective work inside a work order.
 *
 * <p>Task numbers are unique per work order and assigned by the team lead. The status values beyond
 * {@code PLANNED} belong to the execution slice that follows MF4-08.
 */
@Entity
@Table(name = "maintenance_tasks")
public class MaintenanceTask {

  @Id @GeneratedValue private UUID id;

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Column(name = "task_number", nullable = false)
  private int taskNumber;

  @Column(nullable = false, length = 300)
  private String name;

  @Column(length = 4000)
  private String method;

  @Column(name = "assigned_engineer_user_id")
  private UUID assignedEngineerUserId;

  @Column(name = "planned_start_at")
  private Instant plannedStartAt;

  @Column(name = "planned_end_at")
  private Instant plannedEndAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "acceptance_criteria", columnDefinition = "jsonb")
  private String acceptanceCriteria;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private TaskStatus status;

  @Column(name = "completion_notes", length = 4000)
  private String completionNotes;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected MaintenanceTask() {}

  public MaintenanceTask(
      UUID workOrderId,
      int taskNumber,
      String name,
      String method,
      UUID assignedEngineerUserId,
      Instant plannedStartAt,
      Instant plannedEndAt,
      String acceptanceCriteria,
      int displayOrder) {
    this.workOrderId = require(workOrderId, "workOrderId");
    this.taskNumber = requireNumber(taskNumber, "taskNumber");
    this.name = requireText(name, "name");
    this.displayOrder = requireDisplayOrder(displayOrder);
    this.method = method;
    this.assignedEngineerUserId = assignedEngineerUserId;
    this.plannedStartAt = plannedStartAt;
    this.plannedEndAt = plannedEndAt;
    this.acceptanceCriteria = acceptanceCriteria;
    this.status = TaskStatus.PLANNED;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  /** MF4-05: a task may only be planned before its dates run backwards. */
  public void replan(
      String method,
      UUID assignedEngineerUserId,
      Instant plannedStartAt,
      Instant plannedEndAt,
      String acceptanceCriteria) {
    if (status != TaskStatus.PLANNED) {
      throw new IllegalStateException(
          "Task " + taskNumber + " is " + status + "; only PLANNED tasks are editable");
    }
    requireWindow(plannedStartAt, plannedEndAt);
    this.method = method;
    this.assignedEngineerUserId = assignedEngineerUserId;
    this.plannedStartAt = plannedStartAt;
    this.plannedEndAt = plannedEndAt;
    this.acceptanceCriteria = acceptanceCriteria;
    this.updatedAt = Instant.now();
  }

  private static void requireWindow(Instant start, Instant end) {
    if (start != null && end != null && !end.isAfter(start)) {
      throw new IllegalArgumentException("plannedEndAt must be after plannedStartAt");
    }
  }

  private static UUID require(UUID value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  private static int requireNumber(int value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }

  private static int requireDisplayOrder(int value) {
    if (value < 0) {
      throw new IllegalArgumentException("displayOrder must not be negative");
    }
    return value;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
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

  public int getTaskNumber() {
    return taskNumber;
  }

  public String getName() {
    return name;
  }

  public String getMethod() {
    return method;
  }

  public UUID getAssignedEngineerUserId() {
    return assignedEngineerUserId;
  }

  public Instant getPlannedStartAt() {
    return plannedStartAt;
  }

  public Instant getPlannedEndAt() {
    return plannedEndAt;
  }

  public String getAcceptanceCriteria() {
    return acceptanceCriteria;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

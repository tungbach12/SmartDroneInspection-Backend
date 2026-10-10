package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.MaintenanceTask;
import com.smartdroneinspection.maintenance.domain.enums.TaskStatus;
import java.time.Instant;
import java.util.UUID;

/** MF4-05: a planned unit of corrective work. */
public record TaskResponse(
    UUID id,
    int taskNumber,
    String name,
    String method,
    UUID assignedEngineerUserId,
    Instant plannedStartAt,
    Instant plannedEndAt,
    String acceptanceCriteria,
    TaskStatus status,
    int displayOrder) {

  public static TaskResponse from(MaintenanceTask task) {
    return new TaskResponse(
        task.getId(),
        task.getTaskNumber(),
        task.getName(),
        task.getMethod(),
        task.getAssignedEngineerUserId(),
        task.getPlannedStartAt(),
        task.getPlannedEndAt(),
        task.getAcceptanceCriteria(),
        task.getStatus(),
        task.getDisplayOrder());
  }
}

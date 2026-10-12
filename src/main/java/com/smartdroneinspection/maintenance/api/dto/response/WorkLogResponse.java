package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.MaintenanceWorkLog;
import com.smartdroneinspection.maintenance.domain.enums.WorkLogStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** MF4-10/11: an engineer's execution record for one task. */
public record WorkLogResponse(
    UUID id,
    UUID taskId,
    UUID engineerUserId,
    Instant startedAt,
    Instant endedAt,
    BigDecimal hours,
    String actualCostReferences,
    String asLeftCondition,
    String testReadings,
    WorkLogStatus status) {

  public static WorkLogResponse from(MaintenanceWorkLog log) {
    return new WorkLogResponse(
        log.getId(),
        log.getTaskId(),
        log.getEngineerUserId(),
        log.getStartedAt(),
        log.getEndedAt(),
        log.getHours(),
        log.getActualCostReferences(),
        log.getAsLeftCondition(),
        log.getTestReadings(),
        log.getStatus());
  }
}

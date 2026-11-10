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
    String asLeftCondition,
    String testReadings,
    WorkLogStatus status,
    String repairProofUrl) {

  private static final String PROOF_MARKER = "[REPAIR_PROOF:";

  public static WorkLogResponse from(MaintenanceWorkLog log) {
    String rawReadings = log.getTestReadings();
    String cleanReadings = rawReadings;
    String proofUrl = null;
    if (rawReadings != null && rawReadings.contains(PROOF_MARKER)) {
      int start = rawReadings.indexOf(PROOF_MARKER);
      int end = rawReadings.indexOf(']', start);
      if (end > start) {
        proofUrl = rawReadings.substring(start + PROOF_MARKER.length(), end).trim();
        cleanReadings = (rawReadings.substring(0, start) + rawReadings.substring(end + 1)).trim();
        if (cleanReadings.isEmpty()) {
          cleanReadings = null;
        }
      }
    }
    return new WorkLogResponse(
        log.getId(),
        log.getTaskId(),
        log.getEngineerUserId(),
        log.getStartedAt(),
        log.getEndedAt(),
        log.getHours(),
        log.getAsLeftCondition(),
        cleanReadings,
        log.getStatus(),
        proofUrl);
  }
}

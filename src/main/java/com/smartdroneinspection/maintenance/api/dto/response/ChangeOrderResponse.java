package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.MaintenanceChangeOrder;
import com.smartdroneinspection.maintenance.domain.enums.ChangeOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** MF4-12/13: a change proposal and its recorded decision. */
public record ChangeOrderResponse(
    UUID id,
    int changeNumber,
    String reason,
    String affectedTasks,
    BigDecimal proposedDelta,
    ChangeOrderStatus status,
    UUID requestedByUserId,
    UUID decidedByUserId,
    Instant decidedAt,
    String decisionReason) {

  public static ChangeOrderResponse from(MaintenanceChangeOrder change) {
    return new ChangeOrderResponse(
        change.getId(),
        change.getChangeNumber(),
        change.getReason(),
        change.getAffectedTasks(),
        change.getProposedDelta(),
        change.getStatus(),
        change.getRequestedByUserId(),
        change.getDecidedByUserId(),
        change.getDecidedAt(),
        change.getDecisionReason());
  }
}

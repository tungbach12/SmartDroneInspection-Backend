package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.MaintenanceWorkOrder;
import com.smartdroneinspection.maintenance.domain.enums.MaintenancePriority;
import com.smartdroneinspection.maintenance.domain.enums.WorkOrderStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * MF4-01/02: work order header. Financial totals are not part of the header; they come from the
 * reconciliation summary once one exists.
 */
public record WorkOrderResponse(
    UUID id,
    UUID assetId,
    UUID sourceReportVersionId,
    UUID sourceFindingId,
    WorkOrderStatus status,
    MaintenancePriority priority,
    Instant dueAt,
    String correctiveScope,
    String acceptanceCriteria,
    UUID ownerUserId,
    UUID budgetApproverUserId,
    UUID teamLeadUserId,
    UUID reportAuthorUserId,
    UUID acceptingReviewerUserId,
    Instant createdAt,
    Instant updatedAt) {

  public static WorkOrderResponse from(MaintenanceWorkOrder order) {
    return new WorkOrderResponse(
        order.getId(),
        order.getAssetId(),
        order.getSourceReportVersionId(),
        order.getSourceFindingId(),
        order.getStatus(),
        order.getPriority(),
        order.getDueAt(),
        order.getCorrectiveScope(),
        order.getAcceptanceCriteria(),
        order.getOwnerUserId(),
        order.getBudgetApproverUserId(),
        order.getTeamLeadUserId(),
        order.getReportAuthorUserId(),
        order.getAcceptingReviewerUserId(),
        order.getCreatedAt(),
        order.getUpdatedAt());
  }
}

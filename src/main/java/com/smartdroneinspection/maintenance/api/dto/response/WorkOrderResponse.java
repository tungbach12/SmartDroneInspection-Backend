package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.MaintenanceWorkOrder;
import com.smartdroneinspection.maintenance.domain.enums.MaintenancePriority;
import com.smartdroneinspection.maintenance.domain.enums.WorkOrderStatus;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

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
    String budgetApproverName,
    String teamLeadName,
    String reportAuthorName,
    String acceptingReviewerName,
    Instant createdAt,
    Instant updatedAt,
    java.util.UUID reinspectionId,
    java.util.UUID reinspectionReportVersionId) {

  public static WorkOrderResponse from(MaintenanceWorkOrder order) {
    return from(order, id -> null);
  }

  public static WorkOrderResponse from(
      MaintenanceWorkOrder order, Function<UUID, String> nameResolver) {
    Function<UUID, String> resolve =
        id -> (id == null || nameResolver == null) ? null : nameResolver.apply(id);
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
        resolve.apply(order.getBudgetApproverUserId()),
        resolve.apply(order.getTeamLeadUserId()),
        resolve.apply(order.getReportAuthorUserId()),
        resolve.apply(order.getAcceptingReviewerUserId()),
        order.getCreatedAt(),
        order.getUpdatedAt(),
        order.getReinspectionId(),
        order.getReinspectionReportVersionId());
  }
}

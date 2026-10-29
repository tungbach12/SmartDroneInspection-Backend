package com.smartdroneinspection.maintenance.service;

import com.smartdroneinspection.inspections.spi.InspectionReinspectionReportPublishedEvent;
import com.smartdroneinspection.inspections.spi.ReinspectionInspectionAccess;
import com.smartdroneinspection.inspections.spi.ReinspectionInspectionAssignment;
import com.smartdroneinspection.maintenance.api.dto.request.AssignReinspectionRequest;
import com.smartdroneinspection.maintenance.api.dto.request.CreateReinspectionRequest;
import com.smartdroneinspection.maintenance.api.dto.request.DecideReinspectionRequest;
import com.smartdroneinspection.maintenance.api.dto.response.ReinspectionRequestResponse;
import com.smartdroneinspection.maintenance.domain.MaintenanceReportVersion;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkOrder;
import com.smartdroneinspection.maintenance.domain.ReinspectionRequest;
import com.smartdroneinspection.maintenance.domain.enums.ReinspectionRequestStatus;
import com.smartdroneinspection.maintenance.domain.enums.ReportVersionStatus;
import com.smartdroneinspection.maintenance.domain.enums.WorkOrderStatus;
import com.smartdroneinspection.maintenance.repository.MaintenanceEstimateVersionRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceReportVersionRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceWorkOrderRepository;
import com.smartdroneinspection.maintenance.repository.ReinspectionRequestRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceReinspectionService {

  private static final List<ReinspectionRequestStatus> ACTIVE_REQUESTS =
      List.of(
          ReinspectionRequestStatus.REQUESTED,
          ReinspectionRequestStatus.ASSIGNED,
          ReinspectionRequestStatus.INSPECTION_PUBLISHED);

  private final MaintenanceWorkOrderRepository workOrders;
  private final MaintenanceReportVersionRepository reports;
  private final MaintenanceEstimateVersionRepository estimates;
  private final ReinspectionRequestRepository requests;
  private final WorkOrderService workOrderService;
  private final UserAccess userAccess;
  private final ReinspectionInspectionAccess inspectionAccess;

  public MaintenanceReinspectionService(
      MaintenanceWorkOrderRepository workOrders,
      MaintenanceReportVersionRepository reports,
      MaintenanceEstimateVersionRepository estimates,
      ReinspectionRequestRepository requests,
      WorkOrderService workOrderService,
      UserAccess userAccess,
      ReinspectionInspectionAccess inspectionAccess) {
    this.workOrders = workOrders;
    this.reports = reports;
    this.estimates = estimates;
    this.requests = requests;
    this.workOrderService = workOrderService;
    this.userAccess = userAccess;
    this.inspectionAccess = inspectionAccess;
  }

  @Transactional
  public ReinspectionRequestResponse request(
      UUID actorId, UUID workOrderId, CreateReinspectionRequest input) {
    UserAccess.ActiveUser customer = requireCustomer(actorId);
    MaintenanceWorkOrder order =
        workOrders
            .findByIdAndOrganizationId(workOrderId, customer.organizationId())
            .orElseThrow(() -> notFound("Work order not found"));
    if (!order.getOwnerUserId().equals(actorId)) {
      throw forbidden("Only the work-order customer may request reinspection");
    }
    if (order.getStatus() != WorkOrderStatus.AWAITING_CUSTOMER_ACCEPTANCE
        && order.getStatus() != WorkOrderStatus.REPAIR_SUPPLEMENT_REQUIRED) {
      throw conflict("A submitted Maintenance Report or additional repair decision is required");
    }
    MaintenanceReportVersion report =
        reports
            .findByWorkOrderIdAndVersionNo(workOrderId, input.maintenanceReportVersionNo())
            .orElseThrow(() -> notFound("Maintenance Report version not found"));
    if (report.getStatus() != ReportVersionStatus.SUBMITTED
        && report.getStatus() != ReportVersionStatus.APPROVED) {
      throw conflict("Only a submitted Maintenance Report version may be forwarded");
    }
    if (requests.existsByWorkOrderIdAndStatusIn(workOrderId, ACTIVE_REQUESTS)) {
      throw conflict("An active reinspection request already exists for this work order");
    }
    if (input.findingIds() == null
        || input.findingIds().isEmpty()
        || input.findingIds().stream().distinct().count() != input.findingIds().size()
        || input.findingIds().stream()
            .anyMatch(findingId -> !findingId.equals(order.getSourceFindingId()))) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "FINDINGS_INVALID",
          "Select the confirmed source finding linked to this work order exactly once");
    }
    ReinspectionRequest request =
        new ReinspectionRequest(
            order.getOrganizationId(),
            workOrderId,
            report.getId(),
            actorId,
            input.findingIds(),
            input.note());
    order.requestReinspection();
    requests.saveAndFlush(request);
    workOrders.saveAndFlush(order);
    return response(request);
  }

  @Transactional(readOnly = true)
  public List<ReinspectionRequestResponse> managerQueue(UUID actorId) {
    requireManager(actorId);
    return requests
        .findByStatusOrderByRequestedAtAsc(
            ReinspectionRequestStatus.REQUESTED,
            org.springframework.data.domain.PageRequest.of(0, 100))
        .stream()
        .map(r -> response(r, false))
        .toList();
  }

  @Transactional
  public ReinspectionRequestResponse assign(
      UUID actorId, UUID requestId, AssignReinspectionRequest input) {
    UserAccess.ActiveUser manager = requireManager(actorId);
    ReinspectionRequest request =
        requests.findById(requestId).orElseThrow(() -> notFound("Reinspection request not found"));
    if (request.getStatus() != ReinspectionRequestStatus.REQUESTED) {
      throw conflict("Reinspection request is not awaiting assignment");
    }
    MaintenanceWorkOrder order =
        workOrders
            .findByIdAndOrganizationId(request.getWorkOrderId(), request.getOrganizationId())
            .orElseThrow(() -> notFound("Work order not found"));
    UUID inspectionId;
    try {
      inspectionId =
          inspectionAccess.createAssignedInspection(
              new ReinspectionInspectionAssignment(
                  order.getOrganizationId(),
                  order.getAssetId(),
                  input.inspectorUserId(),
                  input.droneId(),
                  actorId,
                  input.plannedStartAt(),
                  input.plannedEndAt()));
      request.assign(actorId, input.inspectorUserId(), input.droneId(), inspectionId);
      order.markReinspectionAssigned();
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw conflict(ex.getMessage());
    }
    requests.saveAndFlush(request);
    workOrders.saveAndFlush(order);
    return response(request);
  }

  @Transactional(readOnly = true)
  public List<ReinspectionRequestResponse> customerRequests(UUID actorId, UUID workOrderId) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    if (!order.getOwnerUserId().equals(actorId))
      throw forbidden("Only the customer may view reinspection requests");
    return requests.findAll().stream()
        .filter(
            r ->
                r.getWorkOrderId().equals(workOrderId)
                    && r.getOrganizationId().equals(order.getOrganizationId()))
        .map(this::response)
        .toList();
  }

  @Transactional
  public ReinspectionRequestResponse decide(
      UUID actorId, UUID workOrderId, UUID requestId, DecideReinspectionRequest input) {
    UserAccess.ActiveUser customer = requireCustomer(actorId);
    ReinspectionRequest request =
        requests
            .findByIdAndOrganizationId(requestId, customer.organizationId())
            .orElseThrow(() -> notFound("Reinspection request not found"));
    if (!request.getWorkOrderId().equals(workOrderId)) {
      throw notFound("Reinspection request not found");
    }
    MaintenanceWorkOrder order =
        workOrders
            .findByIdAndOrganizationId(workOrderId, customer.organizationId())
            .orElseThrow(() -> notFound("Work order not found"));
    if (!order.getOwnerUserId().equals(actorId))
      throw forbidden("Only the work-order customer may decide");
    if (order.getStatus() != WorkOrderStatus.AWAITING_CUSTOMER_ACCEPTANCE) {
      throw conflict(
          "A published, assigned reinspection report is required before customer decision");
    }
    try {
      request.decide(actorId, input.decision(), input.note());
      if ("ACCEPTED".equals(input.decision())) {
        order.recordCustomerDecision(
            com.smartdroneinspection.maintenance.domain.enums.CustomerMaintenanceDecision.ACCEPTED,
            input.note());
      } else {
        order.recordCustomerDecision(
            com.smartdroneinspection.maintenance.domain.enums.CustomerMaintenanceDecision
                .REPAIR_REQUIRED,
            input.note());
      }
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw conflict(ex.getMessage());
    }
    requests.saveAndFlush(request);
    workOrders.saveAndFlush(order);
    return response(request);
  }

  @EventListener
  @Transactional
  public void onInspectionReportPublished(InspectionReinspectionReportPublishedEvent event) {
    requests
        .findByInspectionId(event.inspectionId())
        .ifPresent(
            request -> {
              if (!request.getOrganizationId().equals(event.organizationId())
                  || request.getStatus() != ReinspectionRequestStatus.ASSIGNED) return;
              MaintenanceWorkOrder order =
                  workOrders
                      .findByIdAndOrganizationId(
                          request.getWorkOrderId(), request.getOrganizationId())
                      .orElse(null);
              if (order == null || order.getStatus() != WorkOrderStatus.REINSPECTION_IN_PROGRESS)
                return;
              request.markInspectionPublished(event.inspectionReportId());
              order.markAwaitingReinspectionResult();
              requests.saveAndFlush(request);
              workOrders.saveAndFlush(order);
            });
  }

  private UserAccess.ActiveUser requireCustomer(UUID actorId) {
    UserAccess.ActiveUser user =
        userAccess.findActiveUser(actorId).orElseThrow(() -> forbidden("User is not active"));
    if (!user.hasRole("CUSTOMER") || user.organizationId() == null)
      throw forbidden("Only CUSTOMER can perform this action");
    return user;
  }

  private UserAccess.ActiveUser requireManager(UUID actorId) {
    UserAccess.ActiveUser user =
        userAccess.findActiveUser(actorId).orElseThrow(() -> forbidden("User is not active"));
    if (!user.hasRole("MANAGER") || user.organizationId() != null)
      throw forbidden("Only a platform MANAGER can perform this action");
    return user;
  }

  private ReinspectionRequestResponse response(ReinspectionRequest request) {
    return response(request, true);
  }

  private ReinspectionRequestResponse response(
      ReinspectionRequest request, boolean includeCustomerDetails) {
    MaintenanceReportVersion report =
        reports.findById(request.getMaintenanceReportVersionId()).orElse(null);
    return ReinspectionRequestResponse.from(
        request,
        report == null ? 0 : report.getVersionNo(),
        includeCustomerDetails ? request.getFindingIds() : request.getFindingIds());
  }

  private static BusinessException notFound(String message) {
    return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }

  private static BusinessException forbidden(String message) {
    return new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
  }

  private static BusinessException conflict(String message) {
    return new BusinessException(HttpStatus.CONFLICT, "WORKFLOW_CONFLICT", message);
  }
}

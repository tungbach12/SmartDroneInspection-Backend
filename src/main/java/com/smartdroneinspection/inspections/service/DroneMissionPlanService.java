package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspectionrequests.domain.InspectionRequest;
import com.smartdroneinspection.inspectionrequests.domain.InspectionServiceOrder;
import com.smartdroneinspection.inspectionrequests.repository.InspectionRequestRepository;
import com.smartdroneinspection.inspectionrequests.repository.InspectionServiceOrderRepository;
import com.smartdroneinspection.inspections.api.dto.request.AddShotItemRequest;
import com.smartdroneinspection.inspections.api.dto.request.AirspaceCheckRequest;
import com.smartdroneinspection.inspections.api.dto.request.CreateMissionPlanRequest;
import com.smartdroneinspection.inspections.api.dto.request.UpdateMissionPlanRequest;
import com.smartdroneinspection.inspections.api.dto.request.VerifyPermitRequest;
import com.smartdroneinspection.inspections.api.dto.response.MissionPlanResponse;
import com.smartdroneinspection.inspections.api.dto.response.MissionShotItemResponse;
import com.smartdroneinspection.inspections.domain.DroneMissionPlan;
import com.smartdroneinspection.inspections.domain.MissionShotItem;
import com.smartdroneinspection.inspections.repository.DroneMissionPlanRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing engineering drone mission plans, shot items, and airspace clearance (GAP-F-08,
 * BR-07, BR-08, BR-16).
 *
 * <p>Enforces cross-provider mission data isolation (BR-03, N-6), SOW completeness requirements,
 * and clearance gates.
 */
@Service
public class DroneMissionPlanService {

  private final DroneMissionPlanRepository missionPlans;
  private final InspectionServiceOrderRepository serviceOrders;
  private final InspectionRequestRepository inspectionRequests;
  private final UserAccess users;

  public DroneMissionPlanService(
      DroneMissionPlanRepository missionPlans,
      InspectionServiceOrderRepository serviceOrders,
      InspectionRequestRepository inspectionRequests,
      UserAccess users) {
    this.missionPlans = missionPlans;
    this.serviceOrders = serviceOrders;
    this.inspectionRequests = inspectionRequests;
    this.users = users;
  }

  @Transactional
  public MissionPlanResponse createDraft(UUID callerUserId, CreateMissionPlanRequest request) {
    UUID providerId = requireProviderScope(callerUserId);
    InspectionServiceOrder order =
        serviceOrders.findById(request.serviceOrderId()).orElseThrow(this::serviceOrderNotFound);

    if (order.getProviderId() == null || !order.getProviderId().equals(providerId)) {
      throw crossProviderAccessDenied();
    }

    int nextVersion =
        missionPlans
                .findByServiceOrderIdAndProviderIdOrderByVersionNumberDesc(
                    order.getId(), providerId)
                .stream()
                .mapToInt(DroneMissionPlan::getVersionNumber)
                .max()
                .orElse(0)
            + 1;

    DroneMissionPlan plan =
        new DroneMissionPlan(order.getId(), providerId, nextVersion, null, callerUserId);
    return toResponse(missionPlans.saveAndFlush(plan));
  }

  @Transactional(readOnly = true)
  public MissionPlanResponse getMissionPlan(UUID callerUserId, UUID missionPlanId) {
    UserAccess.ActiveUser caller = requireActiveUser(callerUserId);
    DroneMissionPlan plan =
        missionPlans.findById(missionPlanId).orElseThrow(this::missionPlanNotFound);

    assertAccessAllowed(caller, plan);
    return toResponse(plan);
  }

  @Transactional(readOnly = true)
  public List<MissionPlanResponse> listByOrder(UUID callerUserId, UUID serviceOrderId) {
    UserAccess.ActiveUser caller = requireActiveUser(callerUserId);
    InspectionServiceOrder order =
        serviceOrders.findById(serviceOrderId).orElseThrow(this::serviceOrderNotFound);

    if (caller.hasProviderScope()) {
      if (order.getProviderId() == null || !order.getProviderId().equals(caller.providerId())) {
        throw crossProviderAccessDenied();
      }
      return missionPlans
          .findByServiceOrderIdAndProviderIdOrderByVersionNumberDesc(
              serviceOrderId, caller.providerId())
          .stream()
          .map(this::toResponse)
          .toList();
    }

    if (caller.organizationId() != null) {
      if (!isOwningClient(caller, serviceOrderId)) {
        throw crossProviderAccessDenied();
      }
      return missionPlans.findByServiceOrderIdOrderByVersionNumberDesc(serviceOrderId).stream()
          .map(this::toResponse)
          .toList();
    }

    if (caller.hasRole(Roles.PLATFORM_ADMIN) || caller.hasRole(Roles.PLATFORM_OPERATOR)) {
      return missionPlans.findByServiceOrderIdOrderByVersionNumberDesc(serviceOrderId).stream()
          .map(this::toResponse)
          .toList();
    }

    throw crossProviderAccessDenied();
  }

  @Transactional
  public MissionPlanResponse updateEquipmentAndSow(
      UUID callerUserId, UUID missionPlanId, UpdateMissionPlanRequest request) {
    UUID providerId = requireProviderScope(callerUserId);
    DroneMissionPlan plan =
        missionPlans
            .findByIdAndProviderId(missionPlanId, providerId)
            .orElseThrow(this::crossProviderAccessDenied);

    plan.setEquipmentAndSow(
        request.cameraModel(),
        request.droneRegistrationId(),
        request.pilotUserId(),
        request.targetGsdMmPerPixel(),
        request.plannedAglM(),
        request.forwardOverlapPercent(),
        request.sideOverlapPercent(),
        request.sensorWidthMm(),
        request.focalLengthMm(),
        request.imageWidthPx());

    return toResponse(missionPlans.saveAndFlush(plan));
  }

  @Transactional
  public MissionPlanResponse addShotItem(
      UUID callerUserId, UUID missionPlanId, AddShotItemRequest request) {
    UUID providerId = requireProviderScope(callerUserId);
    DroneMissionPlan plan =
        missionPlans
            .findByIdAndProviderId(missionPlanId, providerId)
            .orElseThrow(this::crossProviderAccessDenied);

    MissionShotItem item =
        new MissionShotItem(
            request.sequenceNumber(),
            request.componentReference(),
            request.waypointLatitude(),
            request.waypointLongitude(),
            request.waypointAltitudeM(),
            request.cameraHeadingDegrees(),
            request.gimbalPitchDegrees(),
            request.targetGsdMmPerPixel(),
            request.captureInstructions());

    plan.addShotItem(item);
    return toResponse(missionPlans.saveAndFlush(plan));
  }

  @Transactional
  public MissionPlanResponse recordAirspacePreCheck(
      UUID callerUserId, UUID missionPlanId, AirspaceCheckRequest request) {
    UUID providerId = requireProviderScope(callerUserId);
    DroneMissionPlan plan =
        missionPlans
            .findByIdAndProviderId(missionPlanId, providerId)
            .orElseThrow(this::crossProviderAccessDenied);

    plan.recordAirspacePreCheck(request.status());
    return toResponse(missionPlans.saveAndFlush(plan));
  }

  @Transactional
  public MissionPlanResponse verifyFlightPermit(
      UUID callerUserId, UUID missionPlanId, VerifyPermitRequest request) {
    UUID providerId = requireProviderScope(callerUserId);
    DroneMissionPlan plan =
        missionPlans
            .findByIdAndProviderId(missionPlanId, providerId)
            .orElseThrow(this::crossProviderAccessDenied);

    plan.verifyFlightPermit(request.flightPermitReference());
    return toResponse(missionPlans.saveAndFlush(plan));
  }

  @Transactional
  public MissionPlanResponse submit(UUID callerUserId, UUID missionPlanId) {
    UUID providerId = requireProviderScope(callerUserId);
    DroneMissionPlan plan =
        missionPlans
            .findByIdAndProviderId(missionPlanId, providerId)
            .orElseThrow(this::crossProviderAccessDenied);

    plan.submit();
    return toResponse(missionPlans.saveAndFlush(plan));
  }

  @Transactional
  public MissionPlanResponse approve(UUID callerUserId, UUID missionPlanId) {
    UUID providerId = requireProviderManager(callerUserId);
    DroneMissionPlan plan =
        missionPlans
            .findByIdAndProviderId(missionPlanId, providerId)
            .orElseThrow(this::crossProviderAccessDenied);

    plan.approve(callerUserId);

    // MF2-06: approval moves the parent order to READY_FOR_FLIGHT in the same transaction.
    InspectionServiceOrder order =
        serviceOrders.findById(plan.getServiceOrderId()).orElseThrow(this::serviceOrderNotFound);
    order.markReadyForFlight();

    try {
      serviceOrders.saveAndFlush(order);
      return toResponse(missionPlans.saveAndFlush(plan));
    } catch (DataIntegrityViolationException ex) {
      throw duplicateApprovedPlan();
    }
  }

  private void assertAccessAllowed(UserAccess.ActiveUser caller, DroneMissionPlan plan) {
    if (caller.hasRole(Roles.PLATFORM_ADMIN) || caller.hasRole(Roles.PLATFORM_OPERATOR)) {
      return;
    }
    if (caller.hasProviderScope()) {
      if (!plan.getProviderId().equals(caller.providerId())) {
        throw crossProviderAccessDenied();
      }
      return;
    }
    if (caller.organizationId() != null && isOwningClient(caller, plan.getServiceOrderId())) {
      return;
    }
    throw crossProviderAccessDenied();
  }

  /**
   * Whether this caller is the client organization the order was contracted by. Fail-closed at
   * every step.
   */
  private boolean isOwningClient(UserAccess.ActiveUser caller, UUID serviceOrderId) {
    if (caller.organizationId() == null) {
      return false;
    }
    InspectionServiceOrder order = serviceOrders.findById(serviceOrderId).orElse(null);
    if (order == null) {
      return false;
    }
    InspectionRequest request =
        inspectionRequests.findById(order.getInspectionRequestId()).orElse(null);
    return request != null
        && request.getOrganizationId() != null
        && request.getOrganizationId().equals(caller.organizationId());
  }

  private UUID requireProviderScope(UUID userId) {
    UserAccess.ActiveUser caller = requireActiveUser(userId);
    if (!caller.hasProviderScope()
        || (!caller.hasRole(Roles.PROVIDER_MANAGER) && !caller.hasRole(Roles.INSPECTOR))) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Caller must be a PROVIDER_MANAGER or INSPECTOR belonging to a provider organization.");
    }
    return caller.providerId();
  }

  private UUID requireProviderManager(UUID userId) {
    UserAccess.ActiveUser caller = requireActiveUser(userId);
    if (!caller.hasProviderScope() || !caller.hasRole(Roles.PROVIDER_MANAGER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only PROVIDER_MANAGER may approve drone mission plans.");
    }
    return caller.providerId();
  }

  private UserAccess.ActiveUser requireActiveUser(UUID userId) {
    return users
        .findActiveUser(userId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Active user is required."));
  }

  private BusinessException crossProviderAccessDenied() {
    return new BusinessException(
        HttpStatus.FORBIDDEN,
        "CROSS_PROVIDER_ACCESS_DENIED",
        "Access to another provider's mission plan or service order is denied.");
  }

  private BusinessException serviceOrderNotFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND,
        "INSPECTION_ORDER_NOT_FOUND",
        "Inspection service order was not found.");
  }

  private BusinessException missionPlanNotFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "MISSION_PLAN_NOT_FOUND", "Drone mission plan was not found.");
  }

  private BusinessException duplicateApprovedPlan() {
    return new BusinessException(
        HttpStatus.CONFLICT,
        "DUPLICATE_APPROVED_PLAN",
        "An approved mission plan already exists for this service order.");
  }

  private MissionPlanResponse toResponse(DroneMissionPlan plan) {
    List<MissionShotItemResponse> items =
        plan.getShotItems().stream()
            .map(
                i ->
                    new MissionShotItemResponse(
                        i.getId(),
                        i.getSequenceNumber(),
                        i.getComponentReference(),
                        i.getWaypointLatitude(),
                        i.getWaypointLongitude(),
                        i.getWaypointAltitudeM(),
                        i.getCameraHeadingDegrees(),
                        i.getGimbalPitchDegrees(),
                        i.getTargetGsdMmPerPixel(),
                        i.getCaptureInstructions()))
            .toList();

    return new MissionPlanResponse(
        plan.getId(),
        plan.getServiceOrderId(),
        plan.getProviderId(),
        plan.getVersionNumber(),
        plan.getPreviousVersionId(),
        plan.getCreatedByUserId(),
        plan.getDroneRegistrationId(),
        plan.getPilotUserId(),
        plan.getFlightPermitReference(),
        plan.getCameraModel(),
        plan.getTargetGsdMmPerPixel(),
        plan.getPlannedAglM(),
        plan.getForwardOverlapPercent(),
        plan.getSideOverlapPercent(),
        plan.getAirspaceCheckStatus().name(),
        plan.getStatus().name(),
        plan.getApprovedByUserId(),
        plan.getApprovedAt(),
        plan.getCreatedAt(),
        plan.getUpdatedAt(),
        items);
  }
}

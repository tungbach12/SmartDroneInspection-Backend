package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.api.dto.request.CreateInspectionRequest;
import com.smartdroneinspection.inspections.api.dto.response.InspectionResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.events.InspectionAssignedEvent;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.subscriptions.SubscriptionEntitlement;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionCreationService {

  private final InspectionRepository inspections;
  private final AssetRepository assets;
  private final DroneRepository drones;
  private final UserAccess userAccess;
  private final Optional<EvidenceObjectStore> objectStore;
  private final SubscriptionEntitlement subscriptionEntitlement;
  private final ApplicationEventPublisher eventPublisher;

  public InspectionCreationService(
      InspectionRepository inspections,
      AssetRepository assets,
      DroneRepository drones,
      UserAccess userAccess,
      Optional<EvidenceObjectStore> objectStore,
      SubscriptionEntitlement subscriptionEntitlement,
      ApplicationEventPublisher eventPublisher) {
    this.inspections = inspections;
    this.assets = assets;
    this.drones = drones;
    this.userAccess = userAccess;
    this.objectStore = objectStore;
    this.subscriptionEntitlement = subscriptionEntitlement;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public InspectionResponse create(UUID actorId, CreateInspectionRequest request) {
    UserAccess.ActiveUser actor = requireOrgAdmin(actorId);
    UUID organizationId = actor.organizationId();

    Instant entitlementCheckAt =
        request.plannedStartAt() != null ? request.plannedStartAt() : Instant.now();
    subscriptionEntitlement.requireActive(organizationId, entitlementCheckAt);

    Asset asset =
        assets
            .findByIdAndOrganizationId(request.assetId(), organizationId)
            .orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Asset not found"));

    if (asset.getStatus() == AssetStatus.RETIRED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INVALID_STATE", "Cannot create an inspection for a retired asset.");
    }

    if (request.plannedStartAt() == null
        || request.plannedEndAt() == null
        || !request.plannedEndAt().isAfter(request.plannedStartAt())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_DATES", "Planned end must be after planned start.");
    }

    UserAccess.ActiveUser inspector =
        userAccess
            .findActiveUser(request.inspectorId())
            .filter(u -> organizationId.equals(u.organizationId()) && u.hasRole(Roles.INSPECTOR))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_INSPECTOR",
                        "Assigned inspector must be active in the organization."));

    Drone drone =
        drones
            .findWithLockByIdAndOrganizationId(request.droneId(), organizationId)
            .filter(d -> d.getServiceability() == DroneServiceability.ACTIVE)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_DRONE",
                        "Assigned drone must be active in the organization."));

    if (inspections.hasScheduleConflict(
        organizationId,
        inspector.id(),
        drone.getId(),
        request.plannedStartAt(),
        request.plannedEndAt())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "SCHEDULE_CONFLICT",
          "The assigned inspector or drone has a schedule conflict with another active inspection in this window.");
    }

    Inspection inspection =
        new Inspection(
            organizationId,
            asset.getId(),
            inspector.id(),
            drone.getId(),
            request.plannedStartAt(),
            request.plannedEndAt());

    Inspection saved = inspections.saveAndFlush(inspection);

    eventPublisher.publishEvent(
        new InspectionAssignedEvent(
            saved.getId(),
            saved.getOrganizationId(),
            saved.getInspectorId(),
            saved.getPlannedStartAt()));

    return toResponse(saved, asset);
  }

  @Transactional
  public Inspection dispatchInspectionInternal(
      UUID organizationId,
      UUID assetId,
      UUID inspectorId,
      UUID droneId,
      Instant plannedStartAt,
      Instant plannedEndAt,
      UUID recurringScheduleId) {
    if (plannedStartAt == null || plannedEndAt == null || !plannedEndAt.isAfter(plannedStartAt)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_DATES", "Planned end must be after planned start.");
    }
    subscriptionEntitlement.requireActive(organizationId, plannedStartAt);

    Asset asset =
        assets
            .findByIdAndOrganizationId(assetId, organizationId)
            .orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Asset not found"));

    if (asset.getStatus() == AssetStatus.RETIRED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INVALID_STATE", "Cannot create an inspection for a retired asset.");
    }

    UserAccess.ActiveUser inspector =
        userAccess
            .findActiveUser(inspectorId)
            .filter(u -> organizationId.equals(u.organizationId()) && u.hasRole(Roles.INSPECTOR))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_INSPECTOR",
                        "Assigned inspector must be active in the organization."));

    Drone drone =
        drones
            .findWithLockByIdAndOrganizationId(droneId, organizationId)
            .filter(d -> d.getServiceability() == DroneServiceability.ACTIVE)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_DRONE",
                        "Assigned drone must be active in the organization."));

    if (inspections.hasScheduleConflict(
        organizationId, inspector.id(), drone.getId(), plannedStartAt, plannedEndAt)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "SCHEDULE_CONFLICT",
          "The assigned inspector or drone has a schedule conflict with another active inspection in this window.");
    }

    Inspection inspection =
        new Inspection(
            organizationId,
            asset.getId(),
            inspector.id(),
            drone.getId(),
            plannedStartAt,
            plannedEndAt);

    if (recurringScheduleId != null) {
      inspection.setRecurringScheduleId(recurringScheduleId);
    }

    Inspection saved = inspections.saveAndFlush(inspection);

    eventPublisher.publishEvent(
        new InspectionAssignedEvent(
            saved.getId(),
            saved.getOrganizationId(),
            saved.getInspectorId(),
            saved.getPlannedStartAt()));

    return saved;
  }

  @Transactional(readOnly = true)
  public InspectionResponse get(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    Inspection inspection;
    if (actor.hasRole(Roles.ADMIN) || actor.hasRole(Roles.MANAGER)) {
      inspection =
          inspections
              .findById(inspectionId)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));
    } else {
      UUID organizationId = actor.organizationId();
      inspection =
          inspections
              .findByIdAndOrganizationId(inspectionId, organizationId)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));
    }

    Asset asset = assets.findById(inspection.getAssetId()).orElse(null);
    return toResponse(inspection, asset);
  }

  @Transactional
  public InspectionResponse uploadFlightPermit(
      UUID actorId,
      UUID inspectionId,
      String fileKey,
      String fileName,
      byte[] content,
      String contentType) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    UUID organizationId = actor.organizationId();

    Inspection inspection =
        (actor.isInspectorPartner()
                ? inspections
                    .findAssignedInspectorInspection(inspectionId, null, actorId, true)
                    .filter(Inspection::isPartnerAssignment)
                : inspections.findByIdAndOrganizationId(inspectionId, organizationId))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));

    if (!inspection.isInspectedBy(actorId) && !actor.hasRole(Roles.ORG_MANAGER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only the assigned inspector or organization manager can upload the flight permit.");
    }

    UserAccess.ActiveUser assignedInspector =
        userAccess
            .findActiveUser(inspection.getInspectorId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_INSPECTOR",
                        "Assigned inspector is no longer active."));

    if (!assignedInspector.hasValidPilotLicense(Instant.now())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PILOT_LICENSE_REQUIRED",
          "The assigned inspector must hold an active, non-expired pilot license.");
    }

    String effectiveKey = fileKey;
    if (content != null && content.length > 0 && objectStore.isPresent()) {
      effectiveKey =
          "inspections/"
              + inspectionId
              + "/flight-permit/"
              + UUID.randomUUID()
              + "-"
              + (fileName != null ? fileName : "permit.pdf");
      try {
        objectStore
            .get()
            .put(
                effectiveKey,
                contentType != null ? contentType : "application/pdf",
                content.length,
                new ByteArrayInputStream(content));
      } catch (IOException e) {
        throw new BusinessException(
            HttpStatus.BAD_GATEWAY, "STORAGE_FAILED", "Failed to store flight permit file.");
      }
    }

    if (effectiveKey == null || effectiveKey.isBlank()) {
      effectiveKey = "permits/" + UUID.randomUUID() + "-" + fileName;
    }

    inspection.recordFlightPermit(
        effectiveKey,
        fileName != null && !fileName.isBlank() ? fileName : "flight-permit.pdf",
        Instant.now());
    Inspection saved = inspections.saveAndFlush(inspection);
    Asset asset =
        assets.findByIdAndOrganizationId(inspection.getAssetId(), organizationId).orElse(null);
    return toResponse(saved, asset);
  }

  @Transactional
  public InspectionResponse reschedule(
      UUID actorId, UUID inspectionId, Instant plannedStartAt, Instant plannedEndAt) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (!actor.hasRole(Roles.MANAGER)
        && !actor.hasRole(Roles.ADMIN)
        && !actor.hasRole(Roles.CUSTOMER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only managers or admins can reschedule an inspection.");
    }
    Inspection inspection =
        inspections
            .findById(inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));

    if (actor.organizationId() != null
        && !actor.organizationId().equals(inspection.getOrganizationId())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied.");
    }

    if (inspection.getStatus()
        != com.smartdroneinspection.inspections.domain.enums.InspectionStatus.SCHEDULED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INVALID_STATE", "Only a scheduled inspection can be rescheduled.");
    }

    if (plannedStartAt == null || plannedEndAt == null || !plannedEndAt.isAfter(plannedStartAt)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_DATES", "Planned end must be after planned start.");
    }

    subscriptionEntitlement.requireActive(inspection.getOrganizationId(), plannedStartAt);

    inspection.updatePlannedWindow(plannedStartAt, plannedEndAt);
    Inspection saved = inspections.saveAndFlush(inspection);
    Asset asset = assets.findById(saved.getAssetId()).orElse(null);
    return toResponse(saved, asset);
  }

  @Transactional
  public InspectionResponse reassign(
      UUID actorId, UUID inspectionId, UUID newInspectorId, UUID newDroneId) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (!actor.hasRole(Roles.MANAGER)
        && !actor.hasRole(Roles.ADMIN)
        && !actor.hasRole(Roles.CUSTOMER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only managers or admins can reassign an inspection.");
    }
    Inspection inspection =
        inspections
            .findById(inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));

    if (actor.organizationId() != null
        && !actor.organizationId().equals(inspection.getOrganizationId())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied.");
    }

    if (inspection.getStatus()
        != com.smartdroneinspection.inspections.domain.enums.InspectionStatus.SCHEDULED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INVALID_STATE", "Only a scheduled inspection can be reassigned.");
    }

    UserAccess.ActiveUser newInspector =
        userAccess
            .findActiveUser(newInspectorId)
            .filter(u -> u.hasRole(Roles.INSPECTOR))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_INSPECTOR",
                        "Assigned inspector must be active."));

    if (newDroneId != null) {
      drones
          .findById(newDroneId)
          .filter(d -> d.getServiceability() == DroneServiceability.ACTIVE)
          .orElseThrow(
              () ->
                  new BusinessException(
                      HttpStatus.BAD_REQUEST, "INVALID_DRONE", "Assigned drone must be active."));
    }

    inspection.reassign(newInspector.id(), newDroneId);
    Inspection saved = inspections.saveAndFlush(inspection);

    eventPublisher.publishEvent(
        new InspectionAssignedEvent(
            saved.getId(),
            saved.getOrganizationId(),
            saved.getInspectorId(),
            saved.getPlannedStartAt()));

    Asset asset = assets.findById(saved.getAssetId()).orElse(null);
    return toResponse(saved, asset);
  }

  @Transactional
  public InspectionResponse cancel(UUID actorId, UUID inspectionId, String reason) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (!actor.hasRole(Roles.MANAGER)
        && !actor.hasRole(Roles.ADMIN)
        && !actor.hasRole(Roles.CUSTOMER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only managers or admins can cancel an inspection.");
    }
    Inspection inspection =
        inspections
            .findById(inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));

    if (actor.organizationId() != null
        && !actor.organizationId().equals(inspection.getOrganizationId())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied.");
    }

    if (inspection.getStatus()
            == com.smartdroneinspection.inspections.domain.enums.InspectionStatus.COMPLETED
        || inspection.getStatus()
            == com.smartdroneinspection.inspections.domain.enums.InspectionStatus.CANCELLED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INVALID_STATE",
          "Inspection cannot be cancelled from status " + inspection.getStatus());
    }

    inspection.cancel(reason);
    Inspection saved = inspections.saveAndFlush(inspection);
    Asset asset = assets.findById(saved.getAssetId()).orElse(null);
    return toResponse(saved, asset);
  }

  private InspectionResponse toResponse(Inspection inspection, Asset asset) {
    String assetName = asset == null ? null : asset.getName();
    String inspectorName =
        userAccess
            .findActiveUser(inspection.getInspectorId())
            .map(UserAccess.ActiveUser::fullName)
            .orElse(null);
    Drone drone =
        inspection.getDroneId() != null
            ? drones
                .findByIdAndOrganizationId(inspection.getDroneId(), inspection.getOrganizationId())
                .orElse(null)
            : null;

    return new InspectionResponse(
        inspection.getId(),
        inspection.getOrganizationId(),
        inspection.getAssetId(),
        assetName,
        asset == null ? null : asset.getLocationText(),
        asset == null ? null : asset.getLatitude(),
        asset == null ? null : asset.getLongitude(),
        asset == null ? java.util.List.of() : asset.getInspectionItems(),
        inspection.getInspectorId(),
        inspectorName,
        inspection.getDroneId(),
        drone == null ? null : drone.getSerialNumber(),
        drone == null ? null : drone.getModel(),
        drone == null ? null : drone.getServiceability().name(),
        inspection.getFlightPermitFileKey(),
        inspection.getFlightPermitFileName(),
        inspection.getFlightPermitUploadedAt(),
        inspection.getPlannedStartAt(),
        inspection.getPlannedEndAt(),
        inspection.getStatus().name(),
        inspection.getCreatedAt(),
        inspection.getUpdatedAt(),
        inspection.getInspectionType() != null ? inspection.getInspectionType().name() : "INITIAL",
        inspection.getWorkOrderId(),
        inspection.getSourceInspectionId(),
        inspection.getSourceReportVersionId());
  }

  private UserAccess.ActiveUser requireOrgAdmin(UUID actorId) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (!actor.hasRole(Roles.ORG_MANAGER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only ORG_MANAGER can perform this action.");
    }
    if (actor.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Actor has no organization scope.");
    }
    return actor;
  }

  private UserAccess.ActiveUser requireActive(UUID actorId) {
    return userAccess
        .findActiveUser(actorId)
        .orElseThrow(
            () -> new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active."));
  }
}

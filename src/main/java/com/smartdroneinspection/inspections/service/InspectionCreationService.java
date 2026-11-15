package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.api.dto.request.CreateInspectionRequest;
import com.smartdroneinspection.inspections.api.dto.response.InspectionResponse;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectorReliabilityEvent;
import com.smartdroneinspection.inspections.domain.InspectorReliabilityEvent.EventType;
import com.smartdroneinspection.inspections.domain.enums.AssignmentStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.events.InspectionAssignedEvent;
import com.smartdroneinspection.inspections.events.InspectionAssignmentRevokedEvent;
import com.smartdroneinspection.inspections.events.InspectionDeclinedEvent;
import com.smartdroneinspection.inspections.events.InspectionRescheduledEvent;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.InspectorReliabilityEventRepository;
import com.smartdroneinspection.shared.api.InspectionContractSyncPort;
import com.smartdroneinspection.shared.api.InspectionReassignCandidatePort;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectProvider<FieldSessionRepository> sessionsProvider;
  private final ObjectProvider<InspectorReliabilityEventRepository> reliabilityProvider;
  private final ObjectProvider<InspectionContractSyncPort> contractSyncProvider;
  private final ObjectProvider<InspectionReassignCandidatePort> reassignCandidatesProvider;

  public InspectionCreationService(
      InspectionRepository inspections,
      AssetRepository assets,
      DroneRepository drones,
      UserAccess userAccess,
      Optional<EvidenceObjectStore> objectStore,
      ApplicationEventPublisher eventPublisher) {
    this(
        inspections,
        assets,
        drones,
        userAccess,
        objectStore,
        eventPublisher,
        null,
        null,
        null,
        null);
  }

  @Autowired
  public InspectionCreationService(
      InspectionRepository inspections,
      AssetRepository assets,
      DroneRepository drones,
      UserAccess userAccess,
      Optional<EvidenceObjectStore> objectStore,
      ApplicationEventPublisher eventPublisher,
      ObjectProvider<FieldSessionRepository> sessionsProvider,
      ObjectProvider<InspectorReliabilityEventRepository> reliabilityProvider,
      ObjectProvider<InspectionContractSyncPort> contractSyncProvider,
      ObjectProvider<InspectionReassignCandidatePort> reassignCandidatesProvider) {
    this.inspections = inspections;
    this.assets = assets;
    this.drones = drones;
    this.userAccess = userAccess;
    this.objectStore = objectStore;
    this.eventPublisher = eventPublisher;
    this.sessionsProvider = sessionsProvider;
    this.reliabilityProvider = reliabilityProvider;
    this.contractSyncProvider = contractSyncProvider;
    this.reassignCandidatesProvider = reassignCandidatesProvider;
  }

  @Transactional
  public InspectionResponse create(UUID actorId, CreateInspectionRequest request) {
    UserAccess.ActiveUser actor = requireOrgAdmin(actorId);
    UUID ownerCustomerId = actor.ownerCustomerId();

    Asset asset =
        assets
            .findByIdAndOwnerCustomerId(request.assetId(), ownerCustomerId)
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
            .filter(u -> u.hasRole(Roles.INSPECTOR))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_INSPECTOR",
                        "Assigned inspector must be an active inspector."));

    Drone drone =
        drones
            .findWithLockByIdAndOwnerCustomerId(request.droneId(), ownerCustomerId)
            .filter(d -> d.getServiceability() == DroneServiceability.ACTIVE)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_DRONE",
                        "Assigned drone must be active and owned by the customer."));

    if (inspector.pilotLicenseExpiresAt() != null
        && !inspector.pilotLicenseExpiresAt().isAfter(request.plannedStartAt())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PILOT_LICENSE_EXPIRED",
          "Assigned inspector's pilot license expires before the planned inspection start time.");
    }

    if (drone.getRegistrationExpiresAt() != null
        && !drone.getRegistrationExpiresAt().isAfter(request.plannedStartAt())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "DRONE_REGISTRATION_EXPIRED",
          "Assigned drone registration expires before the planned inspection start time.");
    }

    if (!inspector.isLicenseClassQualifiedFor(drone.requiresClassBLicense())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PILOT_LICENSE_CLASS_INSUFFICIENT",
          "Assigned drone has MTOW >= 2 kg (Category 3+ under Decree 288/2025/ND-CP) and requires a Class B pilot license.");
    }

    if (inspections.hasScheduleConflictByOwner(
        ownerCustomerId,
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
            actor.ownerCustomerId(),
            asset.getId(),
            inspector.id(),
            drone.getId(),
            request.plannedStartAt(),
            request.plannedEndAt());
    inspection.markCustomerOwned(actorId);
    inspection.markPendingAcceptance();

    Inspection saved = inspections.saveAndFlush(inspection);

    eventPublisher.publishEvent(
        new InspectionAssignedEvent(
            saved.getId(),
            saved.getOwnerCustomerId(),
            saved.getInspectorId(),
            saved.getPlannedStartAt()));

    return toResponse(saved, asset);
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
    } else if (actor.isInspectorPartner() || actor.hasRole(Roles.INSPECTOR)) {
      inspection =
          inspections
              .findAssignedByInspector(inspectionId, actor.id())
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));
    } else {
      UUID ownerCustomerId = actor.ownerCustomerId();
      inspection =
          inspections
              .findByIdAndOwnerCustomerId(inspectionId, ownerCustomerId)
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
    UUID ownerCustomerId = actor.ownerCustomerId();

    Inspection inspection =
        (actor.isInspectorPartner()
                ? inspections.findAssignedByInspector(inspectionId, actorId)
                : inspections.findByIdAndOwnerCustomerId(inspectionId, ownerCustomerId))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));

    if (!inspection.isInspectedBy(actorId)
        && !actor.hasRole(Roles.ORG_MANAGER)
        && !actor.hasRole(Roles.CUSTOMER)
        && !actor.hasRole(Roles.MANAGER)
        && !actor.hasRole(Roles.ADMIN)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only the assigned inspector or manager can upload the flight permit.");
    }

    if (inspection.isReassignmentRequired()
        || inspection.getAssignmentStatus() == AssignmentStatus.DECLINED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "REASSIGNMENT_REQUIRED",
          "This inspection was declined and is awaiting manager reassignment or rescheduling.");
    }
    if (inspection.getAssignmentStatus() == AssignmentStatus.PENDING_ACCEPTANCE) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "ASSIGNMENT_NOT_ACCEPTED",
          "The assigned inspector must accept this mission before uploading a flight permit.");
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

    if (inspection.getDroneId() != null) {
      Drone assignedDrone = drones.findById(inspection.getDroneId()).orElse(null);
      if (assignedDrone != null) {
        if (assignedDrone.getServiceability() != DroneServiceability.ACTIVE) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST, "INVALID_DRONE", "Assigned drone is no longer active.");
        }
        if (assignedDrone.getRegistrationExpiresAt() != null
            && !assignedDrone.getRegistrationExpiresAt().isAfter(Instant.now())) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "DRONE_REGISTRATION_EXPIRED",
              "Assigned drone registration has expired.");
        }
        if (!assignedInspector.isLicenseClassQualifiedFor(assignedDrone.requiresClassBLicense())) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "PILOT_LICENSE_CLASS_INSUFFICIENT",
              "Assigned drone has MTOW >= 2 kg (Category 3+ under Decree 288/2025/ND-CP) and requires a Class B pilot license.");
        }
      }
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
        assets
            .findByIdAndOwnerCustomerId(inspection.getAssetId(), inspection.getOwnerCustomerId())
            .orElse(null);
    return toResponse(saved, asset);
  }

  public record FlightPermitContent(String fileName, String contentType, byte[] bytes) {}

  @Transactional(readOnly = true)
  public FlightPermitContent openFlightPermit(UUID actorId, UUID inspectionId) {
    InspectionResponse detail = get(actorId, inspectionId);
    if (detail.flightPermitFileKey() == null || detail.flightPermitFileKey().isBlank()) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND,
          "FLIGHT_PERMIT_NOT_FOUND",
          "No flight permit has been recorded for this inspection.");
    }
    String fileName =
        detail.flightPermitFileName() != null && !detail.flightPermitFileName().isBlank()
            ? detail.flightPermitFileName()
            : "flight-permit.pdf";
    String lower = fileName.toLowerCase(java.util.Locale.ROOT);
    String contentType =
        lower.endsWith(".png")
            ? "image/png"
            : (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
                ? "image/jpeg"
                : lower.endsWith(".webp") ? "image/webp" : "application/pdf";

    if (objectStore.isPresent()) {
      try (java.io.InputStream input = objectStore.get().open(detail.flightPermitFileKey())) {
        byte[] bytes = input.readAllBytes();
        if (bytes.length > 0) {
          return new FlightPermitContent(fileName, contentType, bytes);
        }
      } catch (Exception ignored) {
        // Fall through to generated permit summary PDF when seeded key has no stored object
      }
    }

    byte[] fallbackPdf = renderFallbackPermitPdf(detail, fileName);
    return new FlightPermitContent(
        fileName.endsWith(".pdf") ? fileName : fileName + ".pdf", "application/pdf", fallbackPdf);
  }

  private byte[] renderFallbackPermitPdf(InspectionResponse detail, String fileName) {
    String html =
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8" />
          <style>
            body { font-family: sans-serif; padding: 32px; color: #111827; }
            .box { border: 1px solid #d1d5db; border-radius: 8px; padding: 24px; }
            h1 { font-size: 18px; margin: 0 0 8px 0; color: #0f172a; }
            p { font-size: 12px; color: #4b5563; margin: 4px 0; }
            .row { margin-top: 12px; font-size: 13px; }
            .label { font-weight: bold; color: #374151; }
          </style>
        </head>
        <body>
          <div class="box">
            <h1>Flight Permit Record</h1>
            <p>Recorded mission flight permit document in SmartDroneInspection.</p>
            <div class="row"><span class="label">Permit File:</span> %s</div>
            <div class="row"><span class="label">Inspection ID:</span> %s</div>
            <div class="row"><span class="label">Target Asset:</span> %s</div>
            <div class="row"><span class="label">Assigned Drone:</span> %s</div>
          </div>
        </body>
        </html>
        """
            .formatted(
                escapeXml(fileName),
                escapeXml(String.valueOf(detail.id())),
                escapeXml(detail.assetName() != null ? detail.assetName() : "—"),
                escapeXml(
                    detail.droneSerialNumber() != null
                        ? (detail.droneModel() != null
                            ? detail.droneModel() + " (" + detail.droneSerialNumber() + ")"
                            : detail.droneSerialNumber())
                        : "—"));
    try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      com.openhtmltopdf.pdfboxout.PdfRendererBuilder builder =
          new com.openhtmltopdf.pdfboxout.PdfRendererBuilder();
      builder.useFastMode();
      builder.withHtmlContent(html, null);
      builder.toStream(out);
      builder.run();
      return out.toByteArray();
    } catch (Exception e) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND,
          "FLIGHT_PERMIT_NOT_FOUND",
          "Flight permit file could not be loaded.");
    }
  }

  private static String escapeXml(String input) {
    if (input == null) return "";
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  @Transactional
  public InspectionResponse reschedule(
      UUID actorId, UUID inspectionId, Instant plannedStartAt, Instant plannedEndAt) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (!actor.hasRole(Roles.MANAGER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only Managers can reschedule an inspection.");
    }
    Inspection inspection =
        inspections
            .findById(inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));

    if (actor.ownerCustomerId() != null
        && !actor.ownerCustomerId().equals(inspection.getOwnerCustomerId())) {
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

    UserAccess.ActiveUser assignedInspector =
        inspection.getInspectorId() != null
            ? userAccess.findActiveUser(inspection.getInspectorId()).orElse(null)
            : null;
    if (assignedInspector != null
        && assignedInspector.pilotLicenseExpiresAt() != null
        && !assignedInspector.pilotLicenseExpiresAt().isAfter(plannedStartAt)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PILOT_LICENSE_EXPIRED",
          "Assigned inspector's pilot license expires before the rescheduled inspection start time.");
    }

    if (inspection.getDroneId() != null) {
      Drone assignedDrone = drones.findById(inspection.getDroneId()).orElse(null);
      if (assignedDrone != null) {
        if (assignedDrone.getServiceability() != DroneServiceability.ACTIVE) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST, "INVALID_DRONE", "Assigned drone must be active.");
        }
        if (assignedDrone.getRegistrationExpiresAt() != null
            && !assignedDrone.getRegistrationExpiresAt().isAfter(plannedStartAt)) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "DRONE_REGISTRATION_EXPIRED",
              "Assigned drone registration expires before the rescheduled inspection start time.");
        }
        if (assignedInspector != null
            && !assignedInspector.isLicenseClassQualifiedFor(
                assignedDrone.requiresClassBLicense())) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "PILOT_LICENSE_CLASS_INSUFFICIENT",
              "Assigned drone has MTOW >= 2 kg (Category 3+ under Decree 288/2025/ND-CP) and requires a Class B pilot license.");
        }
      }
    }

    if (!inspection.isReassignmentRequired()
        && hasScheduleConflictExcluding(
            inspection,
            inspection.getInspectorId(),
            inspection.getDroneId(),
            plannedStartAt,
            plannedEndAt)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "SCHEDULE_CONFLICT",
          "The assigned inspector or drone has a schedule conflict in the rescheduled window.");
    }

    inspection.updatePlannedWindow(plannedStartAt, plannedEndAt);
    Inspection saved = inspections.saveAndFlush(inspection);
    InspectionContractSyncPort syncPort =
        contractSyncProvider == null ? null : contractSyncProvider.getIfAvailable();
    if (syncPort != null) {
      syncPort.syncRescheduleFromInspection(saved.getId(), plannedStartAt, plannedEndAt);
    }
    eventPublisher.publishEvent(
        new InspectionRescheduledEvent(
            saved.getId(),
            saved.getOwnerCustomerId(),
            saved.getInspectorId(),
            saved.getPlannedStartAt(),
            saved.getPlannedEndAt()));
    Asset asset = assets.findById(saved.getAssetId()).orElse(null);
    return toResponse(saved, asset);
  }

  @Transactional
  public InspectionResponse reassign(
      UUID actorId, UUID inspectionId, UUID newInspectorId, UUID newDroneId) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (!actor.hasRole(Roles.MANAGER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only Managers can reassign an inspection.");
    }
    Inspection inspection =
        inspections
            .findById(inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));

    if (actor.ownerCustomerId() != null
        && !actor.ownerCustomerId().equals(inspection.getOwnerCustomerId())) {
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
                        "Assigned inspector must be active and eligible for this inspection."));

    Instant checkAt =
        inspection.getPlannedStartAt() != null ? inspection.getPlannedStartAt() : Instant.now();
    if (newInspector.pilotLicenseExpiresAt() != null
        && !newInspector.pilotLicenseExpiresAt().isAfter(checkAt)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PILOT_LICENSE_REQUIRED",
          "The assigned inspector must hold an active, non-expired pilot license.");
    }

    UUID effectiveDroneId = newDroneId;
    if (effectiveDroneId == null
        && inspection.isPartnerAssignment()
        && !newInspector.id().equals(inspection.getInspectorId())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_DRONE",
          "Partner inspection reassignment requires selecting a drone owned by the new Inspector.");
    }
    if (effectiveDroneId == null) {
      effectiveDroneId = inspection.getDroneId();
    }

    if (newDroneId != null) {
      Drone newDrone =
          drones
              .findById(newDroneId)
              .filter(d -> d.getServiceability() == DroneServiceability.ACTIVE)
              .filter(
                  d ->
                      inspection.isPartnerAssignment()
                          ? newInspector.id().equals(d.getInspectorOwnerId())
                          : inspection.getOwnerCustomerId().equals(d.getOwnerCustomerId()))
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.BAD_REQUEST,
                          "INVALID_DRONE",
                          "Assigned drone must be active and owned by the eligible scope."));
      if (newDrone.getRegistrationExpiresAt() != null
          && !newDrone.getRegistrationExpiresAt().isAfter(checkAt)) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "DRONE_REGISTRATION_EXPIRED",
            "Assigned drone registration expires before the planned inspection start time.");
      }
      if (!newInspector.isLicenseClassQualifiedFor(newDrone.requiresClassBLicense())) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "PILOT_LICENSE_CLASS_INSUFFICIENT",
            "Assigned drone has MTOW >= 2 kg (Category 3+ under Decree 288/2025/ND-CP) and requires a Class B pilot license.");
      }
    } else if (effectiveDroneId != null) {
      Drone currentDrone = drones.findById(effectiveDroneId).orElse(null);
      if (currentDrone != null) {
        if (currentDrone.getServiceability() != DroneServiceability.ACTIVE) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST, "INVALID_DRONE", "Assigned drone must be active.");
        }
        if (inspection.isPartnerAssignment()
            && !newInspector.id().equals(currentDrone.getInspectorOwnerId())) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "INVALID_DRONE",
              "Partner inspection reassignment requires a drone owned by the new Inspector.");
        }
        if (currentDrone.getRegistrationExpiresAt() != null
            && !currentDrone.getRegistrationExpiresAt().isAfter(checkAt)) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "DRONE_REGISTRATION_EXPIRED",
              "Assigned drone registration expires before the planned inspection start time.");
        }
        if (!newInspector.isLicenseClassQualifiedFor(currentDrone.requiresClassBLicense())) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "PILOT_LICENSE_CLASS_INSUFFICIENT",
              "Assigned drone has MTOW >= 2 kg (Category 3+ under Decree 288/2025/ND-CP) and requires a Class B pilot license.");
        }
      }
    }

    if (inspection.getPlannedStartAt() != null
        && inspection.getPlannedEndAt() != null
        && hasScheduleConflictExcluding(
            inspection,
            newInspector.id(),
            effectiveDroneId,
            inspection.getPlannedStartAt(),
            inspection.getPlannedEndAt())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "SCHEDULE_CONFLICT",
          "The selected inspector or drone has a schedule conflict during this planned window.");
    }

    UUID previousInspectorId = inspection.getInspectorId();
    inspection.reassign(newInspector.id(), effectiveDroneId);
    Inspection saved = inspections.saveAndFlush(inspection);

    InspectionContractSyncPort syncPort =
        contractSyncProvider == null ? null : contractSyncProvider.getIfAvailable();
    if (syncPort != null) {
      syncPort.syncReassignFromInspection(saved.getId(), newInspector.id(), effectiveDroneId);
    }

    eventPublisher.publishEvent(
        new InspectionAssignedEvent(
            saved.getId(),
            saved.getOwnerCustomerId(),
            saved.getInspectorId(),
            saved.getPlannedStartAt()));
    if (previousInspectorId != null && !previousInspectorId.equals(newInspector.id())) {
      eventPublisher.publishEvent(
          new InspectionAssignmentRevokedEvent(
              saved.getId(), previousInspectorId, newInspector.id(), Instant.now()));
    }

    Asset asset = assets.findById(saved.getAssetId()).orElse(null);
    return toResponse(saved, asset);
  }

  @Transactional
  public InspectionResponse acceptAssignment(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (!actor.hasRole(Roles.INSPECTOR)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only the assigned inspector may accept a scheduled inspection.");
    }
    Inspection inspection =
        inspections
            .findAssignedByInspectorWithLock(inspectionId, actorId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));
    if (inspection.getStatus() != InspectionStatus.SCHEDULED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INVALID_STATE",
          "Only a scheduled inspection can be accepted before field work starts.");
    }
    if (inspection.isReassignmentRequired()
        || inspection.getAssignmentStatus() == AssignmentStatus.DECLINED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "REASSIGNMENT_REQUIRED",
          "This inspection was declined and is awaiting manager reassignment or rescheduling.");
    }
    try {
      inspection.acceptAssignment(actorId);
    } catch (IllegalStateException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage());
    }
    Inspection saved = inspections.saveAndFlush(inspection);
    Asset asset = assets.findById(saved.getAssetId()).orElse(null);
    return toResponse(saved, asset);
  }

  @Transactional
  public InspectionResponse decline(UUID actorId, UUID inspectionId, String reason) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (!actor.hasRole(Roles.INSPECTOR)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only the assigned inspector may decline a scheduled inspection.");
    }
    Inspection inspection =
        inspections
            .findAssignedByInspectorWithLock(inspectionId, actorId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));
    if (inspection.getStatus() != InspectionStatus.SCHEDULED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INVALID_STATE",
          "Only a scheduled inspection can be declined before field work starts.");
    }
    FieldSessionRepository sessionRepo =
        sessionsProvider == null ? null : sessionsProvider.getIfAvailable();
    if (sessionRepo != null) {
      boolean hasOpen =
          sessionRepo.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
              .anyMatch(FieldSession::isOpen);
      if (hasOpen) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "SESSION_ALREADY_IN_PROGRESS",
            "Cannot decline while a field session is open; abort the active session instead.");
      }
    }
    try {
      inspection.decline(actorId, reason);
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "DECLINE_REASON_REQUIRED", ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage());
    }
    Inspection saved = inspections.saveAndFlush(inspection);
    InspectorReliabilityEventRepository reliabilityRepo =
        reliabilityProvider == null ? null : reliabilityProvider.getIfAvailable();
    if (reliabilityRepo != null) {
      reliabilityRepo.save(
          new InspectorReliabilityEvent(
              actorId, saved.getId(), null, EventType.DECLINE, reason, saved.getDeclinedAt()));
    }
    eventPublisher.publishEvent(
        new InspectionDeclinedEvent(
            saved.getId(),
            saved.getOwnerCustomerId(),
            actorId,
            saved.getDeclineReason(),
            saved.getDeclinedAt()));
    Asset asset = assets.findById(saved.getAssetId()).orElse(null);
    return toResponse(saved, asset);
  }

  @Transactional(readOnly = true)
  public List<InspectionReassignCandidatePort.ReassignCandidate> evaluateReassignCandidates(
      UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (!actor.hasRole(Roles.MANAGER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only Managers can evaluate reassignment candidates.");
    }
    Inspection inspection =
        inspections
            .findById(inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));
    InspectionReassignCandidatePort port =
        reassignCandidatesProvider == null ? null : reassignCandidatesProvider.getIfAvailable();
    if (port == null) {
      return List.of();
    }
    Instant startAt =
        inspection.getPlannedStartAt() != null ? inspection.getPlannedStartAt() : Instant.now();
    Instant endAt =
        inspection.getPlannedEndAt() != null
            ? inspection.getPlannedEndAt()
            : startAt.plusSeconds(3600);
    return port.evaluateReassignCandidates(
        actor.id(),
        inspection.getId(),
        inspection.getAssetId(),
        inspection.getOwnerCustomerId(),
        startAt,
        endAt);
  }

  @Transactional
  public InspectionResponse cancel(UUID actorId, UUID inspectionId, String reason) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (!actor.hasRole(Roles.MANAGER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only Managers can cancel an inspection.");
    }
    Inspection inspection =
        inspections
            .findById(inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));

    if (actor.ownerCustomerId() != null
        && !actor.ownerCustomerId().equals(inspection.getOwnerCustomerId())) {
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

    InspectionContractSyncPort syncPort =
        contractSyncProvider == null ? null : contractSyncProvider.getIfAvailable();
    if (syncPort != null && syncPort.isLinkedOccurrencePaidOrAccepted(inspectionId)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "OCCURRENCE_ALREADY_SETTLED",
          "Cannot cancel an inspection whose commercial occurrence has already been accepted or paid.");
    }

    inspection.cancel(reason);
    Inspection saved = inspections.saveAndFlush(inspection);
    if (syncPort != null) {
      syncPort.syncCancelFromInspection(saved.getId(), reason);
    }
    Asset asset = assets.findById(saved.getAssetId()).orElse(null);
    return toResponse(saved, asset);
  }

  private boolean hasScheduleConflictExcluding(
      Inspection inspection, UUID inspectorId, UUID droneId, Instant start, Instant end) {
    if (start == null || end == null || inspectorId == null) {
      return false;
    }
    UUID effectiveDrone = droneId != null ? droneId : inspectorId;
    boolean inspectionConflict =
        inspection.isPartnerAssignment()
            ? inspections.hasPlatformPartnerScheduleConflictExcluding(
                inspectorId, effectiveDrone, start, end, inspection.getId())
            : inspections.hasScheduleConflictByOwnerExcluding(
                inspection.getOwnerCustomerId(),
                inspectorId,
                effectiveDrone,
                start,
                end,
                inspection.getId());
    if (inspectionConflict) {
      return true;
    }
    InspectionContractSyncPort syncPort =
        contractSyncProvider == null ? null : contractSyncProvider.getIfAvailable();
    return syncPort != null
        && syncPort.hasScheduleConflict(
            inspectorId, effectiveDrone, start, end, inspection.getId());
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
            ? (inspection.isPartnerAssignment()
                ? drones.findById(inspection.getDroneId()).orElse(null)
                : drones
                    .findByIdAndOwnerCustomerId(
                        inspection.getDroneId(), inspection.getOwnerCustomerId())
                    .orElse(null))
            : null;

    return new InspectionResponse(
        inspection.getId(),
        inspection.getOwnerCustomerId(),
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
        inspection.getPlannedStartAt(),
        inspection.getPlannedEndAt(),
        inspection.getStatus().name(),
        inspection.getCreatedAt(),
        inspection.getUpdatedAt(),
        inspection.getInspectionType() != null ? inspection.getInspectionType().name() : "INITIAL",
        null,
        inspection.isReassignmentRequired(),
        inspection.getDeclineReason(),
        inspection.getDeclinedAt(),
        inspection.getDeclinedByInspectorId(),
        inspection.getCancellationReason(),
        inspection.getOverdueNotifiedAt(),
        inspection.getClarificationCount(),
        inspection.getAssignmentStatus() != null
            ? inspection.getAssignmentStatus().name()
            : "ACCEPTED",
        inspection.getAssignmentAcceptedAt());
  }

  private UserAccess.ActiveUser requireOrgAdmin(UUID actorId) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (!actor.hasRole(Roles.ORG_MANAGER) && !actor.hasRole(Roles.CUSTOMER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only a customer can perform this action.");
    }
    if (actor.ownerCustomerId() == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Actor has no customer ownership scope.");
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

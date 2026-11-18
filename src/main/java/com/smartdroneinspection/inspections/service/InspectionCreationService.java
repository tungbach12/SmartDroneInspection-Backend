package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.api.dto.request.CreateInspectionRequest;
import com.smartdroneinspection.inspections.api.dto.request.CreateReinspectionRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReviewFlightPermitRequest;
import com.smartdroneinspection.inspections.api.dto.response.InspectionResponse;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectorReliabilityEvent;
import com.smartdroneinspection.inspections.domain.InspectorReliabilityEvent.EventType;
import com.smartdroneinspection.inspections.domain.enums.AssignmentStatus;
import com.smartdroneinspection.inspections.domain.enums.FindingStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.events.FlightPermitReviewedEvent;
import com.smartdroneinspection.inspections.events.FlightPermitSubmittedEvent;
import com.smartdroneinspection.inspections.events.InspectionAssignedEvent;
import com.smartdroneinspection.inspections.events.InspectionAssignmentRevokedEvent;
import com.smartdroneinspection.inspections.events.InspectionDeclinedEvent;
import com.smartdroneinspection.inspections.events.InspectionRescheduledEvent;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
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
  private final ObjectProvider<FindingRepository> findingsProvider;
  private final ObjectProvider<ReinspectionVerificationService> reinspectionVerificationsProvider;

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
        null,
        null,
        null);
  }

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
    this(
        inspections,
        assets,
        drones,
        userAccess,
        objectStore,
        eventPublisher,
        sessionsProvider,
        reliabilityProvider,
        contractSyncProvider,
        reassignCandidatesProvider,
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
      ObjectProvider<InspectionReassignCandidatePort> reassignCandidatesProvider,
      ObjectProvider<FindingRepository> findingsProvider,
      ObjectProvider<ReinspectionVerificationService> reinspectionVerificationsProvider) {
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
    this.findingsProvider = findingsProvider;
    this.reinspectionVerificationsProvider = reinspectionVerificationsProvider;
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

    if (inspector.pilotLicenseExpiresAt() != null
        && !inspector.pilotLicenseExpiresAt().isAfter(request.plannedStartAt())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PILOT_LICENSE_EXPIRED",
          "Assigned inspector's pilot license expires before the planned inspection start time.");
    }

    if (!inspector.hasValidPilotLicense(request.plannedStartAt())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PILOT_LICENSE_INVALID",
          "Assigned inspector must have a current license and uploaded license scan at the planned start time.");
    }

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

    if (drone.getRegistrationExpiresAt() != null
        && !drone.getRegistrationExpiresAt().isAfter(request.plannedStartAt())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "DRONE_REGISTRATION_EXPIRED",
          "Assigned drone registration expires before the planned inspection start time.");
    }

    if (!drone.hasCurrentRegistration(request.plannedStartAt()) || !drone.hasKnownMtow()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "DRONE_REGISTRATION_INVALID",
          "Assigned drone must have current registration evidence and known MTOW at the planned start time.");
    }

    if (!inspector.isLicenseClassQualifiedForDrone(
        drone.hasKnownMtow(), drone.requiresClassBForSafety())) {
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

  @Transactional
  public InspectionResponse createQuickReinspection(
      UUID actorId, UUID sourceInspectionId, CreateReinspectionRequest request) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    boolean isPlatformManager = actor.hasRole(Roles.ADMIN) || actor.hasRole(Roles.MANAGER);
    boolean isCustomer = actor.hasRole(Roles.CUSTOMER) || actor.hasRole(Roles.ORG_MANAGER);
    if (!isPlatformManager && !isCustomer) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only organization managers or platform managers can create a re-inspection.");
    }

    Inspection source =
        isPlatformManager
            ? inspections
                .findById(sourceInspectionId)
                .orElseThrow(
                    () ->
                        new BusinessException(
                            HttpStatus.NOT_FOUND, "NOT_FOUND", "Source inspection not found."))
            : inspections
                .findByIdAndOwnerCustomerId(sourceInspectionId, actor.ownerCustomerId())
                .orElseThrow(
                    () ->
                        new BusinessException(
                            HttpStatus.NOT_FOUND, "NOT_FOUND", "Source inspection not found."));

    if (source.getStatus() != InspectionStatus.COMPLETED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INSPECTION_STATE_CONFLICT",
          "Publish the source inspection report before creating a re-inspection.");
    }

    Asset asset =
        assets
            .findById(source.getAssetId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Source asset not found."));
    if (asset.getStatus() == AssetStatus.RETIRED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INVALID_STATE",
          "Cannot schedule a re-inspection for a retired asset.");
    }

    FindingRepository findingRepo =
        findingsProvider != null ? findingsProvider.getIfAvailable() : null;
    if (findingRepo != null) {
      List<Finding> confirmedFindings =
          findingRepo.findByInspectionIdOrderByCreatedAtAsc(source.getId()).stream()
              .filter(f -> f.getStatus() == FindingStatus.CONFIRMED && !f.isDeleted())
              .toList();
      if (confirmedFindings.isEmpty()) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "NO_CONFIRMED_FINDINGS",
            "At least one confirmed defect is required to schedule a re-inspection.");
      }
    }

    UUID reqInspectorId = request != null ? request.inspectorId() : null;
    UUID reqDroneId = request != null ? request.droneId() : null;
    if ((reqInspectorId != null || reqDroneId != null) && !isPlatformManager) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only platform managers can override partner inspector or drone assignment.");
    }
    if ((reqInspectorId != null) != (reqDroneId != null)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "ASSIGNMENT_PAIR_INVALID",
          "Both inspectorId and droneId must be supplied when overriding crew.");
    }

    Instant plannedStartAt =
        request != null && request.plannedStartAt() != null
            ? request.plannedStartAt()
            : Instant.now().plusSeconds(3600);
    Instant plannedEndAt =
        request != null && request.plannedEndAt() != null
            ? request.plannedEndAt()
            : plannedStartAt.plusSeconds(7200);
    if (!plannedEndAt.isAfter(plannedStartAt)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_DATES", "Planned end must be after planned start.");
    }

    UUID targetInspectorId = reqInspectorId != null ? reqInspectorId : source.getInspectorId();
    UUID targetDroneId = reqDroneId != null ? reqDroneId : source.getDroneId();

    UserAccess.ActiveUser inspector = null;
    if (targetInspectorId != null) {
      inspector =
          userAccess
              .findActiveUser(targetInspectorId)
              .filter(u -> u.hasRole(Roles.INSPECTOR))
              .filter(u -> u.hasValidPilotLicense(plannedStartAt))
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.BAD_REQUEST,
                          "INVALID_INSPECTOR",
                          "Assigned inspector must be active with a valid pilot license."));
    }

    if (targetDroneId != null) {
      boolean usePartnerPool = reqDroneId != null || source.isPartnerAssignment();
      Drone drone =
          (usePartnerPool
                  ? drones.findWithLockById(targetDroneId)
                  : drones.findWithLockByIdAndOwnerCustomerId(
                      targetDroneId, source.getOwnerCustomerId()))
              .filter(d -> d.getServiceability() == DroneServiceability.ACTIVE)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.BAD_REQUEST,
                          "INVALID_DRONE",
                          "Assigned drone must be active and registered."));
      if (!drone.hasCurrentRegistration(plannedStartAt) || !drone.hasKnownMtow()) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "DRONE_REGISTRATION_INVALID",
            "Assigned drone must have current registration and known MTOW at planned start.");
      }
      if (inspector != null
          && !inspector.isLicenseClassQualifiedForDrone(
              drone.hasKnownMtow(), drone.requiresClassBForSafety())) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "PILOT_LICENSE_CLASS_INSUFFICIENT",
            "Assigned inspector pilot license class does not qualify for drone MTOW.");
      }
      boolean conflict =
          usePartnerPool
              ? inspections.hasPlatformPartnerScheduleConflict(
                  targetInspectorId, targetDroneId, plannedStartAt, plannedEndAt)
              : inspections.hasScheduleConflictByOwner(
                  source.getOwnerCustomerId(),
                  targetInspectorId,
                  targetDroneId,
                  plannedStartAt,
                  plannedEndAt);
      if (conflict) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "SCHEDULE_CONFLICT",
            "Selected inspector or drone has a schedule conflict during this window.");
      }
    }

    Inspection reinspection =
        Inspection.createReinspection(
            source.getOwnerCustomerId(),
            asset.getId(),
            targetInspectorId,
            targetDroneId,
            plannedStartAt,
            plannedEndAt,
            source.getId());
    if (reqInspectorId != null && !reqInspectorId.equals(source.getInspectorId())) {
      reinspection.markPendingAcceptance();
    }

    Inspection saved = inspections.saveAndFlush(reinspection);
    if (reinspectionVerificationsProvider != null) {
      reinspectionVerificationsProvider.ifAvailable(
          svc -> svc.initializeVerificationsForReinspection(saved.getId(), source.getId()));
    }
    if (saved.getInspectorId() != null) {
      eventPublisher.publishEvent(
          new InspectionAssignedEvent(
              saved.getId(),
              saved.getOwnerCustomerId(),
              saved.getInspectorId(),
              saved.getPlannedStartAt()));
    }
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
      String contentType,
      String permitNumber,
      String issuingAuthority,
      Instant validFrom,
      Instant validTo,
      String droneSerial,
      Integer maxAltitudeM) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    Inspection inspection = findInspectionForPermitUpload(actor, inspectionId);

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

    if (inspection.getStatus() != InspectionStatus.SCHEDULED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INVALID_STATE",
          "Flight permit can only be uploaded or replaced while the inspection is SCHEDULED.");
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

    Instant now = Instant.now();
    Drone assignedDrone = requireValidPilotAndDroneForPermit(inspection, now);
    FlightPermitPolicy.validateMetadataAndCrossChecks(
        inspection,
        assignedDrone,
        permitNumber,
        issuingAuthority,
        validFrom,
        validTo,
        droneSerial,
        maxAltitudeM,
        now);

    String effectiveName =
        fileName != null && !fileName.isBlank() ? fileName.trim() : "flight-permit.pdf";
    String effectiveContentType = FlightPermitPolicy.resolveContentType(contentType, effectiveName);
    String fileSha256 = null;
    Long fileSizeBytes = null;
    String effectiveKey = fileKey;

    if (content != null) {
      FlightPermitPolicy.ValidatedPermitFile validated =
          FlightPermitPolicy.validateFile(content, contentType, effectiveName);
      effectiveContentType = validated.contentType();
      fileSha256 = validated.sha256();
      fileSizeBytes = validated.sizeBytes();
      if (objectStore.isPresent()) {
        effectiveKey =
            "inspections/"
                + inspectionId
                + "/flight-permit/"
                + UUID.randomUUID()
                + "-"
                + effectiveName;
        try {
          objectStore
              .get()
              .put(
                  effectiveKey,
                  effectiveContentType,
                  content.length,
                  new ByteArrayInputStream(content));
        } catch (IOException e) {
          throw new BusinessException(
              HttpStatus.BAD_GATEWAY, "STORAGE_FAILED", "Failed to store flight permit file.");
        }
      }
    }

    if (effectiveKey == null || effectiveKey.isBlank()) {
      effectiveKey = "permits/" + UUID.randomUUID() + "-" + effectiveName;
    }

    inspection.recordFlightPermit(
        effectiveKey,
        effectiveName,
        permitNumber,
        issuingAuthority,
        validFrom,
        validTo,
        droneSerial,
        maxAltitudeM,
        fileSha256,
        fileSizeBytes,
        effectiveContentType,
        now,
        actorId);
    Inspection saved = inspections.saveAndFlush(inspection);
    eventPublisher.publishEvent(
        new FlightPermitSubmittedEvent(
            saved.getId(),
            saved.getOwnerCustomerId(),
            saved.getInspectorId(),
            actorId,
            saved.getFlightPermitNumber(),
            saved.getFlightPermitAuthority(),
            now));
    Asset asset =
        assets
            .findByIdAndOwnerCustomerId(inspection.getAssetId(), inspection.getOwnerCustomerId())
            .orElse(null);
    return toResponse(saved, asset);
  }

  @Transactional
  public InspectionResponse reviewFlightPermit(
      UUID actorId,
      UUID inspectionId,
      ReviewFlightPermitRequest.Decision decision,
      String reason,
      String notes) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (!actor.hasRole(Roles.MANAGER) && !actor.hasRole(Roles.ADMIN)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only a Platform Manager or Admin can verify flight permits.");
    }

    Inspection inspection =
        inspections
            .findById(inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));

    if (inspection.isInspectedBy(actorId)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "SELF_REVIEW_FORBIDDEN",
          "The assigned inspector cannot verify their own flight permit.");
    }
    if (inspection.getStatus() != InspectionStatus.SCHEDULED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INVALID_STATE",
          "Flight permit can only be reviewed while the inspection is SCHEDULED.");
    }
    if (!inspection.hasFlightPermit()) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FLIGHT_PERMIT_REQUIRED",
          "No flight permit has been uploaded for this inspection.");
    }

    Instant now = Instant.now();
    boolean approved = decision == ReviewFlightPermitRequest.Decision.APPROVED;
    if (approved) {
      if (inspection.getFlightPermitVerificationStatus()
          != com.smartdroneinspection.inspections.domain.enums.FlightPermitVerificationStatus
              .PENDING_REVIEW) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "PERMIT_NOT_PENDING_REVIEW",
            "Only a flight permit in PENDING_REVIEW status can be approved.");
      }
      Drone assignedDrone = requireValidPilotAndDroneForPermit(inspection, now);
      FlightPermitPolicy.validateMetadataAndCrossChecks(
          inspection,
          assignedDrone,
          inspection.getFlightPermitNumber(),
          inspection.getFlightPermitAuthority(),
          inspection.getFlightPermitValidFrom(),
          inspection.getFlightPermitValidTo(),
          inspection.getFlightPermitDroneSerial(),
          inspection.getFlightPermitMaxAltitudeM(),
          now);
      inspection.approveFlightPermit(actorId, notes, now);
    } else {
      if (reason == null || reason.isBlank()) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "REJECTION_REASON_REQUIRED",
            "A rejection reason is required when rejecting a flight permit.");
      }
      if (inspection.getFlightPermitVerificationStatus()
              != com.smartdroneinspection.inspections.domain.enums.FlightPermitVerificationStatus
                  .PENDING_REVIEW
          && inspection.getFlightPermitVerificationStatus()
              != com.smartdroneinspection.inspections.domain.enums.FlightPermitVerificationStatus
                  .APPROVED) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "PERMIT_NOT_REVIEWABLE",
            "This flight permit cannot be rejected from its current state.");
      }
      inspection.rejectFlightPermit(actorId, reason, notes, now);
    }

    Inspection saved = inspections.saveAndFlush(inspection);
    eventPublisher.publishEvent(
        new FlightPermitReviewedEvent(
            saved.getId(),
            saved.getOwnerCustomerId(),
            saved.getInspectorId(),
            actorId,
            approved,
            saved.getFlightPermitRejectionReason(),
            now));
    Asset asset = assets.findById(saved.getAssetId()).orElse(null);
    return toResponse(saved, asset);
  }

  private Inspection findInspectionForPermitUpload(UserAccess.ActiveUser actor, UUID inspectionId) {
    if (actor.hasRole(Roles.ADMIN) || actor.hasRole(Roles.MANAGER)) {
      return inspections
          .findById(inspectionId)
          .orElseThrow(
              () ->
                  new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));
    }
    return (actor.isInspectorPartner()
            ? inspections.findAssignedByInspector(inspectionId, actor.id())
            : inspections.findByIdAndOwnerCustomerId(inspectionId, actor.ownerCustomerId()))
        .orElseThrow(
            () -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));
  }

  private Drone requireValidPilotAndDroneForPermit(Inspection inspection, Instant permitCheckAt) {
    UserAccess.ActiveUser assignedInspector =
        userAccess
            .findActiveUser(inspection.getInspectorId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_INSPECTOR",
                        "Assigned inspector is no longer active."));

    Instant plannedFlightAt =
        inspection.getPlannedStartAt() != null ? inspection.getPlannedStartAt() : permitCheckAt;
    if (!assignedInspector.hasValidPilotLicense(permitCheckAt)
        || !assignedInspector.hasValidPilotLicense(plannedFlightAt)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PILOT_LICENSE_REQUIRED",
          "The assigned inspector must hold a current license with an uploaded license scan through the planned flight.");
    }

    if (inspection.getDroneId() == null) {
      return null;
    }
    Drone assignedDrone = drones.findById(inspection.getDroneId()).orElse(null);
    if (assignedDrone == null || assignedDrone.getServiceability() != DroneServiceability.ACTIVE) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_DRONE", "Assigned drone is no longer active.");
    }
    if (assignedDrone.getRegistrationExpiresAt() != null
        && (!assignedDrone.getRegistrationExpiresAt().isAfter(permitCheckAt)
            || !assignedDrone.getRegistrationExpiresAt().isAfter(plannedFlightAt))) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "DRONE_REGISTRATION_EXPIRED",
          "Assigned drone registration has expired or expires before the planned flight.");
    }
    if (!assignedDrone.hasCurrentRegistration(permitCheckAt)
        || !assignedDrone.hasCurrentRegistration(plannedFlightAt)
        || !assignedDrone.hasKnownMtow()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "DRONE_REGISTRATION_INVALID",
          "Assigned drone must have current registration evidence and known MTOW before a flight permit is uploaded.");
    }
    if (!assignedInspector.isLicenseClassQualifiedForDrone(
        assignedDrone.hasKnownMtow(), assignedDrone.requiresClassBForSafety())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PILOT_LICENSE_CLASS_INSUFFICIENT",
          "Assigned inspector must have a recognized license class matching a known drone MTOW.");
    }
    return assignedDrone;
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
    String contentType =
        detail.flightPermitContentType() != null && !detail.flightPermitContentType().isBlank()
            ? detail.flightPermitContentType()
            : FlightPermitPolicy.resolveContentType(null, fileName);

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
        userAccess
            .findActiveUser(inspection.getInspectorId())
            .filter(user -> user.hasRole(Roles.INSPECTOR))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_INSPECTOR",
                        "Assigned inspector must be active."));
    if (assignedInspector.pilotLicenseExpiresAt() != null
        && !assignedInspector.pilotLicenseExpiresAt().isAfter(plannedStartAt)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PILOT_LICENSE_EXPIRED",
          "Assigned inspector's pilot license expires before the rescheduled inspection start time.");
    }
    if (!assignedInspector.hasValidPilotLicense(plannedStartAt)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PILOT_LICENSE_INVALID",
          "Assigned inspector must have a current license and uploaded scan at the planned start time.");
    }
    if (inspection.getDroneId() != null) {
      Drone assignedDrone = drones.findById(inspection.getDroneId()).orElse(null);
      if (assignedDrone == null
          || assignedDrone.getServiceability() != DroneServiceability.ACTIVE) {
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
      if (!assignedDrone.hasCurrentRegistration(plannedStartAt) || !assignedDrone.hasKnownMtow()) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "DRONE_REGISTRATION_INVALID",
            "Assigned drone must have a current registration and known MTOW.");
      }
      if (!assignedInspector.isLicenseClassQualifiedForDrone(
          assignedDrone.hasKnownMtow(), assignedDrone.requiresClassBForSafety())) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "PILOT_LICENSE_CLASS_INSUFFICIENT",
            "Assigned inspector must have a recognized license class matching drone MTOW.");
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
    if (!newInspector.hasValidPilotLicense(checkAt)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PILOT_LICENSE_REQUIRED",
          "The assigned inspector must hold a current pilot license with uploaded evidence.");
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
      if (!newDrone.hasCurrentRegistration(checkAt) || !newDrone.hasKnownMtow()) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "DRONE_REGISTRATION_INVALID",
            "Assigned drone must have current registration evidence and known MTOW.");
      }
      if (!newInspector.isLicenseClassQualifiedForDrone(
          newDrone.hasKnownMtow(), newDrone.requiresClassBForSafety())) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "PILOT_LICENSE_CLASS_INSUFFICIENT",
            "Assigned drone has MTOW >= 2 kg (Category 3+ under Decree 288/2025/ND-CP) and requires a Class B pilot license.");
      }
    } else if (effectiveDroneId != null) {
      Drone currentDrone = drones.findById(effectiveDroneId).orElse(null);
      if (currentDrone == null || currentDrone.getServiceability() != DroneServiceability.ACTIVE) {
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
      if (!currentDrone.hasCurrentRegistration(checkAt) || !currentDrone.hasKnownMtow()) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "DRONE_REGISTRATION_INVALID",
            "The assigned drone must have a current registration and known MTOW.");
      }
      if (!newInspector.isLicenseClassQualifiedForDrone(
          currentDrone.hasKnownMtow(), currentDrone.requiresClassBForSafety())) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "PILOT_LICENSE_CLASS_INSUFFICIENT",
            "Assigned drone has MTOW >= 2 kg (Category 3+ under Decree 288/2025/ND-CP) and requires a Class B pilot license.");
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

    UserAccess.ActiveUser verifiedByUser =
        inspection.getFlightPermitVerifiedBy() != null
            ? userAccess.findActiveUser(inspection.getFlightPermitVerifiedBy()).orElse(null)
            : null;
    String verifiedByName = verifiedByUser != null ? verifiedByUser.fullName() : null;
    UserAccess.ActiveUser ownerCustomerUser =
        inspection.getOwnerCustomerId() != null
            ? userAccess.findUser(inspection.getOwnerCustomerId()).orElse(null)
            : null;
    String ownerCustomerName = ownerCustomerUser != null ? ownerCustomerUser.fullName() : null;
    String ownerCustomerEmail = ownerCustomerUser != null ? ownerCustomerUser.email() : null;
    String ownerCustomerPhone = ownerCustomerUser != null ? ownerCustomerUser.phoneNumber() : null;
    UserAccess.ActiveUser managerUser =
        (verifiedByUser != null
                && verifiedByUser.fullName() != null
                && !verifiedByUser.fullName().isBlank())
            ? verifiedByUser
            : userAccess.listActivePlatformManagers().stream().findFirst().orElse(null);
    String managerName = managerUser != null ? managerUser.fullName() : null;
    String managerEmail = managerUser != null ? managerUser.email() : null;
    String managerPhone = managerUser != null ? managerUser.phoneNumber() : null;

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
        inspection.getSourceInspectionId(),
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
        inspection.getAssignmentAcceptedAt(),
        inspection.getFlightPermitNumber(),
        inspection.getFlightPermitAuthority(),
        inspection.getFlightPermitValidFrom(),
        inspection.getFlightPermitValidTo(),
        inspection.getFlightPermitDroneSerial(),
        inspection.getFlightPermitMaxAltitudeM(),
        inspection.getFlightPermitFileSha256(),
        inspection.getFlightPermitFileSizeBytes(),
        inspection.getFlightPermitContentType(),
        inspection.getFlightPermitUploadedAt(),
        inspection.getFlightPermitUploadedBy(),
        inspection.getFlightPermitVerificationStatus().name(),
        inspection.getFlightPermitVerifiedBy(),
        verifiedByName,
        inspection.getFlightPermitVerifiedAt(),
        inspection.getFlightPermitRejectionReason(),
        inspection.getFlightPermitReviewNotes(),
        ownerCustomerName,
        ownerCustomerEmail,
        ownerCustomerPhone,
        managerName,
        managerEmail,
        managerPhone);
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

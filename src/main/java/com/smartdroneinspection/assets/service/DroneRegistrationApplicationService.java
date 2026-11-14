package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.ReviewDroneRegistrationApplicationRequest;
import com.smartdroneinspection.assets.api.dto.request.SubmitDroneRegistrationApplicationRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneRegistrationApplicationResponse;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.DroneDocumentConfig;
import com.smartdroneinspection.assets.domain.DroneRegistrationApplication;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.DroneDocumentConfigRepository;
import com.smartdroneinspection.assets.repository.DroneRegistrationApplicationRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.assets.service.DroneEvidenceUploadService.EvidenceContent;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.shared.storage.EvidenceUploadValidator;
import com.smartdroneinspection.users.UserAccess;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DroneRegistrationApplicationService {
  private final DroneRegistrationApplicationRepository applications;
  private final DroneRepository drones;
  private final UserAccess userAccess;
  private final Optional<EvidenceObjectStore> objectStore;
  private final DroneEvidenceUploadService evidenceUploads;
  private final DroneDocumentConfigRepository documentConfigs;

  public DroneRegistrationApplicationService(
      DroneRegistrationApplicationRepository applications,
      DroneRepository drones,
      UserAccess userAccess,
      Optional<EvidenceObjectStore> objectStore,
      DroneEvidenceUploadService evidenceUploads,
      DroneDocumentConfigRepository documentConfigs) {
    this.applications = applications;
    this.drones = drones;
    this.userAccess = userAccess;
    this.objectStore = objectStore;
    this.evidenceUploads = evidenceUploads;
    this.documentConfigs = documentConfigs;
  }

  private Map<String, String> resolveAndValidateDocuments(
      UUID inspectorId,
      SubmitDroneRegistrationApplicationRequest request,
      List<String> fallbackImageKeys) {
    Map<String, String> docMap =
        request.documentFiles() != null ? new HashMap<>(request.documentFiles()) : new HashMap<>();
    if (request.registrationFileKey() != null && !request.registrationFileKey().isBlank()) {
      docMap.putIfAbsent("REGISTRATION", request.registrationFileKey().trim());
    }
    if (request.ownershipFileKey() != null && !request.ownershipFileKey().isBlank()) {
      docMap.putIfAbsent("OWNERSHIP", request.ownershipFileKey().trim());
    }
    if (request.inspectionFileKey() != null && !request.inspectionFileKey().isBlank()) {
      docMap.putIfAbsent("INSPECTION", request.inspectionFileKey().trim());
    }

    // Dynamic validation: check required active configs
    List<DroneDocumentConfig> activeConfigs =
        documentConfigs.findAllByIsActiveTrueOrderByDisplayOrderAscCreatedAtAsc();
    if (activeConfigs.isEmpty()) {
      // Fallback: REGISTRATION and OWNERSHIP are mandatory
      if (!docMap.containsKey("REGISTRATION") || docMap.get("REGISTRATION").isBlank()) {
        throw new AuthException(
            HttpStatus.BAD_REQUEST,
            "MISSING_REQUIRED_DOCUMENT",
            "Missing required document: REGISTRATION");
      }
      if (!docMap.containsKey("OWNERSHIP") || docMap.get("OWNERSHIP").isBlank()) {
        throw new AuthException(
            HttpStatus.BAD_REQUEST,
            "MISSING_REQUIRED_DOCUMENT",
            "Missing required document: OWNERSHIP");
      }
    } else {
      for (DroneDocumentConfig config : activeConfigs) {
        if (config.isRequired()) {
          String key = docMap.get(config.getCode());
          if (key == null || key.isBlank()) {
            throw new AuthException(
                HttpStatus.BAD_REQUEST,
                "MISSING_REQUIRED_DOCUMENT",
                "Missing required document: "
                    + config.getTitleVi()
                    + " ("
                    + config.getCode()
                    + ")");
          }
        }
      }
    }

    // Validate ownership of all uploaded keys
    for (Map.Entry<String, String> entry : docMap.entrySet()) {
      if (entry.getValue() != null && !entry.getValue().isBlank()) {
        evidenceUploads.validateReference(inspectorId, entry.getKey(), entry.getValue());
      }
    }

    List<String> images = request.imageFileKeys();
    if (images == null || images.isEmpty()) {
      if (fallbackImageKeys == null || fallbackImageKeys.isEmpty()) {
        throw new AuthException(
            HttpStatus.BAD_REQUEST, "MISSING_DRONE_IMAGE", "At least one drone image is required.");
      }
    } else {
      for (String imgKey : images) {
        if (imgKey != null && !imgKey.isBlank()) {
          evidenceUploads.validateReference(inspectorId, "DRONE_IMAGE", imgKey);
        }
      }
    }

    return docMap;
  }

  @Transactional
  public DroneRegistrationApplicationResponse submit(
      UUID inspectorId,
      SubmitDroneRegistrationApplicationRequest request,
      String ip,
      String agent,
      String correlationId) {
    requireInspector(inspectorId);
    Map<String, String> docMap = resolveAndValidateDocuments(inspectorId, request, null);

    if (!request.registrationExpiresAt().isAfter(Instant.now())) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST,
          "DRONE_REGISTRATION_EXPIRED",
          "Drone registration must be current.");
    }
    String serial = request.serialNumber().trim();
    if (applications.existsBySerialNumberIgnoreCaseAndStatus(serial, "PENDING")) {
      throw new AuthException(
          HttpStatus.CONFLICT,
          "DUPLICATE_SERIAL_NUMBER",
          "An application for this drone serial is already pending review.");
    }
    Optional<Drone> existingDroneOpt = drones.findBySerialNumberIgnoreCase(serial);
    UUID existingDroneId = null;
    if (existingDroneOpt.isPresent()) {
      Drone existingDrone = existingDroneOpt.get();
      if (!inspectorId.equals(existingDrone.getInspectorOwnerId())) {
        throw new AuthException(
            HttpStatus.CONFLICT,
            "DUPLICATE_SERIAL_NUMBER",
            "Drone serial is already registered by another organization or inspector.");
      }
      existingDroneId = existingDrone.getId();
    }
    var application =
        new DroneRegistrationApplication(
            inspectorId,
            serial,
            request.model(),
            request.manufacturer(),
            request.registrationNumber(),
            docMap.getOrDefault("REGISTRATION", request.registrationFileKey()),
            request.registrationExpiresAt(),
            docMap.getOrDefault("OWNERSHIP", request.ownershipFileKey()),
            docMap.get("INSPECTION"),
            docMap,
            request.maxWindResistanceMps(),
            request.opticalZoomLevel(),
            Boolean.TRUE.equals(request.hasThermalCamera()),
            Boolean.TRUE.equals(request.hasRtk()),
            request.imageFileKeys(),
            request.features());
    if (existingDroneId != null) {
      application.linkExistingDrone(existingDroneId);
    }
    application = applications.saveAndFlush(application);
    userAccess.recordSecurityAudit(
        inspectorId,
        application.getId(),
        "DRONE_REGISTRATION_SUBMITTED",
        "SUCCESS",
        ip,
        agent,
        correlationId);
    var inspector = userAccess.findActiveUser(inspectorId).orElse(null);
    return DroneRegistrationApplicationResponse.from(application, inspector);
  }

  @Transactional
  public DroneRegistrationApplicationResponse resubmit(
      UUID inspectorId,
      UUID applicationId,
      SubmitDroneRegistrationApplicationRequest request,
      String ip,
      String agent,
      String correlationId) {
    requireInspector(inspectorId);
    DroneRegistrationApplication application =
        applications.findById(applicationId).orElseThrow(this::notFound);
    if (!inspectorId.equals(application.getInspectorUserId())) {
      throw new AuthException(
          HttpStatus.FORBIDDEN,
          "NOT_APPLICATION_OWNER",
          "Only the submitting Inspector can resubmit this application.");
    }
    List<String> fallbackImages = application.getImageFileKeys();
    Map<String, String> docMap = resolveAndValidateDocuments(inspectorId, request, fallbackImages);
    List<String> finalImageKeys =
        (request.imageFileKeys() != null && !request.imageFileKeys().isEmpty())
            ? request.imageFileKeys()
            : fallbackImages;

    if (!request.registrationExpiresAt().isAfter(Instant.now())) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST,
          "DRONE_REGISTRATION_EXPIRED",
          "Drone registration must be current.");
    }
    try {
      application.resubmit(
          request.model(),
          request.manufacturer(),
          request.registrationNumber(),
          docMap.getOrDefault("REGISTRATION", request.registrationFileKey()),
          request.registrationExpiresAt(),
          docMap.getOrDefault("OWNERSHIP", request.ownershipFileKey()),
          docMap.get("INSPECTION"),
          docMap,
          request.maxWindResistanceMps(),
          request.opticalZoomLevel(),
          Boolean.TRUE.equals(request.hasThermalCamera()),
          Boolean.TRUE.equals(request.hasRtk()),
          finalImageKeys,
          request.features());
    } catch (IllegalStateException ex) {
      throw new AuthException(HttpStatus.CONFLICT, "RESUBMIT_INVALID", ex.getMessage());
    }
    applications.saveAndFlush(application);
    userAccess.recordSecurityAudit(
        inspectorId,
        application.getId(),
        "DRONE_REGISTRATION_RESUBMITTED",
        "SUCCESS",
        ip,
        agent,
        correlationId);
    var inspector = userAccess.findActiveUser(inspectorId).orElse(null);
    return DroneRegistrationApplicationResponse.from(application, inspector);
  }

  @Transactional(readOnly = true)
  public EvidenceContent openEvidence(UUID callerId, UUID applicationId, String type) {
    DroneRegistrationApplication application =
        applications.findById(applicationId).orElseThrow(this::notFound);
    if (!callerId.equals(application.getInspectorUserId())) {
      requireReviewer(callerId);
    }
    String fileKey = null;
    if (type != null && type.startsWith("DRONE_IMAGE")) {
      int idx = 0;
      if (type.contains("_") && !type.equals("DRONE_IMAGE")) {
        try {
          idx = Integer.parseInt(type.substring(type.lastIndexOf('_') + 1));
        } catch (NumberFormatException ignored) {
        }
      }
      if (application.getImageFileKeys().size() > idx) {
        fileKey = application.getImageFileKeys().get(idx);
      }
    }
    if (fileKey == null && application.getDocumentFiles() != null) {
      fileKey = application.getDocumentFiles().get(type);
    }
    if (fileKey == null) {
      fileKey =
          switch (type == null ? "" : type) {
            case "REGISTRATION" -> application.getRegistrationFileKey();
            case "OWNERSHIP" -> application.getOwnershipFileKey();
            case "INSPECTION" -> application.getInspectionFileKey();
            default -> null;
          };
    }
    if (fileKey == null || !DroneEvidenceUploadService.isEvidenceKey(fileKey)) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Evidence document not found");
    }
    try {
      String extension = fileKey.substring(fileKey.lastIndexOf('.') + 1);
      return new EvidenceContent(
          EvidenceUploadValidator.contentType(extension),
          objectStore
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.BAD_GATEWAY,
                          "STORAGE_UNAVAILABLE",
                          "Object storage is unavailable"))
              .open(fileKey));
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE", "Object storage is unavailable");
    }
  }

  @Transactional(readOnly = true)
  public EvidenceContent openDroneImage(UUID callerId, UUID droneId, int index) {
    Drone drone =
        drones
            .findById(droneId)
            .orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Drone not found."));
    if (drone.getInspectorOwnerId() == null || !callerId.equals(drone.getInspectorOwnerId())) {
      requireReviewer(callerId);
    }
    List<String> keys = drone.getImageFileKeys();
    if (keys.isEmpty() || index < 0 || index >= keys.size()) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Drone image not found.");
    }
    String fileKey = keys.get(index);
    if (!DroneEvidenceUploadService.isEvidenceKey(fileKey)) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Drone image not found.");
    }
    try {
      String extension = fileKey.substring(fileKey.lastIndexOf('.') + 1);
      return new EvidenceContent(
          EvidenceUploadValidator.contentType(extension),
          objectStore
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.BAD_GATEWAY,
                          "STORAGE_UNAVAILABLE",
                          "Object storage is unavailable"))
              .open(fileKey));
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE", "Object storage is unavailable");
    }
  }

  @Transactional(readOnly = true)
  public List<DroneRegistrationApplicationResponse> mine(UUID inspectorId) {
    requireInspector(inspectorId);
    var inspector = userAccess.findUser(inspectorId).orElse(null);
    return applications.findAllByInspectorUserIdOrderByCreatedAtDesc(inspectorId).stream()
        .map(app -> DroneRegistrationApplicationResponse.from(app, inspector))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<com.smartdroneinspection.assets.api.dto.response.InspectorDroneResponse>
      inspectorDrones(UUID inspectorId) {
    requireInspector(inspectorId);
    var inspector = userAccess.findUser(inspectorId).orElse(null);
    return drones.findAllByInspectorOwnerId(inspectorId).stream()
        .filter(d -> d.getServiceability() != DroneServiceability.RETIRED)
        .map(
            d ->
                com.smartdroneinspection.assets.api.dto.response.InspectorDroneResponse.from(
                    d, inspector))
        .toList();
  }

  @Transactional
  public com.smartdroneinspection.assets.api.dto.response.InspectorDroneResponse
      updateMyDroneServiceability(UUID inspectorId, UUID droneId, DroneServiceability target) {
    requireInspector(inspectorId);
    Drone drone =
        drones
            .findById(droneId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "DRONE_NOT_FOUND", "Drone not found"));
    if (!inspectorId.equals(drone.getInspectorOwnerId())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not own this drone");
    }
    if (drone.getServiceability() == DroneServiceability.RETIRED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INVALID_STATE", "Retired drones cannot change serviceability");
    }
    drone.changeServiceability(target);
    Drone saved = drones.saveAndFlush(drone);
    var inspector = userAccess.findUser(inspectorId).orElse(null);
    return com.smartdroneinspection.assets.api.dto.response.InspectorDroneResponse.from(
        saved, inspector);
  }

  @Transactional
  public void deleteMyDrone(UUID inspectorId, UUID droneId) {
    requireInspector(inspectorId);
    Drone drone =
        drones
            .findById(droneId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "DRONE_NOT_FOUND", "Drone not found"));
    if (!inspectorId.equals(drone.getInspectorOwnerId())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not own this drone");
    }
    if (drone.getServiceability() != DroneServiceability.RETIRED) {
      drone.changeServiceability(DroneServiceability.RETIRED);
      drones.saveAndFlush(drone);
    }
  }

  @Transactional(readOnly = true)
  public List<DroneRegistrationApplicationResponse> pending(UUID reviewerId) {
    requireReviewer(reviewerId);
    return applications.findAllByStatusOrderByCreatedAtAsc("PENDING").stream()
        .map(
            app -> {
              var inspector = userAccess.findUser(app.getInspectorUserId()).orElse(null);
              return DroneRegistrationApplicationResponse.from(app, inspector);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public List<DroneRegistrationApplicationResponse> history(UUID reviewerId) {
    requireReviewer(reviewerId);
    return applications
        .findAllByStatusInOrderByReviewedAtDesc(List.of("APPROVED", "REJECTED"))
        .stream()
        .map(
            app -> {
              var inspector = userAccess.findUser(app.getInspectorUserId()).orElse(null);
              var reviewer =
                  app.getReviewedByUserId() != null
                      ? userAccess.findUser(app.getReviewedByUserId()).orElse(null)
                      : null;
              return DroneRegistrationApplicationResponse.from(app, inspector, reviewer);
            })
        .toList();
  }

  @Transactional
  public DroneRegistrationApplicationResponse approve(
      UUID reviewerId, UUID applicationId, String ip, String agent, String correlationId) {
    requireReviewer(reviewerId);
    DroneRegistrationApplication application = requirePending(applicationId);
    requireInspector(application.getInspectorUserId());
    if (!application.getRegistrationExpiresAt().isAfter(Instant.now())) {
      throw new AuthException(
          HttpStatus.CONFLICT,
          "DRONE_REGISTRATION_EXPIRED",
          "Drone registration expired before approval.");
    }
    Drone drone;
    if (application.getDroneId() != null) {
      drone = drones.findById(application.getDroneId()).orElse(null);
      if (drone != null) {
        drone.updateDetails(application.getModel(), application.getManufacturer());
        drone.updateRegistration(
            application.getRegistrationNumber(),
            application.getRegistrationFileKey(),
            application.getRegistrationExpiresAt());
        drone.applyCapabilitiesAndDocuments(
            application.getMaxWindResistanceMps(),
            application.getOpticalZoomLevel(),
            application.isHasThermalCamera(),
            application.isHasRtk(),
            application.getDocumentFiles(),
            application.getImageFileKeys(),
            application.getFeatures());
        drone = drones.saveAndFlush(drone);
      } else {
        drone =
            Drone.createInspectorOwned(
                application.getInspectorUserId(),
                application.getSerialNumber(),
                application.getModel(),
                application.getManufacturer(),
                application.getRegistrationNumber(),
                application.getRegistrationFileKey(),
                application.getRegistrationExpiresAt());
        drone.applyCapabilitiesAndDocuments(
            application.getMaxWindResistanceMps(),
            application.getOpticalZoomLevel(),
            application.isHasThermalCamera(),
            application.isHasRtk(),
            application.getDocumentFiles(),
            application.getImageFileKeys(),
            application.getFeatures());
        drone = drones.saveAndFlush(drone);
      }
    } else {
      if (drones.existsBySerialNumberIgnoreCase(application.getSerialNumber())) {
        throw new AuthException(
            HttpStatus.CONFLICT, "DUPLICATE_SERIAL_NUMBER", "Drone serial is already registered.");
      }
      drone =
          Drone.createInspectorOwned(
              application.getInspectorUserId(),
              application.getSerialNumber(),
              application.getModel(),
              application.getManufacturer(),
              application.getRegistrationNumber(),
              application.getRegistrationFileKey(),
              application.getRegistrationExpiresAt());
      drone.applyCapabilitiesAndDocuments(
          application.getMaxWindResistanceMps(),
          application.getOpticalZoomLevel(),
          application.isHasThermalCamera(),
          application.isHasRtk(),
          application.getDocumentFiles(),
          application.getImageFileKeys(),
          application.getFeatures());
      drone = drones.saveAndFlush(drone);
    }
    application.approve(reviewerId, drone.getId());
    applications.saveAndFlush(application);
    userAccess.recordSecurityAudit(
        reviewerId,
        application.getId(),
        "DRONE_REGISTRATION_APPROVED",
        "SUCCESS",
        ip,
        agent,
        correlationId);
    var inspector = userAccess.findUser(application.getInspectorUserId()).orElse(null);
    var reviewer = userAccess.findUser(reviewerId).orElse(null);
    return DroneRegistrationApplicationResponse.from(application, inspector, reviewer);
  }

  @Transactional
  public DroneRegistrationApplicationResponse reject(
      UUID reviewerId,
      UUID applicationId,
      ReviewDroneRegistrationApplicationRequest body,
      String ip,
      String agent,
      String correlationId) {
    requireReviewer(reviewerId);
    DroneRegistrationApplication application = requirePending(applicationId);
    application.reject(reviewerId, body.reviewNote().trim());
    applications.saveAndFlush(application);
    userAccess.recordSecurityAudit(
        reviewerId,
        application.getId(),
        "DRONE_REGISTRATION_REJECTED",
        "SUCCESS",
        ip,
        agent,
        correlationId);
    var inspector = userAccess.findUser(application.getInspectorUserId()).orElse(null);
    var reviewer = userAccess.findUser(reviewerId).orElse(null);
    return DroneRegistrationApplicationResponse.from(application, inspector, reviewer);
  }

  private void requireInspector(UUID inspectorId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(inspectorId)
            .orElseThrow(
                () ->
                    new AuthException(
                        HttpStatus.FORBIDDEN,
                        "INSPECTOR_PARTNER_REQUIRED",
                        "An active Inspector partner is required."));
    if (!actor.isInspectorPartner()) {
      throw new AuthException(
          HttpStatus.FORBIDDEN,
          "INSPECTOR_PARTNER_REQUIRED",
          "An active Inspector partner is required.");
    }
  }

  private void requireReviewer(UUID reviewerId) {
    UserAccess.ActiveUser admin =
        userAccess
            .findActiveUser(reviewerId)
            .orElseThrow(
                () ->
                    new AuthException(
                        HttpStatus.FORBIDDEN,
                        "REVIEWER_REQUIRED",
                        "Platform Reviewer access is required."));
    if (!"PLATFORM".equals(admin.actorZone())
        || (!admin.hasRole("ADMIN") && !admin.hasRole("MANAGER"))) {
      throw new AuthException(
          HttpStatus.FORBIDDEN, "REVIEWER_REQUIRED", "Platform Reviewer access is required.");
    }
  }

  private DroneRegistrationApplication requirePending(UUID applicationId) {
    return applications
        .findByIdAndStatus(applicationId, "PENDING")
        .orElseThrow(
            () ->
                new AuthException(
                    HttpStatus.NOT_FOUND,
                    "APPLICATION_NOT_FOUND",
                    "Application not found or not pending review."));
  }

  private BusinessException notFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Drone registration application not found.");
  }
}

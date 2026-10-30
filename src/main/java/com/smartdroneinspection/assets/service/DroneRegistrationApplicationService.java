package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.ReviewDroneRegistrationApplicationRequest;
import com.smartdroneinspection.assets.api.dto.request.SubmitDroneRegistrationApplicationRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneRegistrationApplicationResponse;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.DroneRegistrationApplication;
import com.smartdroneinspection.assets.repository.DroneRegistrationApplicationRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.shared.storage.EvidenceUploadValidator;
import com.smartdroneinspection.users.UserAccess;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
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

  public DroneRegistrationApplicationService(
      DroneRegistrationApplicationRepository applications,
      DroneRepository drones,
      UserAccess userAccess,
      Optional<EvidenceObjectStore> objectStore,
      DroneEvidenceUploadService evidenceUploads) {
    this.applications = applications;
    this.drones = drones;
    this.userAccess = userAccess;
    this.objectStore = objectStore;
    this.evidenceUploads = evidenceUploads;
  }

  @Transactional
  public DroneRegistrationApplicationResponse submit(
      UUID inspectorId,
      SubmitDroneRegistrationApplicationRequest request,
      String ip,
      String agent,
      String correlationId) {
    requireInspector(inspectorId);
    evidenceUploads.validateReference(inspectorId, "REGISTRATION", request.registrationFileKey());
    evidenceUploads.validateReference(inspectorId, "OWNERSHIP", request.ownershipFileKey());
    evidenceUploads.validateReference(inspectorId, "INSPECTION", request.inspectionFileKey());
    if (!request.registrationExpiresAt().isAfter(Instant.now())) {
      throw new AuthException(
          HttpStatus.BAD_REQUEST,
          "DRONE_REGISTRATION_EXPIRED",
          "Drone registration must be current.");
    }
    String serial = request.serialNumber().trim();
    if (drones.existsBySerialNumberIgnoreCase(serial)
        || applications.existsBySerialNumberIgnoreCaseAndStatus(serial, "PENDING")) {
      throw new AuthException(
          HttpStatus.CONFLICT,
          "DUPLICATE_SERIAL_NUMBER",
          "Drone serial is already registered or pending.");
    }
    var application =
        applications.saveAndFlush(
            new DroneRegistrationApplication(
                inspectorId,
                serial,
                request.model(),
                request.manufacturer(),
                request.registrationNumber(),
                request.registrationFileKey(),
                request.registrationExpiresAt(),
                request.ownershipFileKey(),
                request.inspectionFileKey()));
    userAccess.recordSecurityAudit(
        inspectorId,
        application.getId(),
        "DRONE_REGISTRATION_SUBMITTED",
        "SUCCESS",
        ip,
        agent,
        correlationId);
    return DroneRegistrationApplicationResponse.from(application);
  }

  @Transactional(readOnly = true)
  public EvidenceContent openEvidence(UUID adminId, UUID applicationId, String type) {
    requireAdmin(adminId);
    DroneRegistrationApplication application =
        applications.findById(applicationId).orElseThrow(this::notFound);
    String fileKey =
        switch (type == null ? "" : type) {
          case "REGISTRATION" -> application.getRegistrationFileKey();
          case "OWNERSHIP" -> application.getOwnershipFileKey();
          case "INSPECTION" -> application.getInspectionFileKey();
          default ->
              throw new BusinessException(
                  HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Unknown evidence type");
        };
    if (!DroneEvidenceUploadService.isEvidenceKey(fileKey)) {
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
  public List<DroneRegistrationApplicationResponse> mine(UUID inspectorId) {
    requireInspector(inspectorId);
    return applications.findAllByInspectorUserIdOrderByCreatedAtDesc(inspectorId).stream()
        .map(DroneRegistrationApplicationResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<com.smartdroneinspection.assets.api.dto.response.InspectorDroneResponse>
      inspectorDrones(UUID inspectorId) {
    requireInspector(inspectorId);
    return drones.findAllByInspectorOwnerId(inspectorId).stream()
        .map(com.smartdroneinspection.assets.api.dto.response.InspectorDroneResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<DroneRegistrationApplicationResponse> pending(UUID adminId) {
    requireAdmin(adminId);
    return applications.findAllByStatusOrderByCreatedAtAsc("PENDING").stream()
        .map(DroneRegistrationApplicationResponse::from)
        .toList();
  }

  @Transactional
  public DroneRegistrationApplicationResponse approve(
      UUID adminId, UUID applicationId, String ip, String agent, String correlationId) {
    requireAdmin(adminId);
    DroneRegistrationApplication application = requirePending(applicationId);
    requireInspector(application.getInspectorUserId());
    if (!application.getRegistrationExpiresAt().isAfter(Instant.now())) {
      throw new AuthException(
          HttpStatus.CONFLICT,
          "DRONE_REGISTRATION_EXPIRED",
          "Drone registration expired before approval.");
    }
    if (drones.existsBySerialNumberIgnoreCase(application.getSerialNumber())) {
      throw new AuthException(
          HttpStatus.CONFLICT, "DUPLICATE_SERIAL_NUMBER", "Drone serial is already registered.");
    }
    Drone drone =
        drones.saveAndFlush(
            Drone.createInspectorOwned(
                application.getInspectorUserId(),
                application.getSerialNumber(),
                application.getModel(),
                application.getManufacturer(),
                application.getRegistrationNumber(),
                application.getRegistrationFileKey(),
                application.getRegistrationExpiresAt()));
    application.approve(adminId, drone.getId());
    applications.saveAndFlush(application);
    userAccess.recordSecurityAudit(
        adminId,
        application.getId(),
        "DRONE_REGISTRATION_APPROVED",
        "SUCCESS",
        ip,
        agent,
        correlationId);
    return DroneRegistrationApplicationResponse.from(application);
  }

  @Transactional
  public DroneRegistrationApplicationResponse reject(
      UUID adminId,
      UUID applicationId,
      ReviewDroneRegistrationApplicationRequest request,
      String ip,
      String agent,
      String correlationId) {
    requireAdmin(adminId);
    DroneRegistrationApplication application = requirePending(applicationId);
    application.reject(adminId, request.reviewNote().trim());
    applications.saveAndFlush(application);
    userAccess.recordSecurityAudit(
        adminId,
        application.getId(),
        "DRONE_REGISTRATION_REJECTED",
        "SUCCESS",
        ip,
        agent,
        correlationId);
    return DroneRegistrationApplicationResponse.from(application);
  }

  public record EvidenceContent(String contentType, InputStream stream) {}

  private AuthException notFound() {
    return new AuthException(
        HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Pending application was not found.");
  }

  private DroneRegistrationApplication requirePending(UUID id) {
    return applications.findByIdAndStatus(id, "PENDING").orElseThrow(this::notFound);
  }

  private UserAccess.ActiveUser requireInspector(UUID id) {
    return userAccess
        .findActiveUser(id)
        .filter(user -> "INSPECTOR_PARTNER".equals(user.actorZone()))
        .filter(user -> user.hasRole(Roles.INSPECTOR))
        .filter(user -> user.pilotLicenseExpiresAt() != null)
        .filter(user -> user.pilotLicenseExpiresAt().isAfter(Instant.now()))
        .orElseThrow(
            () ->
                new AuthException(
                    HttpStatus.FORBIDDEN,
                    "INSPECTOR_PARTNER_REQUIRED",
                    "An active Inspector partner is required."));
  }

  private void requireAdmin(UUID id) {
    UserAccess.ActiveUser admin = userAccess.findActiveUser(id).orElseThrow(() -> notFound());
    if (!"PLATFORM".equals(admin.actorZone()) || !admin.hasRole(Roles.ADMIN)) {
      throw new AuthException(
          HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Platform Admin access is required.");
    }
  }
}

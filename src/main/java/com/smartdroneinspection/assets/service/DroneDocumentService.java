package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.UpdateDroneDocumentStatusRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneDocumentResponse;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.DroneDocument;
import com.smartdroneinspection.assets.domain.enums.DroneDocumentStatus;
import com.smartdroneinspection.assets.repository.DroneDocumentRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.subscriptions.SubscriptionEntitlement;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Drone document upload and review lifecycle, scoped to the owning organization. */
@Service
public class DroneDocumentService {

  static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
  private static final Set<String> ALLOWED_TYPES =
      Set.of("image/png", "image/jpeg", "image/webp", "application/pdf");
  private static final String NOT_FOUND_MESSAGE = "Drone not found.";

  private final DroneRepository drones;
  private final DroneDocumentRepository droneDocuments;
  private final UserAccess userAccess;
  private final SubscriptionEntitlement entitlement;
  private final Optional<EvidenceObjectStore> objectStore;

  public DroneDocumentService(
      DroneRepository drones,
      DroneDocumentRepository droneDocuments,
      UserAccess userAccess,
      SubscriptionEntitlement entitlement,
      Optional<EvidenceObjectStore> objectStore) {
    this.drones = drones;
    this.droneDocuments = droneDocuments;
    this.userAccess = userAccess;
    this.entitlement = entitlement;
    this.objectStore = objectStore;
  }

  /**
   * Stores the uploaded file and records its metadata.
   *
   * <p>The recorded checksum is computed here, server-side, over the exact bytes handed to storage.
   * A checksum supplied by the caller is never read or trusted, so the stored value is always a
   * fact about the object that exists in the bucket rather than a claim about it.
   */
  @Transactional
  public DroneDocumentResponse upload(
      UUID actorId,
      UUID droneId,
      MultipartFile file,
      String documentType,
      String issuer,
      String documentReference,
      Instant validFrom,
      Instant validUntil) {
    Drone drone = requireOwnedDrone(actorId, droneId);
    entitlement.requireActive(drone.getOrganizationId(), Instant.now());

    String contentType = file.getContentType() == null ? "" : file.getContentType();
    if (!ALLOWED_TYPES.contains(contentType)) {
      throw new BusinessException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "UNSUPPORTED_MEDIA_TYPE",
          "Allowed types: png, jpeg, webp, pdf");
    }
    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException ex) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Unreadable file");
    }
    if (content.length == 0) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Document must not be empty");
    }
    if (content.length > MAX_SIZE_BYTES) {
      throw new BusinessException(
          HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Maximum document size is 10 MB");
    }
    // Reject an impossible validity window before writing anything to storage.
    if (validFrom != null && validUntil != null && !validUntil.isAfter(validFrom)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "Document validity must end after it starts");
    }

    String checksum = sha256(content);
    String objectKey = "drones/" + droneId + "/documents/" + UUID.randomUUID();
    try {
      objectStore
          .orElseThrow(
              () ->
                  new BusinessException(
                      HttpStatus.BAD_GATEWAY,
                      "STORAGE_UNAVAILABLE",
                      "Object storage is not configured"))
          .put(objectKey, contentType, content.length, new ByteArrayInputStream(content));
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE", "Object storage is unavailable");
    }

    DroneDocument document =
        new DroneDocument(
            drone,
            documentType,
            issuer,
            documentReference,
            validFrom,
            validUntil,
            DroneDocumentStatus.PENDING_REVIEW,
            objectKey,
            checksum,
            actorId);
    return DroneDocumentResponse.from(droneDocuments.saveAndFlush(document));
  }

  @Transactional(readOnly = true)
  public List<DroneDocumentResponse> list(UUID actorId, UUID droneId) {
    Drone drone = requireOwnedDrone(actorId, droneId);
    Instant now = Instant.now();
    return droneDocuments.findAllByDrone_IdOrderByCreatedAtDesc(drone.getId()).stream()
        .map(document -> DroneDocumentResponse.from(document, now))
        .toList();
  }

  @Transactional
  public DroneDocumentResponse updateStatus(
      UUID actorId, UUID droneId, UUID documentId, UpdateDroneDocumentStatusRequest request) {
    Drone drone = requireOwnedDrone(actorId, droneId);
    entitlement.requireActive(drone.getOrganizationId(), Instant.now());

    DroneDocument document =
        droneDocuments.findAllByDrone_IdOrderByCreatedAtDesc(drone.getId()).stream()
            .filter(candidate -> candidate.getId().equals(documentId))
            .findFirst()
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "DRONE_DOCUMENT_NOT_FOUND", "Document not found."));
    if (document.getStatus() == DroneDocumentStatus.REVOKED) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_STATE", "Revoked documents cannot change status");
    }
    document.changeStatus(request.status(), actorId, Instant.now());
    return DroneDocumentResponse.from(droneDocuments.saveAndFlush(document));
  }

  private Drone requireOwnedDrone(UUID actorId, UUID droneId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));
    if (actor.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Actor has no organization scope");
    }
    return drones
        .findByIdAndOrganizationId(droneId, actor.organizationId())
        .orElseThrow(
            () ->
                new BusinessException(HttpStatus.NOT_FOUND, "DRONE_NOT_FOUND", NOT_FOUND_MESSAGE));
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}

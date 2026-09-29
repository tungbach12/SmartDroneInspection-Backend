package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.response.AssetDocumentResponse;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetDocument;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetDocumentService {

  static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
  private static final Set<String> ALLOWED_TYPES =
      Set.of("image/png", "image/jpeg", "image/webp", "application/pdf");

  private final AssetRepository assets;
  private final Optional<EvidenceObjectStore> objectStore;
  private final UserAccess userAccess;

  public AssetDocumentService(
      AssetRepository assets, Optional<EvidenceObjectStore> objectStore, UserAccess userAccess) {
    this.assets = assets;
    this.objectStore = objectStore;
    this.userAccess = userAccess;
  }

  @Transactional
  public AssetDocumentResponse upload(
      UUID actorId, UUID assetId, MultipartFile file, String documentType, LocalDate documentDate) {
    Asset asset = requireOwnedAsset(actorId, assetId);
    if (asset.getStatus() != AssetStatus.ACTIVE && asset.getStatus() != AssetStatus.INACTIVE) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INVALID_STATE", "Documents can only be added to active assets");
    }
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
    if (content.length > MAX_SIZE_BYTES) {
      throw new BusinessException(
          HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Maximum document size is 10 MB");
    }

    String objectKey = "assets/" + assetId + "/documents/" + UUID.randomUUID();
    EvidenceObjectStore store = requireObjectStore();
    try {
      store.put(objectKey, contentType, content.length, new ByteArrayInputStream(content));
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE", "Object storage is unavailable");
    }

    AssetDocument document =
        asset.addDocument(
            actorId,
            documentType,
            sanitizeFileName(file.getOriginalFilename()),
            contentType,
            content.length,
            sha256(content),
            objectKey,
            documentDate);
    assets.saveAndFlush(asset);
    return toResponse(document);
  }

  @Transactional(readOnly = true)
  public List<AssetDocumentResponse> list(UUID actorId, UUID assetId) {
    Asset asset = requireOwnedAsset(actorId, assetId);
    return asset.getDocuments().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public DocumentContent open(UUID actorId, UUID assetId, UUID documentId) {
    Asset asset = requireOwnedAsset(actorId, assetId);
    AssetDocument document =
        asset.getDocuments().stream()
            .filter(d -> d.getId().equals(documentId))
            .findFirst()
            .orElseThrow(
                () ->
                    new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document not found"));
    try {
      return new DocumentContent(
          document.getContentType(),
          document.getFileName(),
          requireObjectStore().open(document.getObjectKey()));
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE", "Object storage is unavailable");
    }
  }

  private EvidenceObjectStore requireObjectStore() {
    return objectStore.orElseThrow(
        () ->
            new BusinessException(
                HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE", "Object storage is not configured"));
  }

  private Asset requireOwnedAsset(UUID actorId, UUID assetId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));
    if (actor.organizationId() == null) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No organization scope");
    }
    return assets
        .findDetailedByIdAndOrganizationId(assetId, actor.organizationId())
        .orElseThrow(
            () -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Asset not found"));
  }

  private static String sanitizeFileName(String name) {
    if (name == null || name.isBlank()) {
      return "document";
    }
    String cleaned = name.replaceAll("[\\\\/\\r\\n]", "_");
    return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private AssetDocumentResponse toResponse(AssetDocument document) {
    return new AssetDocumentResponse(
        document.getId(),
        document.getDocumentType(),
        document.getFileName(),
        document.getContentType(),
        document.getSizeBytes(),
        document.getChecksumSha256(),
        document.getDocumentDate(),
        document.getCreatedAt());
  }

  public record DocumentContent(String contentType, String fileName, InputStream stream) {}
}

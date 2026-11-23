package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.inspections.api.dto.response.InspectionChatThreadResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionChatThreadResponse.InspectionMessageItem;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionMessage;
import com.smartdroneinspection.inspections.domain.InspectionMessageAttachment;
import com.smartdroneinspection.inspections.domain.enums.AssignmentStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.InspectionMessageAttachmentRepository;
import com.smartdroneinspection.inspections.repository.InspectionMessageRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.ChatAttachmentValidator;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** Manages dedicated Customer <-> Inspector messaging threads per assigned inspection. */
@Service
public class InspectionChatService {

  private final InspectionRepository inspections;
  private final InspectionMessageRepository messages;
  private final InspectionMessageAttachmentRepository attachments;
  private final AssetRepository assets;
  private final UserAccess users;
  private final Optional<EvidenceObjectStore> objectStore;

  @Autowired
  public InspectionChatService(
      InspectionRepository inspections,
      InspectionMessageRepository messages,
      InspectionMessageAttachmentRepository attachments,
      AssetRepository assets,
      UserAccess users,
      Optional<EvidenceObjectStore> objectStore) {
    this.inspections = inspections;
    this.messages = messages;
    this.attachments = attachments;
    this.assets = assets;
    this.users = users;
    this.objectStore = objectStore != null ? objectStore : Optional.empty();
  }

  public record AttachmentFile(
      String fileName, String contentType, long sizeBytes, InputStream stream) {}

  @Transactional(readOnly = true)
  public List<InspectionChatThreadResponse> listThreads(UUID actorId) {
    UserAccess.ActiveUser actor = requireChatActor(actorId);
    List<Inspection> scoped =
        isInspectorActor(actor)
            ? inspections.findByInspectorIdOrderByCreatedAtDesc(actor.id())
            : inspections.findByOwnerCustomerIdAndInspectorIdIsNotNullOrderByCreatedAtDesc(
                actor.ownerCustomerId());
    return scoped.stream()
        .filter(InspectionChatService::isChatEligible)
        .map(inspection -> toThreadResponse(inspection, actor))
        .toList();
  }

  @Transactional(readOnly = true)
  public InspectionChatThreadResponse getThread(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor = requireChatActor(actorId);
    Inspection inspection = requireAuthorizedInspection(actor, inspectionId, false);
    return toThreadResponse(inspection, actor);
  }

  @Transactional
  public InspectionChatThreadResponse postMessage(
      UUID actorId, UUID inspectionId, String body, List<MultipartFile> files) {
    UserAccess.ActiveUser actor = requireChatActor(actorId);
    Inspection inspection = requireAuthorizedInspection(actor, inspectionId, true);
    if (!isChatEligible(inspection)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INSPECTION_CHAT_UNAVAILABLE",
          "Inspector chat is only available for active assigned inspections.");
    }
    List<ChatAttachmentValidator.ValidatedAttachment> uploads =
        ChatAttachmentValidator.validate(files);
    if (!StringUtils.hasText(body) && uploads.isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "MESSAGE_INVALID", "Message text or an attachment is required");
    }

    InspectionMessage message;
    try {
      message = new InspectionMessage(inspection.getId(), actor.id(), body, !uploads.isEmpty());
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "MESSAGE_INVALID", exception.getMessage());
    }

    EvidenceObjectStore store = uploads.isEmpty() ? null : requireStore();
    List<String> storedKeys = new ArrayList<>();
    try {
      for (ChatAttachmentValidator.ValidatedAttachment upload : uploads) {
        String objectKey =
            "inspections/"
                + inspectionId
                + "/messages/"
                + UUID.randomUUID()
                + "/"
                + upload.fileName();
        store.put(
            objectKey,
            upload.contentType(),
            upload.content().length,
            new ByteArrayInputStream(upload.content()));
        storedKeys.add(objectKey);
        message.addAttachment(
            upload.fileName(),
            upload.contentType(),
            upload.content().length,
            upload.sha256(),
            objectKey);
      }
      messages.saveAndFlush(message);
      if (!message.getAttachments().isEmpty()) {
        attachments.saveAllAndFlush(message.getAttachments());
      }
    } catch (IOException exception) {
      deleteStoredObjects(store, storedKeys);
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "STORAGE_FAILED", "Failed to store message attachment");
    } catch (RuntimeException exception) {
      deleteStoredObjects(store, storedKeys);
      throw exception;
    }

    return toThreadResponse(inspection, actor);
  }

  @Transactional
  public InspectionChatThreadResponse markMessagesRead(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor = requireChatActor(actorId);
    Inspection inspection = requireAuthorizedInspection(actor, inspectionId, true);
    List<InspectionMessage> unread =
        messages.findByInspectionIdAndAuthorUserIdNotAndReadAtIsNull(
            inspection.getId(), actor.id());
    Instant now = Instant.now();
    for (InspectionMessage item : unread) {
      item.markRead(now);
    }
    if (!unread.isEmpty()) {
      messages.saveAllAndFlush(unread);
    }
    return toThreadResponse(inspection, actor);
  }

  @Transactional(readOnly = true)
  public AttachmentFile openMessageAttachment(
      UUID actorId, UUID inspectionId, UUID messageId, UUID attachmentId) {
    UserAccess.ActiveUser actor = requireChatActor(actorId);
    requireAuthorizedInspection(actor, inspectionId, false);
    InspectionMessageAttachment attachment =
        attachments
            .findByIdAndMessage_IdAndMessage_InspectionId(attachmentId, messageId, inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "MESSAGE_ATTACHMENT_NOT_FOUND",
                        "Attachment not found"));
    EvidenceObjectStore store = requireStore();
    try {
      return new AttachmentFile(
          attachment.getFileName(),
          attachment.getContentType(),
          attachment.getSizeBytes(),
          store.open(attachment.getObjectKey()));
    } catch (IOException exception) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "STORAGE_FAILED", "Failed to open message attachment");
    }
  }

  private UserAccess.ActiveUser requireChatActor(UUID actorId) {
    UserAccess.ActiveUser actor =
        users
            .findActiveUser(actorId)
            .orElseThrow(
                () -> new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User not active"));
    if (!isInspectorActor(actor) && !isCustomerActor(actor)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only Customers and assigned Inspectors may access inspection chat.");
    }
    return actor;
  }

  private Inspection requireAuthorizedInspection(
      UserAccess.ActiveUser actor, UUID inspectionId, boolean lock) {
    Inspection inspection =
        (lock ? inspections.findWithLockById(inspectionId) : inspections.findById(inspectionId))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection not found"));
    if (isInspectorActor(actor) && actor.id().equals(inspection.getInspectorId())) {
      return inspection;
    }
    if (isCustomerActor(actor)
        && actor.ownerCustomerId() != null
        && actor.ownerCustomerId().equals(inspection.getOwnerCustomerId())
        && inspection.getInspectorId() != null) {
      return inspection;
    }
    throw new BusinessException(
        HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this inspection chat.");
  }

  private static boolean isInspectorActor(UserAccess.ActiveUser actor) {
    return actor.hasRole(Roles.INSPECTOR);
  }

  private static boolean isCustomerActor(UserAccess.ActiveUser actor) {
    return actor.hasRole(Roles.CUSTOMER);
  }

  private static boolean isChatEligible(Inspection inspection) {
    return inspection.getInspectorId() != null
        && inspection.getStatus() != InspectionStatus.CANCELLED
        && inspection.getAssignmentStatus() != AssignmentStatus.DECLINED;
  }

  private InspectionChatThreadResponse toThreadResponse(
      Inspection inspection, UserAccess.ActiveUser viewer) {
    String assetName = assets.findById(inspection.getAssetId()).map(Asset::getName).orElse("Asset");
    boolean viewerIsInspector = isInspectorActor(viewer);
    UUID counterpartId =
        viewerIsInspector ? inspection.getOwnerCustomerId() : inspection.getInspectorId();
    Optional<UserAccess.ActiveUser> counterpart =
        counterpartId == null ? Optional.empty() : users.findUser(counterpartId);
    String counterpartName =
        counterpart
            .map(UserAccess.ActiveUser::fullName)
            .filter(StringUtils::hasText)
            .orElse(viewerIsInspector ? "Customer" : "Inspector");
    String counterpartRole = viewerIsInspector ? Roles.CUSTOMER : Roles.INSPECTOR;
    String counterpartAvatar = counterpart.map(UserAccess.ActiveUser::avatarUrl).orElse(null);

    List<InspectionMessageItem> threadMessages =
        messages.findByInspectionIdOrderByCreatedAtAscIdAsc(inspection.getId()).stream()
            .map(this::toMessageItem)
            .toList();

    return InspectionChatThreadResponse.from(
        inspection,
        assetName,
        counterpartId,
        counterpartName,
        counterpartRole,
        counterpartAvatar,
        threadMessages);
  }

  private InspectionMessageItem toMessageItem(InspectionMessage message) {
    Optional<UserAccess.ActiveUser> author = users.findUser(message.getAuthorUserId());
    String authorName = author.map(UserAccess.ActiveUser::fullName).orElse(null);
    String authorRole = author.map(u -> u.roles().stream().findFirst().orElse(null)).orElse(null);
    return InspectionMessageItem.from(message, authorName, authorRole);
  }

  private EvidenceObjectStore requireStore() {
    return objectStore.orElseThrow(
        () ->
            new BusinessException(
                HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE", "Object storage is not configured"));
  }

  private static void deleteStoredObjects(EvidenceObjectStore store, List<String> keys) {
    if (store == null) return;
    for (String key : keys) {
      try {
        store.delete(key);
      } catch (IOException ignored) {
        // Ignore cleanup failures after transaction abort
      }
    }
  }
}

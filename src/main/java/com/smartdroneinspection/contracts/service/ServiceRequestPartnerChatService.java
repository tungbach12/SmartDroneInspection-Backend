package com.smartdroneinspection.contracts.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.contracts.api.dto.response.PartnerChatThreadResponse;
import com.smartdroneinspection.contracts.api.dto.response.PartnerChatThreadResponse.PartnerChatMessageItem;
import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequest.ServiceRequestStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrenceStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestPartnerMessage;
import com.smartdroneinspection.contracts.domain.ServiceRequestPartnerMessageAttachment;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestPartnerMessageAttachmentRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestPartnerMessageRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
import com.smartdroneinspection.inspections.spi.Mf2InspectionAssignment;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Manages private Manager <-> assigned Inspector chat threads scoped to a customer service request
 * where the Manager manages the request and the Inspector is assigned to at least one active
 * occurrence of that request.
 */
@Service
public class ServiceRequestPartnerChatService {

  private final ServiceRequestRepository requests;
  private final ServiceRequestOccurrenceRepository occurrences;
  private final ServiceRequestPartnerMessageRepository messages;
  private final ServiceRequestPartnerMessageAttachmentRepository attachments;
  private final AssetRepository assets;
  private final UserAccess users;
  private final Mf2ActorPolicy actorPolicy;
  private final Mf2InspectionAssignment assignment;
  private final Optional<EvidenceObjectStore> objectStore;

  @Autowired
  public ServiceRequestPartnerChatService(
      ServiceRequestRepository requests,
      ServiceRequestOccurrenceRepository occurrences,
      ServiceRequestPartnerMessageRepository messages,
      ServiceRequestPartnerMessageAttachmentRepository attachments,
      AssetRepository assets,
      UserAccess users,
      Mf2ActorPolicy actorPolicy,
      Mf2InspectionAssignment assignment,
      Optional<EvidenceObjectStore> objectStore) {
    this.requests = requests;
    this.occurrences = occurrences;
    this.messages = messages;
    this.attachments = attachments;
    this.assets = assets;
    this.users = users;
    this.actorPolicy = actorPolicy;
    this.assignment = assignment;
    this.objectStore = objectStore != null ? objectStore : Optional.empty();
  }

  public record AttachmentFile(
      String fileName, String contentType, long sizeBytes, InputStream stream) {}

  @Transactional(readOnly = true)
  public List<PartnerChatThreadResponse> listThreads(UUID actorId) {
    UserAccess.ActiveUser actor = requirePartnerChatActor(actorId);
    Mf2ActorPolicy.ActorKind kind = actorPolicy.actorKind(actor.roles());
    if (kind == Mf2ActorPolicy.ActorKind.MANAGER) {
      return listManagerThreads(actor);
    }
    return listInspectorThreads(actor);
  }

  @Transactional(readOnly = true)
  public PartnerChatThreadResponse getThread(UUID actorId, UUID requestId, UUID inspectorId) {
    UserAccess.ActiveUser actor = requirePartnerChatActor(actorId);
    AuthorizedThread thread = requireAuthorizedThread(actor, requestId, inspectorId, false);
    return toThreadResponse(thread.request(), inspectorId, thread.assignedOccurrences(), actor);
  }

  @Transactional
  public PartnerChatThreadResponse postMessage(
      UUID actorId, UUID requestId, UUID inspectorId, String body, List<MultipartFile> files) {
    UserAccess.ActiveUser actor = requirePartnerChatActor(actorId);
    AuthorizedThread thread = requireAuthorizedThread(actor, requestId, inspectorId, true);
    List<ChatAttachmentValidator.ValidatedAttachment> uploads =
        ChatAttachmentValidator.validate(files);
    if (!StringUtils.hasText(body) && uploads.isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "MESSAGE_INVALID", "Message text or an attachment is required");
    }

    ServiceRequestPartnerMessage message;
    try {
      message =
          new ServiceRequestPartnerMessage(
              thread.request().getId(), inspectorId, actor.id(), body, !uploads.isEmpty());
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "MESSAGE_INVALID", exception.getMessage());
    }

    EvidenceObjectStore store = uploads.isEmpty() ? null : requireStore();
    List<String> storedKeys = new ArrayList<>();
    try {
      for (ChatAttachmentValidator.ValidatedAttachment upload : uploads) {
        String objectKey =
            "contracts/"
                + requestId
                + "/partner-messages/"
                + inspectorId
                + "/"
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

    return toThreadResponse(thread.request(), inspectorId, thread.assignedOccurrences(), actor);
  }

  @Transactional
  public PartnerChatThreadResponse markMessagesRead(
      UUID actorId, UUID requestId, UUID inspectorId) {
    UserAccess.ActiveUser actor = requirePartnerChatActor(actorId);
    AuthorizedThread thread = requireAuthorizedThread(actor, requestId, inspectorId, true);
    List<ServiceRequestPartnerMessage> unread =
        messages.findByRequestIdAndInspectorIdAndAuthorUserIdNotAndReadAtIsNull(
            thread.request().getId(), inspectorId, actor.id());
    Instant now = Instant.now();
    for (ServiceRequestPartnerMessage item : unread) {
      item.markRead(now);
    }
    if (!unread.isEmpty()) {
      messages.saveAllAndFlush(unread);
    }
    return toThreadResponse(thread.request(), inspectorId, thread.assignedOccurrences(), actor);
  }

  @Transactional(readOnly = true)
  public AttachmentFile openMessageAttachment(
      UUID actorId, UUID requestId, UUID inspectorId, UUID messageId, UUID attachmentId) {
    UserAccess.ActiveUser actor = requirePartnerChatActor(actorId);
    requireAuthorizedThread(actor, requestId, inspectorId, false);
    ServiceRequestPartnerMessageAttachment attachment =
        attachments
            .findByIdAndMessage_IdAndMessage_RequestIdAndMessage_InspectorId(
                attachmentId, messageId, requestId, inspectorId)
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

  private List<PartnerChatThreadResponse> listManagerThreads(UserAccess.ActiveUser manager) {
    List<ServiceRequest> managedRequests =
        requests.findByAssignedManagerIdAndStatusNotOrderByCreatedAtDesc(
            manager.id(), ServiceRequestStatus.CANCELLED);
    List<PartnerChatThreadResponse> threads = new ArrayList<>();
    for (ServiceRequest request : managedRequests) {
      Map<UUID, List<ServiceRequestOccurrence>> byInspector = new LinkedHashMap<>();
      for (ServiceRequestOccurrence occ :
          occurrences.findByRequestIdOrderByOccurrenceIndexAsc(request.getId())) {
        if (isEligibleOccurrence(occ) && occ.getInspectorId() != null) {
          byInspector.computeIfAbsent(occ.getInspectorId(), ignored -> new ArrayList<>()).add(occ);
        }
      }
      for (Map.Entry<UUID, List<ServiceRequestOccurrence>> entry : byInspector.entrySet()) {
        threads.add(toThreadResponse(request, entry.getKey(), entry.getValue(), manager));
      }
    }
    return threads;
  }

  private List<PartnerChatThreadResponse> listInspectorThreads(UserAccess.ActiveUser inspector) {
    List<ServiceRequestOccurrence> assignedOccurrences =
        occurrences.findByInspectorIdAndStatusOrderByPlannedStartAtAsc(
            inspector.id(), OccurrenceStatus.ASSIGNED);
    Map<UUID, List<ServiceRequestOccurrence>> byRequest = new LinkedHashMap<>();
    for (ServiceRequestOccurrence occ : assignedOccurrences) {
      if (isEligibleOccurrence(occ)) {
        byRequest.computeIfAbsent(occ.getRequestId(), ignored -> new ArrayList<>()).add(occ);
      }
    }
    if (byRequest.isEmpty()) {
      return List.of();
    }
    Map<UUID, ServiceRequest> requestsById = new HashMap<>();
    for (ServiceRequest req : requests.findAllById(byRequest.keySet())) {
      requestsById.put(req.getId(), req);
    }
    List<PartnerChatThreadResponse> threads = new ArrayList<>();
    for (Map.Entry<UUID, List<ServiceRequestOccurrence>> entry : byRequest.entrySet()) {
      ServiceRequest request = requestsById.get(entry.getKey());
      if (request != null
          && request.getStatus() != ServiceRequestStatus.CANCELLED
          && request.getAssignedManagerId() != null) {
        threads.add(toThreadResponse(request, inspector.id(), entry.getValue(), inspector));
      }
    }
    return threads;
  }

  private UserAccess.ActiveUser requirePartnerChatActor(UUID actorId) {
    UserAccess.ActiveUser actor =
        users
            .findActiveUser(actorId)
            .orElseThrow(
                () -> new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User not active"));
    Mf2ActorPolicy.ActorKind kind = actorPolicy.actorKind(actor.roles());
    if (kind != Mf2ActorPolicy.ActorKind.MANAGER && kind != Mf2ActorPolicy.ActorKind.INSPECTOR) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only assigned Service Managers and Inspectors may access partner chat.");
    }
    return actor;
  }

  private AuthorizedThread requireAuthorizedThread(
      UserAccess.ActiveUser actor, UUID requestId, UUID inspectorId, boolean lock) {
    ServiceRequest request =
        (lock ? requests.findWithLockById(requestId) : requests.findById(requestId))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "Service request not found"));
    if (request.getStatus() == ServiceRequestStatus.CANCELLED
        || request.getAssignedManagerId() == null) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PARTNER_CHAT_UNAVAILABLE",
          "Manager and Inspector chat is available only for active managed requests.");
    }

    Mf2ActorPolicy.ActorKind kind = actorPolicy.actorKind(actor.roles());
    if (kind == Mf2ActorPolicy.ActorKind.MANAGER
        && !actor.id().equals(request.getAssignedManagerId())) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not manage this service request.");
    }
    if (kind == Mf2ActorPolicy.ActorKind.INSPECTOR && !actor.id().equals(inspectorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this partner chat thread.");
    }

    List<ServiceRequestOccurrence> eligibleOccurrences =
        occurrences
            .findByRequestIdAndInspectorIdAndStatusOrderByOccurrenceIndexAsc(
                request.getId(), inspectorId, OccurrenceStatus.ASSIGNED)
            .stream()
            .filter(this::isEligibleOccurrence)
            .toList();
    if (eligibleOccurrences.isEmpty()) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Inspector must be actively assigned to an occurrence of this service request.");
    }

    return new AuthorizedThread(request, eligibleOccurrences);
  }

  private boolean isEligibleOccurrence(ServiceRequestOccurrence occurrence) {
    if (occurrence == null
        || occurrence.getStatus() != OccurrenceStatus.ASSIGNED
        || occurrence.getInspectorId() == null) {
      return false;
    }
    if (occurrence.getInspectionId() == null || assignment == null) {
      return true;
    }
    return assignment
        .findAssignmentState(occurrence.getInspectionId())
        .map(state -> !"DECLINED".equalsIgnoreCase(state.assignmentStatus()))
        .orElse(true);
  }

  private PartnerChatThreadResponse toThreadResponse(
      ServiceRequest request,
      UUID inspectorId,
      List<ServiceRequestOccurrence> assignedOccurrences,
      UserAccess.ActiveUser viewer) {
    Asset asset =
        assets == null || request.getAssetId() == null
            ? null
            : assets.findById(request.getAssetId()).orElse(null);
    String assetName = asset != null ? asset.getName() : "Asset";
    String assetCode = asset != null ? asset.getCode() : null;
    String customerName =
        request.getOwnerCustomerId() == null
            ? null
            : users
                .findUser(request.getOwnerCustomerId())
                .map(UserAccess.ActiveUser::fullName)
                .filter(StringUtils::hasText)
                .orElse(null);

    boolean viewerIsInspector =
        actorPolicy.actorKind(viewer.roles()) == Mf2ActorPolicy.ActorKind.INSPECTOR;
    UUID counterpartId = viewerIsInspector ? request.getAssignedManagerId() : inspectorId;
    Optional<UserAccess.ActiveUser> counterpart =
        counterpartId == null ? Optional.empty() : users.findUser(counterpartId);
    String counterpartName =
        counterpart
            .map(UserAccess.ActiveUser::fullName)
            .filter(StringUtils::hasText)
            .orElse(viewerIsInspector ? "Service Manager" : "Inspector");
    String counterpartRole = viewerIsInspector ? Roles.MANAGER : Roles.INSPECTOR;
    String counterpartAvatar = counterpart.map(UserAccess.ActiveUser::avatarUrl).orElse(null);

    ServiceRequestOccurrence nextOccurrence =
        assignedOccurrences.stream()
            .min(Comparator.comparing(ServiceRequestOccurrence::getPlannedStartAt))
            .orElse(null);

    List<PartnerChatMessageItem> threadMessages =
        messages
            .findByRequestIdAndInspectorIdOrderByCreatedAtAscIdAsc(request.getId(), inspectorId)
            .stream()
            .map(this::toMessageItem)
            .toList();

    return PartnerChatThreadResponse.from(
        request,
        inspectorId,
        customerName,
        assetName,
        assetCode,
        counterpartId,
        counterpartName,
        counterpartRole,
        counterpartAvatar,
        assignedOccurrences.size(),
        nextOccurrence != null ? nextOccurrence.getPlannedStartAt() : null,
        nextOccurrence != null ? nextOccurrence.getPlannedEndAt() : null,
        threadMessages);
  }

  private PartnerChatMessageItem toMessageItem(ServiceRequestPartnerMessage message) {
    Optional<UserAccess.ActiveUser> author = users.findUser(message.getAuthorUserId());
    String authorName = author.map(UserAccess.ActiveUser::fullName).orElse(null);
    String authorRole = author.map(u -> u.roles().stream().findFirst().orElse(null)).orElse(null);
    return PartnerChatMessageItem.from(message, authorName, authorRole);
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

  private record AuthorizedThread(
      ServiceRequest request, List<ServiceRequestOccurrence> assignedOccurrences) {}
}

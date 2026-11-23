package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequestPartnerMessage;
import com.smartdroneinspection.contracts.domain.ServiceRequestPartnerMessageAttachment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PartnerChatThreadResponse(
    UUID requestId,
    UUID inspectorId,
    UUID managerId,
    UUID ownerCustomerId,
    String customerName,
    UUID assetId,
    String assetName,
    String assetCode,
    String requestDescription,
    String requestStatus,
    UUID counterpartUserId,
    String counterpartName,
    String counterpartRole,
    String counterpartAvatarUrl,
    int occurrenceCount,
    Instant nextPlannedStartAt,
    Instant nextPlannedEndAt,
    List<PartnerChatMessageItem> messages) {

  public record PartnerChatMessageAttachmentItem(
      UUID id,
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      Instant createdAt) {

    public static PartnerChatMessageAttachmentItem from(
        ServiceRequestPartnerMessageAttachment attachment) {
      return new PartnerChatMessageAttachmentItem(
          attachment.getId(),
          attachment.getFileName(),
          attachment.getContentType(),
          attachment.getSizeBytes(),
          attachment.getChecksumSha256(),
          attachment.getCreatedAt());
    }
  }

  public record PartnerChatMessageItem(
      UUID id,
      UUID requestId,
      UUID inspectorId,
      UUID authorUserId,
      String authorName,
      String authorRole,
      String body,
      Instant readAt,
      Instant createdAt,
      List<PartnerChatMessageAttachmentItem> attachments) {

    public static PartnerChatMessageItem from(
        ServiceRequestPartnerMessage message, String authorName, String authorRole) {
      return new PartnerChatMessageItem(
          message.getId(),
          message.getRequestId(),
          message.getInspectorId(),
          message.getAuthorUserId(),
          authorName,
          authorRole,
          message.getBody(),
          message.getReadAt(),
          message.getCreatedAt(),
          message.getAttachments().stream().map(PartnerChatMessageAttachmentItem::from).toList());
    }
  }

  public static PartnerChatThreadResponse from(
      ServiceRequest request,
      UUID inspectorId,
      String customerName,
      String assetName,
      String assetCode,
      UUID counterpartUserId,
      String counterpartName,
      String counterpartRole,
      String counterpartAvatarUrl,
      int occurrenceCount,
      Instant nextPlannedStartAt,
      Instant nextPlannedEndAt,
      List<PartnerChatMessageItem> messages) {
    return new PartnerChatThreadResponse(
        request.getId(),
        inspectorId,
        request.getAssignedManagerId(),
        request.getOwnerCustomerId(),
        customerName,
        request.getAssetId(),
        assetName,
        assetCode,
        request.getDescription(),
        request.getStatus().name(),
        counterpartUserId,
        counterpartName,
        counterpartRole,
        counterpartAvatarUrl,
        occurrenceCount,
        nextPlannedStartAt,
        nextPlannedEndAt,
        messages);
  }
}

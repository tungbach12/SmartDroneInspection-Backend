package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionMessage;
import com.smartdroneinspection.inspections.domain.InspectionMessageAttachment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionChatThreadResponse(
    UUID inspectionId,
    UUID assetId,
    String assetName,
    String status,
    String assignmentStatus,
    Instant plannedStartAt,
    Instant plannedEndAt,
    UUID counterpartUserId,
    String counterpartName,
    String counterpartRole,
    String counterpartAvatarUrl,
    List<InspectionMessageItem> messages) {

  public record InspectionMessageAttachmentItem(
      UUID id,
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      Instant createdAt) {

    public static InspectionMessageAttachmentItem from(InspectionMessageAttachment attachment) {
      return new InspectionMessageAttachmentItem(
          attachment.getId(),
          attachment.getFileName(),
          attachment.getContentType(),
          attachment.getSizeBytes(),
          attachment.getChecksumSha256(),
          attachment.getCreatedAt());
    }
  }

  public record InspectionMessageItem(
      UUID id,
      UUID inspectionId,
      UUID authorUserId,
      String authorName,
      String authorRole,
      String body,
      Instant readAt,
      Instant createdAt,
      List<InspectionMessageAttachmentItem> attachments) {

    public static InspectionMessageItem from(
        InspectionMessage message, String authorName, String authorRole) {
      return new InspectionMessageItem(
          message.getId(),
          message.getInspectionId(),
          message.getAuthorUserId(),
          authorName,
          authorRole,
          message.getBody(),
          message.getReadAt(),
          message.getCreatedAt(),
          message.getAttachments().stream().map(InspectionMessageAttachmentItem::from).toList());
    }
  }

  public static InspectionChatThreadResponse from(
      Inspection inspection,
      String assetName,
      UUID counterpartUserId,
      String counterpartName,
      String counterpartRole,
      String counterpartAvatarUrl,
      List<InspectionMessageItem> messages) {
    return new InspectionChatThreadResponse(
        inspection.getId(),
        inspection.getAssetId(),
        assetName,
        inspection.getStatus().name(),
        inspection.getAssignmentStatus().name(),
        inspection.getPlannedStartAt(),
        inspection.getPlannedEndAt(),
        counterpartUserId,
        counterpartName,
        counterpartRole,
        counterpartAvatarUrl,
        messages);
  }
}

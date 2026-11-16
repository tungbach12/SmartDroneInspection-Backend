package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.ServiceRequestMessageAttachment;
import java.time.Instant;
import java.util.UUID;

public record ServiceRequestMessageAttachmentResponse(
    UUID id,
    String fileName,
    String contentType,
    long sizeBytes,
    String checksumSha256,
    Instant createdAt) {

  public static ServiceRequestMessageAttachmentResponse from(
      ServiceRequestMessageAttachment attachment) {
    return new ServiceRequestMessageAttachmentResponse(
        attachment.getId(),
        attachment.getFileName(),
        attachment.getContentType(),
        attachment.getSizeBytes(),
        attachment.getChecksumSha256(),
        attachment.getCreatedAt());
  }
}

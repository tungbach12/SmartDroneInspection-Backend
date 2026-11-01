package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.ServiceRequestMessage;
import java.time.Instant;
import java.util.UUID;

public record ServiceRequestMessageResponse(
    UUID id,
    UUID authorUserId,
    String authorName,
    String authorRole,
    String body,
    Instant createdAt) {

  public static ServiceRequestMessageResponse from(ServiceRequestMessage message) {
    return new ServiceRequestMessageResponse(
        message.getId(),
        message.getAuthorUserId(),
        null,
        null,
        message.getBody(),
        message.getCreatedAt());
  }

  public static ServiceRequestMessageResponse from(
      ServiceRequestMessage message, String authorName, String authorRole) {
    return new ServiceRequestMessageResponse(
        message.getId(),
        message.getAuthorUserId(),
        authorName,
        authorRole,
        message.getBody(),
        message.getCreatedAt());
  }
}

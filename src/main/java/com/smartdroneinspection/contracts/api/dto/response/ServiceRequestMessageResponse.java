package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.ServiceRequestMessage;
import java.time.Instant;
import java.util.UUID;

public record ServiceRequestMessageResponse(
    UUID id, UUID authorUserId, String body, Instant createdAt) {
  public static ServiceRequestMessageResponse from(ServiceRequestMessage message) {
    return new ServiceRequestMessageResponse(
        message.getId(), message.getAuthorUserId(), message.getBody(), message.getCreatedAt());
  }
}

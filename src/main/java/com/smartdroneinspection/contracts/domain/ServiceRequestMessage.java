package com.smartdroneinspection.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.util.StringUtils;

@Entity
@Table(name = "service_request_messages")
public class ServiceRequestMessage {

  @Id @GeneratedValue private UUID id;

  @Column(name = "request_id", nullable = false)
  private UUID requestId;

  @Column(name = "author_user_id", nullable = false)
  private UUID authorUserId;

  @Column(nullable = false, length = 4000)
  private String body;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected ServiceRequestMessage() {}

  public ServiceRequestMessage(UUID requestId, UUID authorUserId, String body) {
    if (requestId == null || authorUserId == null || !StringUtils.hasText(body)) {
      throw new IllegalArgumentException("Message request, author, and body are required");
    }
    if (body.trim().length() > 4000) {
      throw new IllegalArgumentException("Message must not exceed 4000 characters");
    }
    this.requestId = requestId;
    this.authorUserId = authorUserId;
    this.body = body.trim();
  }

  public UUID getId() {
    return id;
  }

  public UUID getRequestId() {
    return requestId;
  }

  public UUID getAuthorUserId() {
    return authorUserId;
  }

  public String getBody() {
    return body;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

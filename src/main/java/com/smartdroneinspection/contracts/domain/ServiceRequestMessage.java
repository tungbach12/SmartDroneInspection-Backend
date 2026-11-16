package com.smartdroneinspection.contracts.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

  @Column(name = "read_at")
  private Instant readAt;

  @OneToMany(
      mappedBy = "message",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<ServiceRequestMessageAttachment> attachments = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected ServiceRequestMessage() {}

  public ServiceRequestMessage(UUID requestId, UUID authorUserId, String body) {
    this(requestId, authorUserId, body, false);
  }

  public ServiceRequestMessage(
      UUID requestId, UUID authorUserId, String body, boolean allowEmptyBody) {
    if (requestId == null
        || authorUserId == null
        || (!allowEmptyBody && !StringUtils.hasText(body))) {
      throw new IllegalArgumentException("Message request, author, and body are required");
    }
    String normalizedBody = body == null ? "" : body.trim();
    if (normalizedBody.length() > 4000) {
      throw new IllegalArgumentException("Message must not exceed 4000 characters");
    }
    this.requestId = requestId;
    this.authorUserId = authorUserId;
    this.body = normalizedBody;
  }

  public void addAttachment(ServiceRequestMessageAttachment attachment) {
    if (attachment == null || attachment.getMessage() != this) {
      throw new IllegalArgumentException("Attachment must reference this message");
    }
    attachments.add(attachment);
  }

  public ServiceRequestMessageAttachment addAttachment(
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      String objectKey) {
    ServiceRequestMessageAttachment attachment =
        new ServiceRequestMessageAttachment(
            this, fileName, contentType, sizeBytes, checksumSha256, objectKey);
    attachments.add(attachment);
    return attachment;
  }

  public List<ServiceRequestMessageAttachment> getAttachments() {
    return List.copyOf(attachments);
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

  public Instant getReadAt() {
    return readAt;
  }

  public void markRead(Instant at) {
    if (this.readAt == null) {
      this.readAt = at != null ? at : Instant.now();
    }
  }
}

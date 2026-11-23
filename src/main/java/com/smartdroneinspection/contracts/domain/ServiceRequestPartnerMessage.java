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
@Table(name = "service_request_partner_messages")
public class ServiceRequestPartnerMessage {

  @Id @GeneratedValue private UUID id;

  @Column(name = "request_id", nullable = false)
  private UUID requestId;

  @Column(name = "inspector_id", nullable = false)
  private UUID inspectorId;

  @Column(name = "author_user_id", nullable = false)
  private UUID authorUserId;

  @Column(nullable = false, length = 4000)
  private String body;

  @Column(name = "has_attachments", nullable = false)
  private boolean hasAttachments;

  @Column(name = "read_at")
  private Instant readAt;

  @OneToMany(
      mappedBy = "message",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<ServiceRequestPartnerMessageAttachment> attachments = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected ServiceRequestPartnerMessage() {}

  public ServiceRequestPartnerMessage(
      UUID requestId, UUID inspectorId, UUID authorUserId, String body, boolean hasAttachments) {
    if (requestId == null
        || inspectorId == null
        || authorUserId == null
        || (!hasAttachments && !StringUtils.hasText(body))) {
      throw new IllegalArgumentException(
          "Partner message request, inspector, author, and body are required");
    }
    String normalizedBody = body == null ? "" : body.trim();
    if (normalizedBody.length() > 4000) {
      throw new IllegalArgumentException("Message must not exceed 4000 characters");
    }
    this.requestId = requestId;
    this.inspectorId = inspectorId;
    this.authorUserId = authorUserId;
    this.body = normalizedBody;
    this.hasAttachments = hasAttachments;
  }

  public ServiceRequestPartnerMessageAttachment addAttachment(
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      String objectKey) {
    ServiceRequestPartnerMessageAttachment attachment =
        new ServiceRequestPartnerMessageAttachment(
            this, fileName, contentType, sizeBytes, checksumSha256, objectKey);
    attachments.add(attachment);
    this.hasAttachments = true;
    return attachment;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRequestId() {
    return requestId;
  }

  public UUID getInspectorId() {
    return inspectorId;
  }

  public UUID getAuthorUserId() {
    return authorUserId;
  }

  public String getBody() {
    return body;
  }

  public boolean isHasAttachments() {
    return hasAttachments;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public List<ServiceRequestPartnerMessageAttachment> getAttachments() {
    return List.copyOf(attachments);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void markRead(Instant at) {
    if (this.readAt == null) {
      this.readAt = at != null ? at : Instant.now();
    }
  }
}

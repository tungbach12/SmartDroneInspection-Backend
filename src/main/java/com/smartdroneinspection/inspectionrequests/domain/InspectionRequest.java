package com.smartdroneinspection.inspectionrequests.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "inspection_requests")
public class InspectionRequest {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Column(name = "schedule_id")
  private UUID scheduleId;

  @Column(name = "checklist_template_id", nullable = false)
  private UUID checklistTemplateId;

  @Enumerated(EnumType.STRING)
  @Column(name = "request_type", nullable = false, length = 16)
  private InspectionRequestType requestType;

  @Column(name = "due_cycle")
  private LocalDate dueCycle;

  @Column(name = "linked_maintenance_ticket_id")
  private UUID linkedMaintenanceTicketId;

  @Column(name = "requested_by_user_id")
  private UUID requestedByUserId;

  @Column(nullable = false, length = 4000)
  private String scope;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private InspectionRequestPriority priority;

  @Column(name = "preferred_deadline")
  private Instant preferredDeadline;

  @Column(name = "site_access_constraints", length = 2000)
  private String siteAccessConstraints;

  @Column(name = "contact_name", length = 200)
  private String contactName;

  @Column(name = "contact_phone", length = 32)
  private String contactPhone;

  @Column(name = "contact_email", length = 320)
  private String contactEmail;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private InspectionRequestStatus status;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  @OneToMany(
      mappedBy = "inspectionRequest",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("createdAt DESC")
  private List<InspectionRequestAttachment> attachments = new ArrayList<>();

  protected InspectionRequest() {}

  public static InspectionRequest periodic(
      UUID organizationId,
      UUID assetId,
      UUID scheduleId,
      UUID checklistTemplateId,
      LocalDate dueCycle,
      UUID requestedByUserId,
      String scope,
      InspectionRequestPriority priority,
      Instant preferredDeadline,
      String siteAccessConstraints,
      String contactName,
      String contactPhone,
      String contactEmail,
      UUID linkedMaintenanceTicketId) {
    if (scheduleId == null) {
      throw new IllegalArgumentException("Periodic requests require a schedule");
    }
    if (dueCycle == null) {
      throw new IllegalArgumentException("Periodic requests require a due cycle");
    }
    return new InspectionRequest(
        organizationId,
        assetId,
        scheduleId,
        checklistTemplateId,
        InspectionRequestType.PERIODIC,
        dueCycle,
        requestedByUserId,
        scope,
        priority,
        preferredDeadline,
        siteAccessConstraints,
        contactName,
        contactPhone,
        contactEmail,
        linkedMaintenanceTicketId);
  }

  public static InspectionRequest adHoc(
      UUID organizationId,
      UUID assetId,
      UUID checklistTemplateId,
      UUID requestedByUserId,
      String scope,
      InspectionRequestPriority priority,
      Instant preferredDeadline,
      String siteAccessConstraints,
      String contactName,
      String contactPhone,
      String contactEmail,
      UUID linkedMaintenanceTicketId) {
    return new InspectionRequest(
        organizationId,
        assetId,
        null,
        checklistTemplateId,
        InspectionRequestType.AD_HOC,
        null,
        requestedByUserId,
        scope,
        priority,
        preferredDeadline,
        siteAccessConstraints,
        contactName,
        contactPhone,
        contactEmail,
        linkedMaintenanceTicketId);
  }

  private InspectionRequest(
      UUID organizationId,
      UUID assetId,
      UUID scheduleId,
      UUID checklistTemplateId,
      InspectionRequestType requestType,
      LocalDate dueCycle,
      UUID requestedByUserId,
      String scope,
      InspectionRequestPriority priority,
      Instant preferredDeadline,
      String siteAccessConstraints,
      String contactName,
      String contactPhone,
      String contactEmail,
      UUID linkedMaintenanceTicketId) {
    this.organizationId = Objects.requireNonNull(organizationId, "Organization is required");
    this.assetId = Objects.requireNonNull(assetId, "Asset is required");
    this.checklistTemplateId =
        Objects.requireNonNull(checklistTemplateId, "Checklist template is required");
    if (scope == null || scope.isBlank()) {
      throw new IllegalArgumentException("Request scope must not be blank");
    }
    this.scope = scope;
    this.scheduleId = scheduleId;
    this.requestType = Objects.requireNonNull(requestType, "Request type is required");
    this.dueCycle = dueCycle;
    this.requestedByUserId = requestedByUserId;
    this.priority = Objects.requireNonNull(priority, "Request priority is required");
    this.preferredDeadline = preferredDeadline;
    this.siteAccessConstraints = siteAccessConstraints;
    this.contactName = contactName;
    this.contactPhone = contactPhone;
    this.contactEmail = contactEmail;
    this.linkedMaintenanceTicketId = linkedMaintenanceTicketId;
    this.status = InspectionRequestStatus.DRAFT;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public void submit() {
    if (status != InspectionRequestStatus.DRAFT) {
      throw new IllegalStateException("Only draft requests can be submitted");
    }
    status = InspectionRequestStatus.SUBMITTED;
    submittedAt = Instant.now();
    touch();
  }

  public void markQuoted() {
    transitionTo(
        InspectionRequestStatus.QUOTED,
        InspectionRequestStatus.SUBMITTED,
        InspectionRequestStatus.UNDER_REVIEW,
        InspectionRequestStatus.REVISION_REQUIRED);
  }

  public void markAwaitingClientApproval() {
    transitionTo(InspectionRequestStatus.AWAITING_CLIENT_APPROVAL, InspectionRequestStatus.QUOTED);
  }

  public void markOrderConfirmed() {
    transitionTo(
        InspectionRequestStatus.ORDER_CONFIRMED, InspectionRequestStatus.AWAITING_CLIENT_APPROVAL);
  }

  public void cancel() {
    if (status == InspectionRequestStatus.CANCELLED) {
      throw new IllegalStateException("Request is already cancelled");
    }
    if (status == InspectionRequestStatus.ORDER_CONFIRMED
        || status == InspectionRequestStatus.ASSIGNMENT_PENDING
        || status == InspectionRequestStatus.READY_FOR_INSPECTION
        || status == InspectionRequestStatus.MANUAL_REVIEW) {
      throw new IllegalStateException("Order-progress requests cannot be cancelled");
    }
    status = InspectionRequestStatus.CANCELLED;
    touch();
  }

  public InspectionRequestAttachment addAttachment(
      UUID uploadedByUserId,
      String fileName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      String objectKey,
      String description) {
    InspectionRequestAttachment attachment =
        new InspectionRequestAttachment(
            this,
            uploadedByUserId,
            fileName,
            contentType,
            sizeBytes,
            checksumSha256,
            objectKey,
            description);
    attachments.add(attachment);
    touch();
    return attachment;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getAssetId() {
    return assetId;
  }

  public UUID getScheduleId() {
    return scheduleId;
  }

  public UUID getChecklistTemplateId() {
    return checklistTemplateId;
  }

  public InspectionRequestType getRequestType() {
    return requestType;
  }

  public LocalDate getDueCycle() {
    return dueCycle;
  }

  public InspectionRequestStatus getStatus() {
    return status;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public List<InspectionRequestAttachment> getAttachments() {
    return List.copyOf(attachments);
  }

  private void transitionTo(InspectionRequestStatus next, InspectionRequestStatus... allowed) {
    for (InspectionRequestStatus current : allowed) {
      if (status == current) {
        status = next;
        touch();
        return;
      }
    }
    throw new IllegalStateException("Invalid request status transition to " + next);
  }

  private void touch() {
    updatedAt = Instant.now();
  }
}

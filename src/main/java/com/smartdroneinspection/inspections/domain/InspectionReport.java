package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One immutable-at-publication version of an inspection report. */
@Entity
@Table(name = "inspection_reports")
public class InspectionReport {

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_id", nullable = false)
  private UUID inspectionId;

  @Column(name = "version_no", nullable = false)
  private int versionNo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ReportStatus status;

  @Column(name = "reviewer_user_id")
  private UUID reviewerUserId;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "return_reason", length = 2000)
  private String returnReason;

  @Column(name = "summary", columnDefinition = "TEXT")
  private String summary;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  protected InspectionReport() {}

  public InspectionReport(UUID inspectionId, int versionNo) {
    this.inspectionId = inspectionId;
    this.versionNo = versionNo;
    this.status = ReportStatus.SUBMITTED;
    this.submittedAt = Instant.now();
    this.createdAt = Instant.now();
  }

  public InspectionReport(UUID inspectionId, int versionNo, String summary) {
    this(inspectionId, versionNo);
    this.summary = summary;
  }

  /** Re-verifying a returned version clears return metadata and submits. */
  public void verifyAsAuthor(UUID verifyingUserId, UUID reportAuthorUserId) {
    if (verifyingUserId == null || !verifyingUserId.equals(reportAuthorUserId)) {
      throw new IllegalStateException("Only the report author may verify this version");
    }
    if (status == ReportStatus.RETURNED) {
      submit();
    }
  }

  public void editContent(String newContent) {
    requireStatus(ReportStatus.SUBMITTED, ReportStatus.RETURNED);
    if (newContent != null && !newContent.isBlank()) {
      this.summary = newContent;
    }
  }

  public void submit() {
    if (status == ReportStatus.SUBMITTED) {
      return;
    }
    requireStatus(ReportStatus.RETURNED);
    reviewerUserId = null;
    reviewedAt = null;
    returnReason = null;
    submittedAt = Instant.now();
    status = ReportStatus.SUBMITTED;
  }

  public void reviewBy(UUID reviewerId, boolean approve, String reason) {
    requireStatus(ReportStatus.SUBMITTED);
    if (reviewerId == null) {
      throw new IllegalArgumentException("A reviewer is required");
    }
    if (approve) {
      throw new IllegalArgumentException(
          "Reviewer approval directly publishes the report via publish()");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("A return requires a reason");
    }
    reviewerUserId = reviewerId;
    reviewedAt = Instant.now();
    returnReason = reason;
    status = ReportStatus.RETURNED;
  }

  public void withdraw(UUID actorId, UUID reportAuthorUserId) {
    requireStatus(ReportStatus.SUBMITTED);
    if (actorId == null || !actorId.equals(reportAuthorUserId)) {
      throw new IllegalStateException("Only the report author can withdraw this submission");
    }
    if (reviewedAt != null || reviewerUserId != null) {
      throw new IllegalStateException(
          "A version the reviewer already acted on cannot be withdrawn");
    }
    status = ReportStatus.RETURNED;
    returnReason = "Withdrawn by author for revision";
  }

  /** Publication is the reviewer's approval decision; the result is immutable. */
  public void publish(UUID publishingUserId, UUID reportAuthorUserId) {
    requireStatus(ReportStatus.SUBMITTED);
    if (publishingUserId == null) {
      throw new IllegalArgumentException("A publishing user is required");
    }
    if (publishingUserId.equals(reportAuthorUserId)) {
      throw new IllegalStateException("Publication requires a qualified reviewer decision");
    }
    reviewerUserId = publishingUserId;
    reviewedAt = Instant.now();
    status = ReportStatus.PUBLISHED;
    publishedAt = Instant.now();
  }

  public void supersede() {
    if (status != ReportStatus.PUBLISHED) {
      throw new IllegalStateException("Only a published version can be superseded");
    }
    status = ReportStatus.SUPERSEDED;
  }

  public void returnForCustomerClarification(UUID customerId, String reason) {
    requireStatus(ReportStatus.PUBLISHED);
    if (customerId == null) {
      throw new IllegalArgumentException("Customer requester ID is required");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("A clarification request reason is required");
    }
    reviewerUserId = customerId;
    reviewedAt = Instant.now();
    publishedAt = null;
    returnReason = "[Yêu cầu làm rõ từ Khách hàng] " + reason.trim();
    status = ReportStatus.RETURNED;
  }

  public boolean isImmutable() {
    return status == ReportStatus.PUBLISHED || status == ReportStatus.SUPERSEDED;
  }

  private void requireStatus(ReportStatus... allowed) {
    for (ReportStatus candidate : allowed) {
      if (status == candidate) return;
    }
    throw new IllegalStateException(
        "Report version " + versionNo + " is " + status + "; expected one of " + allowed);
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getInspectionReportId() {
    return inspectionId;
  }

  public int getVersionNo() {
    return versionNo;
  }

  public ReportStatus getStatus() {
    return status;
  }

  public UUID getReviewerUserId() {
    return reviewerUserId;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public String getReturnReason() {
    return returnReason;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getContentSnapshot() {
    return null;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

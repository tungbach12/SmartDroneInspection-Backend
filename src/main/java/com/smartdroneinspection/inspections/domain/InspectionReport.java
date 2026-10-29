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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "content_snapshot", nullable = false, columnDefinition = "jsonb")
  private String contentSnapshot;

  @Column(name = "rendered_object_key", length = 1000)
  private String renderedObjectKey;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "rendered_checksum_sha256", length = 64)
  private String renderedChecksumSha256;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  protected InspectionReport() {}

  public InspectionReport(UUID inspectionId, int versionNo, String contentSnapshot) {
    this.inspectionId = inspectionId;
    this.versionNo = versionNo;
    this.contentSnapshot = contentSnapshot;
    this.status = ReportStatus.DRAFT;
    this.createdAt = Instant.now();
  }

  /** Only the report author may verify the draft against its sources. */
  public void verifyAsAuthor(UUID verifyingUserId, UUID reportAuthorUserId) {
    requireStatus(ReportStatus.DRAFT, ReportStatus.RETURNED);
    if (verifyingUserId == null || !verifyingUserId.equals(reportAuthorUserId)) {
      throw new IllegalStateException("Only the report author may verify this version");
    }
    // A renewed verification starts a fresh review round; an earlier return is no longer current.
    reviewerUserId = null;
    reviewedAt = null;
    returnReason = null;
    status = ReportStatus.AUTHOR_VERIFIED;
  }

  public void editContent(String newContent) {
    requireStatus(ReportStatus.DRAFT, ReportStatus.RETURNED);
    contentSnapshot = newContent;
  }

  public void recordArtefact(String objectKey, String checksumSha256) {
    requireStatus(ReportStatus.PUBLISHED);
    if (objectKey == null
        || objectKey.isBlank()
        || checksumSha256 == null
        || checksumSha256.isBlank()) {
      throw new IllegalArgumentException("A published report requires its object key and checksum");
    }
    renderedObjectKey = objectKey;
    renderedChecksumSha256 = checksumSha256;
  }

  public String getRenderedObjectKey() {
    return renderedObjectKey;
  }

  public String getRenderedChecksumSha256() {
    return renderedChecksumSha256;
  }

  public void submit() {
    if (status == ReportStatus.DRAFT || status == ReportStatus.RETURNED) {
      status = ReportStatus.AUTHOR_VERIFIED;
    }
    requireStatus(ReportStatus.AUTHOR_VERIFIED);
    submittedAt = Instant.now();
    status = ReportStatus.SUBMITTED;
  }

  public void reviewBy(UUID reviewerId, boolean approve, String reason) {
    requireStatus(ReportStatus.SUBMITTED);
    if (reviewerId == null) {
      throw new IllegalArgumentException("A reviewer is required");
    }
    if (!approve && (reason == null || reason.isBlank())) {
      throw new IllegalArgumentException("A return requires a reason");
    }
    reviewerUserId = reviewerId;
    reviewedAt = Instant.now();
    returnReason = approve ? null : reason;
    status = approve ? ReportStatus.APPROVED : ReportStatus.RETURNED;
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
    status = ReportStatus.DRAFT;
    submittedAt = null;
  }

  /** Publication is a separate act after approval; the result is immutable. */
  public void publish(UUID publishingUserId, UUID reportAuthorUserId) {
    requireStatus(ReportStatus.APPROVED);
    if (publishingUserId == null) {
      throw new IllegalArgumentException("A publishing user is required");
    }
    if (reviewerUserId == null || reviewerUserId.equals(reportAuthorUserId)) {
      throw new IllegalStateException("Publication requires a qualified reviewer decision");
    }
    status = ReportStatus.PUBLISHED;
    publishedAt = Instant.now();
  }

  public void supersede() {
    if (status != ReportStatus.PUBLISHED) {
      throw new IllegalStateException("Only a published version can be superseded");
    }
    status = ReportStatus.SUPERSEDED;
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

  public String getContentSnapshot() {
    return contentSnapshot;
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

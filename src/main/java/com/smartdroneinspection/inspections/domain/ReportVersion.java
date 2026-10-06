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

@Entity
@Table(name = "report_versions")
public class ReportVersion {

  @Id @GeneratedValue private UUID id;

  @Column(name = "report_id", nullable = false)
  private UUID reportId;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Column(name = "source_version_id")
  private UUID sourceVersionId;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "content_snapshot", nullable = false, columnDefinition = "jsonb")
  private String contentSnapshot;

  @Column(name = "pdf_object_key", length = 1000)
  private String pdfObjectKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ReportStatus status;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "technically_approved_at")
  private Instant technicallyApprovedAt;

  @Column(name = "released_at")
  private Instant releasedAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "client_decision_by_user_id")
  private UUID clientDecisionByUserId;

  @Column(name = "client_decision_reason", length = 2000)
  private String clientDecisionReason;

  @Column(name = "author_verified_by_user_id")
  private UUID authorVerifiedByUserId;

  @Column(name = "author_verified_at")
  private Instant authorVerifiedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "author_verification_snapshot", columnDefinition = "jsonb")
  private String authorVerificationSnapshot;

  @Column(name = "completeness_checked_by_user_id")
  private UUID completenessCheckedByUserId;

  @Column(name = "completeness_checked_at")
  private Instant completenessCheckedAt;

  @Column(name = "completeness_return_reason", length = 2000)
  private String completenessReturnReason;

  @Column(nullable = false)
  private boolean immutable;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ReportVersion() {}

  public ReportVersion(
      UUID reportId,
      int versionNumber,
      UUID sourceVersionId,
      UUID createdByUserId,
      String contentSnapshot) {
    this.reportId = reportId;
    this.versionNumber = versionNumber;
    this.sourceVersionId = sourceVersionId;
    this.createdByUserId = createdByUserId;
    this.contentSnapshot = contentSnapshot;
    this.status = ReportStatus.DRAFT;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getReportId() {
    return reportId;
  }

  public UUID getSourceVersionId() {
    return sourceVersionId;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public String getContentSnapshot() {
    return contentSnapshot;
  }

  public void updateContentSnapshot(String newSnapshot) {
    requireStatus(ReportStatus.DRAFT);
    this.contentSnapshot = newSnapshot;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public ReportStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getReleasedAt() {
    return releasedAt;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }

  public UUID getClientDecisionByUserId() {
    return clientDecisionByUserId;
  }

  public String getClientDecisionReason() {
    return clientDecisionReason;
  }

  public boolean isImmutable() {
    return immutable;
  }

  public UUID getAuthorVerifiedByUserId() {
    return authorVerifiedByUserId;
  }

  public Instant getAuthorVerifiedAt() {
    return authorVerifiedAt;
  }

  public String getAuthorVerificationSnapshot() {
    return authorVerificationSnapshot;
  }

  public UUID getCompletenessCheckedByUserId() {
    return completenessCheckedByUserId;
  }

  public Instant getCompletenessCheckedAt() {
    return completenessCheckedAt;
  }

  public String getCompletenessReturnReason() {
    return completenessReturnReason;
  }

  /** Completeness gate passed: record which Provider Manager ran it and when. */
  public void markCompletenessChecked(UUID actorId) {
    this.completenessCheckedByUserId = actorId;
    this.completenessCheckedAt = Instant.now();
  }

  /** Completeness gate failure reason recorded by the Provider Manager at release. */
  public void recordCompletenessFailure(UUID actorId, String reason) {
    requireStatus(ReportStatus.TECHNICALLY_APPROVED);
    this.completenessCheckedByUserId = actorId;
    this.completenessCheckedAt = Instant.now();
    this.completenessReturnReason = reason;
  }

  /** MF3-07: the authoring Inspector signs off the completed draft. */
  public void verify(UUID authorId, String snapshot) {
    requireStatus(ReportStatus.DRAFT);
    status = ReportStatus.TECHNICALLY_APPROVED;
    submittedAt = Instant.now();
    technicallyApprovedAt = submittedAt;
    this.authorVerifiedByUserId = authorId;
    this.authorVerifiedAt = submittedAt;
    this.authorVerificationSnapshot = snapshot;
  }

  public void release() {
    requireStatus(ReportStatus.TECHNICALLY_APPROVED);
    status = ReportStatus.RELEASED;
    releasedAt = Instant.now();
  }

  public void accept(UUID clientUserId) {
    requireStatus(ReportStatus.RELEASED);
    status = ReportStatus.ACCEPTED;
    acceptedAt = Instant.now();
    clientDecisionByUserId = clientUserId;
    immutable = true;
  }

  public void requestClientRevision(UUID clientUserId, String reason) {
    requireStatus(ReportStatus.RELEASED);
    if (clientUserId == null || reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("A Client revision decision and reason are required");
    }
    status = ReportStatus.REVISION_REQUESTED;
    clientDecisionByUserId = clientUserId;
    clientDecisionReason = reason.trim();
  }

  private void requireStatus(ReportStatus expected) {
    if (immutable || status != expected) {
      throw new IllegalStateException("Invalid report version transition from " + status);
    }
  }
}

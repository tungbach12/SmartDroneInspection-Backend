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

/**
 * One version of an inspection report.
 *
 * <p>The lifecycle is DRAFT -> AUTHOR_VERIFIED -> SUBMITTED -> (RETURNED | APPROVED) -> PUBLISHED,
 * with SUPERSEDED when a later version replaces it. Every transition is an explicit human act: the
 * author verifies what the model drafted, and a reviewer who is not the author decides. Nothing
 * advances on a timer, and a published version is immutable.
 */
@Entity
@Table(name = "inspection_report_versions")
public class InspectionReportVersion {

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_report_id", nullable = false)
  private UUID inspectionReportId;

  @Column(name = "version_no", nullable = false)
  private int versionNo;

  @Column(name = "source_version_id")
  private UUID sourceVersionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ReportStatus status;

  @Column(name = "author_user_id", nullable = false)
  private UUID authorUserId;

  @Column(name = "author_verified_by_user_id")
  private UUID authorVerifiedByUserId;

  @Column(name = "author_verified_at")
  private Instant authorVerifiedAt;

  @Column(name = "reviewer_user_id")
  private UUID reviewerUserId;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "review_reason", length = 2000)
  private String reviewReason;

  @Column(name = "llm_provider", length = 96)
  private String llmProvider;

  @Column(name = "llm_model", length = 160)
  private String llmModel;

  @Column(name = "prompt_version", length = 96)
  private String promptVersion;

  @Column(name = "generated_at")
  private Instant generatedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "content_snapshot", nullable = false, columnDefinition = "jsonb")
  private String contentSnapshot;

  @Column(name = "rendered_object_key", length = 1000)
  private String renderedObjectKey;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "rendered_checksum_sha256", length = 64)
  private String renderedChecksumSha256;

  @Column(name = "evidence_snapshot_hash", length = 128)
  private String evidenceSnapshotHash;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected InspectionReportVersion() {}

  public InspectionReportVersion(
      UUID inspectionReportId,
      int versionNo,
      UUID sourceVersionId,
      UUID authorUserId,
      String contentSnapshot,
      String llmProvider,
      String llmModel,
      String promptVersion,
      String evidenceSnapshotHash) {
    this.inspectionReportId = inspectionReportId;
    this.versionNo = versionNo;
    this.sourceVersionId = sourceVersionId;
    this.authorUserId = authorUserId;
    this.contentSnapshot = contentSnapshot;
    this.llmProvider = llmProvider;
    this.llmModel = llmModel;
    this.promptVersion = promptVersion;
    this.evidenceSnapshotHash = evidenceSnapshotHash;
    this.status = ReportStatus.DRAFT;
    this.generatedAt = contentSnapshot == null ? null : Instant.now();
    this.createdAt = Instant.now();
  }

  /** Only the author may verify the draft against its sources. */
  public void verifyAsAuthor(UUID verifyingUserId) {
    requireStatus(ReportStatus.DRAFT, ReportStatus.RETURNED);
    if (!authorUserId.equals(verifyingUserId)) {
      throw new IllegalStateException("Only the report author may verify this version");
    }
    this.authorVerifiedByUserId = verifyingUserId;
    this.authorVerifiedAt = Instant.now();
    this.status = ReportStatus.AUTHOR_VERIFIED;
  }

  public void editContent(String newContent) {
    requireStatus(ReportStatus.DRAFT, ReportStatus.RETURNED);
    this.contentSnapshot = newContent;
  }

  public void submit() {
    requireStatus(ReportStatus.AUTHOR_VERIFIED);
    if (authorVerifiedByUserId == null || authorVerifiedAt == null) {
      throw new IllegalStateException("A version must be author-verified before submission");
    }
    this.status = ReportStatus.SUBMITTED;
  }

  /**
   * The qualified reviewer records a decision. Separation of duties is a domain invariant here, not
   * only a service check: no report is self-reviewed.
   */
  public void reviewBy(UUID reviewerId, boolean approve, String reason) {
    requireStatus(ReportStatus.SUBMITTED);
    if (reviewerId == null) {
      throw new IllegalArgumentException("A reviewer is required");
    }
    if (authorUserId.equals(reviewerId)) {
      throw new IllegalStateException("The report author cannot review their own report");
    }
    if (!approve && (reason == null || reason.isBlank())) {
      throw new IllegalArgumentException("A return requires a reason");
    }
    this.reviewerUserId = reviewerId;
    this.reviewedAt = Instant.now();
    this.reviewReason = reason;
    this.status = approve ? ReportStatus.APPROVED : ReportStatus.RETURNED;
  }

  /** Publication is a separate act after approval; the result is immutable. */
  public void publish(UUID publishingUserId) {
    requireStatus(ReportStatus.APPROVED);
    if (publishingUserId == null) {
      throw new IllegalArgumentException("A publishing user is required");
    }
    if (authorUserId.equals(publishingUserId) && reviewerUserId == null) {
      throw new IllegalStateException("Publication requires a qualified reviewer decision");
    }
    this.status = ReportStatus.PUBLISHED;
    this.publishedAt = Instant.now();
  }

  public void supersede() {
    if (status != ReportStatus.PUBLISHED) {
      throw new IllegalStateException("Only a published version can be superseded");
    }
    this.status = ReportStatus.SUPERSEDED;
  }

  public boolean isImmutable() {
    return status == ReportStatus.PUBLISHED || status == ReportStatus.SUPERSEDED;
  }

  private void requireStatus(ReportStatus... allowed) {
    for (ReportStatus candidate : allowed) {
      if (status == candidate) {
        return;
      }
    }
    throw new IllegalStateException(
        "Report version " + versionNo + " is " + status + "; expected one of " + allowed);
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectionReportId() {
    return inspectionReportId;
  }

  public int getVersionNo() {
    return versionNo;
  }

  public UUID getSourceVersionId() {
    return sourceVersionId;
  }

  public ReportStatus getStatus() {
    return status;
  }

  public UUID getAuthorUserId() {
    return authorUserId;
  }

  public UUID getAuthorVerifiedByUserId() {
    return authorVerifiedByUserId;
  }

  public Instant getAuthorVerifiedAt() {
    return authorVerifiedAt;
  }

  public UUID getReviewerUserId() {
    return reviewerUserId;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public String getReviewReason() {
    return reviewReason;
  }

  public String getLlmProvider() {
    return llmProvider;
  }

  public String getLlmModel() {
    return llmModel;
  }

  public String getPromptVersion() {
    return promptVersion;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public String getContentSnapshot() {
    return contentSnapshot;
  }

  public String getEvidenceSnapshotHash() {
    return evidenceSnapshotHash;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.ReportVersionStatus;
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
 * A version of the completion report the team submits for the work performed.
 *
 * <p>The lifecycle mirrors MF3: the designated author verifies the content against the team's
 * records, and a qualified reviewer who is not the author decides. AI drafting is optional
 * metadata; the author remains accountable for the content even when a model drafted it, and no
 * financial total is ever taken from generated text.
 */
@Entity
@Table(name = "maintenance_report_versions")
public class MaintenanceReportVersion {

  @Id @GeneratedValue private UUID id;

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Column(name = "version_no", nullable = false)
  private int versionNo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ReportVersionStatus status;

  @Column(name = "author_user_id", nullable = false)
  private UUID authorUserId;

  @Column(name = "author_verified_at")
  private Instant authorVerifiedAt;

  @Column(name = "llm_provider", length = 96)
  private String llmProvider;

  @Column(name = "llm_model", length = 160)
  private String llmModel;

  @Column(name = "prompt_version", length = 96)
  private String promptVersion;

  @Column(name = "generated_at")
  private Instant generatedAt;

  @Column(name = "approved_scope_hash", length = 128)
  private String approvedScopeHash;

  @Column(name = "change_snapshot_hash", length = 128)
  private String changeSnapshotHash;

  @Column(name = "work_log_snapshot_hash", length = 128)
  private String workLogSnapshotHash;

  @Column(name = "actual_cost_snapshot_hash", length = 128)
  private String actualCostSnapshotHash;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "content_snapshot", nullable = false, columnDefinition = "jsonb")
  private String contentSnapshot;

  @Column(name = "rendered_object_key", length = 1000)
  private String renderedObjectKey;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "rendered_checksum_sha256", length = 64)
  private String renderedChecksumSha256;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MaintenanceReportVersion() {}

  public MaintenanceReportVersion(
      UUID workOrderId,
      int versionNo,
      UUID authorUserId,
      String contentSnapshot,
      String approvedScopeHash,
      String changeSnapshotHash,
      String workLogSnapshotHash,
      String actualCostSnapshotHash,
      String llmProvider,
      String llmModel,
      String promptVersion) {
    this.workOrderId = require(workOrderId, "workOrderId");
    this.versionNo = requirePositive(versionNo, "versionNo");
    this.authorUserId = require(authorUserId, "authorUserId");
    this.contentSnapshot = contentSnapshot == null ? "{}" : contentSnapshot;
    this.approvedScopeHash = approvedScopeHash;
    this.changeSnapshotHash = changeSnapshotHash;
    this.workLogSnapshotHash = workLogSnapshotHash;
    this.actualCostSnapshotHash = actualCostSnapshotHash;
    this.llmProvider = llmProvider;
    this.llmModel = llmModel;
    this.promptVersion = promptVersion;
    this.generatedAt = llmProvider == null ? null : Instant.now();
    this.status = ReportVersionStatus.DRAFT;
    this.createdAt = Instant.now();
  }

  /** MF4-16: only the designated author may verify the report against the team's records. */
  public void verifyAsAuthor(UUID verifyingUserId) {
    requireStatus(ReportVersionStatus.DRAFT, ReportVersionStatus.RETURNED);
    if (!authorUserId.equals(verifyingUserId)) {
      throw new IllegalStateException("Only the report author may verify this version");
    }
    this.authorVerifiedAt = Instant.now();
    this.status = ReportVersionStatus.AUTHOR_VERIFIED;
  }

  public void editContent(String newContent) {
    requireStatus(ReportVersionStatus.DRAFT, ReportVersionStatus.RETURNED);
    if (newContent == null || newContent.isBlank()) {
      throw new IllegalArgumentException("Report content is required");
    }
    this.contentSnapshot = newContent;
  }

  /** MF4-17: submission hands the version to the independent reviewer. */
  public void submit() {
    requireStatus(ReportVersionStatus.AUTHOR_VERIFIED);
    if (authorVerifiedAt == null) {
      throw new IllegalStateException("A version must be author-verified before submission");
    }
    this.status = ReportVersionStatus.SUBMITTED;
  }

  /** MF4-17: a return always carries a reason so the team knows what to correct. */
  public void returnToAuthor(String reason) {
    requireStatus(ReportVersionStatus.SUBMITTED);
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("A return requires a reason");
    }
    this.status = ReportVersionStatus.RETURNED;
  }

  public void supersede() {
    requireStatus(ReportVersionStatus.APPROVED);
    this.status = ReportVersionStatus.SUPERSEDED;
  }

  public boolean isImmutable() {
    return status == ReportVersionStatus.SUPERSEDED;
  }

  private void requireStatus(ReportVersionStatus... allowed) {
    for (ReportVersionStatus candidate : allowed) {
      if (status == candidate) {
        return;
      }
    }
    throw new IllegalStateException(
        "Report version " + versionNo + " is " + status + "; expected one of " + allowed);
  }

  private static UUID require(UUID value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  private static int requirePositive(int value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkOrderId() {
    return workOrderId;
  }

  public int getVersionNo() {
    return versionNo;
  }

  public ReportVersionStatus getStatus() {
    return status;
  }

  public UUID getAuthorUserId() {
    return authorUserId;
  }

  public Instant getAuthorVerifiedAt() {
    return authorVerifiedAt;
  }

  public String getLlmProvider() {
    return llmProvider;
  }

  public String getApprovedScopeHash() {
    return approvedScopeHash;
  }

  public String getChangeSnapshotHash() {
    return changeSnapshotHash;
  }

  public String getWorkLogSnapshotHash() {
    return workLogSnapshotHash;
  }

  public String getActualCostSnapshotHash() {
    return actualCostSnapshotHash;
  }

  public String getContentSnapshot() {
    return contentSnapshot;
  }
}

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
@Table(name = "maintenance_reports")
public class MaintenanceReportVersion {

  @Id @GeneratedValue private UUID id;

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Column(name = "version_no", nullable = false)
  private int versionNo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ReportVersionStatus status;

  @Column(name = "author_verified_at")
  private Instant authorVerifiedAt;

  @Column(name = "content_snapshot", nullable = false, columnDefinition = "text")
  private String contentSnapshot;

  @Column(name = "rendered_object_key", length = 1000)
  private String renderedObjectKey;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "rendered_checksum_sha256", length = 64)
  private String renderedChecksumSha256;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MaintenanceReportVersion() {}

  public MaintenanceReportVersion(UUID workOrderId, int versionNo, String contentSnapshot) {
    this.workOrderId = require(workOrderId, "workOrderId");
    this.versionNo = requirePositive(versionNo, "versionNo");
    this.contentSnapshot = requireText(contentSnapshot, "contentSnapshot");
    this.status = ReportVersionStatus.DRAFT;
    this.createdAt = Instant.now();
  }

  /** MF4-16: only the designated author may verify the report against the team's records. */
  public void verifyAsAuthor(UUID verifyingUserId, UUID designatedAuthorUserId) {
    requireStatus(ReportVersionStatus.DRAFT, ReportVersionStatus.RETURNED);
    UUID actor = require(verifyingUserId, "verifyingUserId");
    UUID author = require(designatedAuthorUserId, "designatedAuthorUserId");
    if (!author.equals(actor)) {
      throw new IllegalStateException("Only the report author may verify this version");
    }
    this.authorVerifiedAt = Instant.now();
    this.status = ReportVersionStatus.AUTHOR_VERIFIED;
  }

  public void editContent(String newContent) {
    requireStatus(ReportVersionStatus.DRAFT, ReportVersionStatus.RETURNED);
    this.contentSnapshot = requireText(newContent, "Report content");
  }

  /** MF4-17: submission hands the version to the independent reviewer. */
  public void submit() {
    requireStatus(ReportVersionStatus.AUTHOR_VERIFIED);
    if (authorVerifiedAt == null) {
      throw new IllegalStateException("A version must be author-verified before submission");
    }
    this.status = ReportVersionStatus.SUBMITTED;
  }

  /**
   * MF4-18: recording an acceptance also approves the report version it was made against, so the
   * accepted artifact is the one the reviewer actually read.
   */
  public void approveReport() {
    requireStatus(ReportVersionStatus.SUBMITTED);
    this.status = ReportVersionStatus.APPROVED;
  }

  /** MF4-17: a return always carries a reason so the team knows what to correct. */
  public void returnToAuthor(String reason) {
    requireStatus(ReportVersionStatus.SUBMITTED);
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("A return requires a reason");
    }
    this.status = ReportVersionStatus.RETURNED;
  }

  /**
   * MF4-18: When an independent re-inspection flight verifies the repaired work, the completion
   * report is reinstated to SUBMITTED so the customer/reviewer can record final acceptance.
   */
  public void resubmitForReinspectionAcceptance() {
    requireStatus(ReportVersionStatus.RETURNED);
    this.status = ReportVersionStatus.SUBMITTED;
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

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
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

  public Instant getAuthorVerifiedAt() {
    return authorVerifiedAt;
  }

  public String getContentSnapshot() {
    return contentSnapshot;
  }

  /** Records the stored PDF artifact key and SHA-256 checksum for an approved completion report. */
  public void recordArtefact(String objectKey, String checksumSha256) {
    requireStatus(ReportVersionStatus.APPROVED);
    if (objectKey == null
        || objectKey.isBlank()
        || checksumSha256 == null
        || checksumSha256.isBlank()) {
      throw new IllegalArgumentException("An approved report requires its object key and checksum");
    }
    this.renderedObjectKey = objectKey;
    this.renderedChecksumSha256 = checksumSha256;
  }

  public String getRenderedObjectKey() {
    return renderedObjectKey;
  }

  public String getRenderedChecksumSha256() {
    return renderedChecksumSha256;
  }
}

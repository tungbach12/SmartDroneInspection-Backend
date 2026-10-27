package com.smartdroneinspection.inspections.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The inspection report's human gates and published-artifact immutability. */
class InspectionReportTest {

  private final UUID reportId = UUID.randomUUID();
  private final UUID author = UUID.randomUUID();
  private final UUID reviewer = UUID.randomUUID();

  private InspectionReport draft() {
    return new InspectionReport(reportId, 1, "{}");
  }

  private void verifyAndSubmit(InspectionReport version) {
    version.verifyAsAuthor(author, author);
    version.submit();
  }

  @Test
  void newVersionStartsAsDraft() {
    assertThat(draft().getStatus()).isEqualTo(ReportStatus.DRAFT);
  }

  @Test
  void onlyTheParentReportAuthorMayVerifyThenSubmit() {
    InspectionReport version = draft();

    assertThatThrownBy(() -> version.verifyAsAuthor(reviewer, author))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only the report author");

    verifyAndSubmit(version);

    assertThat(version.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
    assertThat(version.getSubmittedAt()).isNotNull();
  }

  @Test
  void cannotSubmitBeforeAuthorVerification() {
    assertThatThrownBy(draft()::submit).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void reviewRequiresAReviewer() {
    InspectionReport version = draft();
    verifyAndSubmit(version);

    assertThatThrownBy(() -> version.reviewBy(null, true, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reviewer is required");
  }

  @Test
  void reviewerApprovalMovesToApproved() {
    InspectionReport version = draft();
    verifyAndSubmit(version);

    version.reviewBy(reviewer, true, null);

    assertThat(version.getStatus()).isEqualTo(ReportStatus.APPROVED);
    assertThat(version.getReviewerUserId()).isEqualTo(reviewer);
    assertThat(version.getReviewedAt()).isNotNull();
    assertThat(version.getReturnReason()).isNull();
  }

  @Test
  void returningADraftRequiresAReasonAndRecordsReturnReason() {
    InspectionReport version = draft();
    verifyAndSubmit(version);

    assertThatThrownBy(() -> version.reviewBy(reviewer, false, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("return requires a reason");
    assertThatThrownBy(() -> version.reviewBy(reviewer, false, "  "))
        .isInstanceOf(IllegalArgumentException.class);

    version.reviewBy(reviewer, false, "Coverage of the west face is missing");

    assertThat(version.getStatus()).isEqualTo(ReportStatus.RETURNED);
    assertThat(version.getReturnReason()).contains("west face");
  }

  @Test
  void renewedAuthorVerificationResetsThePreviousReviewDecision() {
    InspectionReport version = draft();
    verifyAndSubmit(version);
    version.reviewBy(reviewer, false, "Missing limitations");

    version.editContent("{\"narrative\":\"revised with disclosed limitations\"}");
    version.verifyAsAuthor(author, author);

    assertThat(version.getReviewerUserId()).isNull();
    assertThat(version.getReviewedAt()).isNull();
    assertThat(version.getReturnReason()).isNull();
    version.submit();
    assertThat(version.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
    assertThat(version.getSubmittedAt()).isNotNull();
  }

  @Test
  void authorCanWithdrawOnlyBeforeReviewerActs() {
    InspectionReport version = draft();
    verifyAndSubmit(version);

    version.withdraw(author, author);

    assertThat(version.getStatus()).isEqualTo(ReportStatus.DRAFT);
    assertThat(version.getSubmittedAt()).isNull();
  }

  @Test
  void publicationRequiresAnApprovedVersion() {
    assertThatThrownBy(() -> draft().publish(reviewer, author))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void publishedVersionKeepsItsPdfKeyAndChecksumWhenSuperseded() {
    InspectionReport version = draft();
    verifyAndSubmit(version);
    version.reviewBy(reviewer, true, null);
    version.publish(reviewer, author);
    version.recordArtefact("reports/report-v1.pdf", "a".repeat(64));

    assertThat(version.getStatus()).isEqualTo(ReportStatus.PUBLISHED);
    assertThat(version.getPublishedAt()).isNotNull();
    assertThat(version.isImmutable()).isTrue();
    assertThat(version.getRenderedObjectKey()).isEqualTo("reports/report-v1.pdf");
    assertThat(version.getRenderedChecksumSha256()).isEqualTo("a".repeat(64));

    assertThatThrownBy(() -> version.verifyAsAuthor(author, author))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(version::submit).isInstanceOf(IllegalStateException.class);

    version.supersede();
    assertThat(version.getStatus()).isEqualTo(ReportStatus.SUPERSEDED);
    assertThat(version.getRenderedObjectKey()).isEqualTo("reports/report-v1.pdf");
    assertThat(version.getRenderedChecksumSha256()).isEqualTo("a".repeat(64));
  }

  @Test
  void reviewIsRejectedWhenTheVersionIsNotSubmitted() {
    assertThatThrownBy(() -> draft().reviewBy(reviewer, true, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("expected one of");
  }
}

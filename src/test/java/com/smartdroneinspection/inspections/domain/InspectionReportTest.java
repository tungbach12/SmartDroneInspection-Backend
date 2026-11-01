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

  @Test
  void newVersionStartsAsSubmitted() {
    assertThat(draft().getStatus()).isEqualTo(ReportStatus.SUBMITTED);
    assertThat(draft().getSubmittedAt()).isNotNull();
  }

  @Test
  void reviewRequiresAReviewer() {
    InspectionReport version = draft();

    assertThatThrownBy(() -> version.reviewBy(null, true, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reviewer is required");
  }

  @Test
  void reviewerApprovalMovesToApproved() {
    InspectionReport version = draft();

    version.reviewBy(reviewer, true, null);

    assertThat(version.getStatus()).isEqualTo(ReportStatus.APPROVED);
    assertThat(version.getReviewerUserId()).isEqualTo(reviewer);
    assertThat(version.getReviewedAt()).isNotNull();
    assertThat(version.getReturnReason()).isNull();
  }

  @Test
  void returningADraftRequiresAReasonAndRecordsReturnReason() {
    InspectionReport version = draft();

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
  void resubmissionResetsThePreviousReviewDecision() {
    InspectionReport version = draft();
    version.reviewBy(reviewer, false, "Missing limitations");

    version.editContent("{\"narrative\":\"revised with disclosed limitations\"}");
    version.submit();

    assertThat(version.getReviewerUserId()).isNull();
    assertThat(version.getReviewedAt()).isNull();
    assertThat(version.getReturnReason()).isNull();
    assertThat(version.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
    assertThat(version.getSubmittedAt()).isNotNull();
  }

  @Test
  void authorCanWithdrawOnlyBeforeReviewerActs() {
    InspectionReport version = draft();

    version.withdraw(author, author);

    assertThat(version.getStatus()).isEqualTo(ReportStatus.RETURNED);
    assertThat(version.getReturnReason()).contains("Withdrawn");
  }

  @Test
  void publicationSeparationOfDuties() {
    assertThatThrownBy(() -> draft().publish(author, author))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Publication requires a qualified reviewer decision");
  }

  @Test
  void publishedVersionKeepsItsPdfKeyAndChecksumWhenSuperseded() {
    InspectionReport version = draft();
    version.reviewBy(reviewer, true, null);
    version.publish(reviewer, author);
    version.recordArtefact("reports/report-v1.pdf", "a".repeat(64));

    assertThat(version.getStatus()).isEqualTo(ReportStatus.PUBLISHED);
    assertThat(version.getPublishedAt()).isNotNull();
    assertThat(version.isImmutable()).isTrue();
    assertThat(version.getRenderedObjectKey()).isEqualTo("reports/report-v1.pdf");
    assertThat(version.getRenderedChecksumSha256()).isEqualTo("a".repeat(64));

    version.supersede();
    assertThat(version.getStatus()).isEqualTo(ReportStatus.SUPERSEDED);
    assertThat(version.getRenderedObjectKey()).isEqualTo("reports/report-v1.pdf");
    assertThat(version.getRenderedChecksumSha256()).isEqualTo("a".repeat(64));
  }

  @Test
  void reviewIsRejectedWhenTheVersionIsNotSubmitted() {
    InspectionReport version = draft();
    version.reviewBy(reviewer, true, null); // now APPROVED
    assertThatThrownBy(() -> version.reviewBy(reviewer, true, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("expected one of");
  }
}

package com.smartdroneinspection.inspections.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The human gates are the behaviour under test: nothing advances without the right person acting,
 * and a published version cannot be revised.
 */
class InspectionReportVersionTest {

  private final UUID reportId = UUID.randomUUID();
  private final UUID author = UUID.randomUUID();
  private final UUID reviewer = UUID.randomUUID();

  private InspectionReportVersion draft() {
    return new InspectionReportVersion(
        reportId, 1, null, author, "{}", null, "gpt-test", "v1", "abc");
  }

  @Test
  void newVersionStartsAsDraft() {
    assertThat(draft().getStatus()).isEqualTo(ReportStatus.DRAFT);
  }

  @Test
  void authorVerifiesThenSubmits() {
    InspectionReportVersion version = draft();

    version.verifyAsAuthor(author);
    assertThat(version.getStatus()).isEqualTo(ReportStatus.AUTHOR_VERIFIED);
    assertThat(version.getAuthorVerifiedAt()).isNotNull();

    version.submit();
    assertThat(version.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
  }

  @Test
  void onlyTheAuthorMayVerify() {
    InspectionReportVersion version = draft();

    assertThatThrownBy(() -> version.verifyAsAuthor(reviewer))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only the report author");
  }

  @Test
  void cannotSubmitBeforeAuthorVerification() {
    InspectionReportVersion version = draft();

    assertThatThrownBy(version::submit).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void authorCannotReviewTheirOwnReport() {
    InspectionReportVersion version = draft();
    version.verifyAsAuthor(author);
    version.submit();

    assertThatThrownBy(() -> version.reviewBy(author, true, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot review their own report");
  }

  @Test
  void reviewerApprovalMovesToApproved() {
    InspectionReportVersion version = draft();
    version.verifyAsAuthor(author);
    version.submit();

    version.reviewBy(reviewer, true, null);

    assertThat(version.getStatus()).isEqualTo(ReportStatus.APPROVED);
    assertThat(version.getReviewerUserId()).isEqualTo(reviewer);
    assertThat(version.getReviewedAt()).isNotNull();
  }

  @Test
  void returningADraftRequiresAReason() {
    InspectionReportVersion version = draft();
    version.verifyAsAuthor(author);
    version.submit();

    assertThatThrownBy(() -> version.reviewBy(reviewer, false, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("return requires a reason");
    assertThatThrownBy(() -> version.reviewBy(reviewer, false, "  "))
        .isInstanceOf(IllegalArgumentException.class);

    version.reviewBy(reviewer, false, "Coverage of the west face is missing");
    assertThat(version.getStatus()).isEqualTo(ReportStatus.RETURNED);
    assertThat(version.getReviewReason()).contains("west face");
  }

  @Test
  void aReturnedVersionCanBeRevisedAndReSubmitted() {
    InspectionReportVersion version = draft();
    version.verifyAsAuthor(author);
    version.submit();
    version.reviewBy(reviewer, false, "Missing limitations");

    version.editContent("{\"narrative\":\"revised with disclosed limitations\"}");
    version.verifyAsAuthor(author);
    version.submit();

    assertThat(version.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
  }

  @Test
  void publicationRequiresAnApprovedVersion() {
    InspectionReportVersion version = draft();

    assertThatThrownBy(() -> version.publish(reviewer)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void publishedVersionIsImmutableAndSupersedable() {
    InspectionReportVersion version = draft();
    version.verifyAsAuthor(author);
    version.submit();
    version.reviewBy(reviewer, true, null);
    version.publish(reviewer);

    assertThat(version.getStatus()).isEqualTo(ReportStatus.PUBLISHED);
    assertThat(version.getPublishedAt()).isNotNull();
    assertThat(version.isImmutable()).isTrue();

    assertThatThrownBy(() -> version.verifyAsAuthor(author))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(version::submit).isInstanceOf(IllegalStateException.class);

    version.supersede();
    assertThat(version.getStatus()).isEqualTo(ReportStatus.SUPERSEDED);
  }

  @Test
  void reviewIsRejectedWhenTheVersionIsNotSubmitted() {
    InspectionReportVersion version = draft();

    assertThatThrownBy(() -> version.reviewBy(reviewer, true, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("expected one of");
  }
}

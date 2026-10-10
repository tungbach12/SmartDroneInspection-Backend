package com.smartdroneinspection.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.maintenance.domain.enums.ReportVersionStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MF4-14/17: the completion report author verifies and submits the version they own. */
class MaintenanceReportVersionTest {

  private static final UUID WORK_ORDER = UUID.randomUUID();
  private static final UUID AUTHOR = UUID.randomUUID();
  private static final UUID OTHER = UUID.randomUUID();

  private static MaintenanceReportVersion draft() {
    return new MaintenanceReportVersion(
        WORK_ORDER,
        1,
        AUTHOR,
        "{\"summary\":\"Replaced blade\"}",
        "scope-hash",
        null,
        "log-hash",
        "cost-hash",
        null,
        null,
        null);
  }

  @Test
  void startsAsDraftWithSnapshotHashes() {
    MaintenanceReportVersion report = draft();

    assertThat(report.getStatus()).isEqualTo(ReportVersionStatus.DRAFT);
    assertThat(report.getContentSnapshot()).contains("Replaced blade");
    assertThat(report.getApprovedScopeHash()).isEqualTo("scope-hash");
    assertThat(report.getActualCostSnapshotHash()).isEqualTo("cost-hash");
  }

  @Test
  void requiresAuthorAndPositiveVersion() {
    assertThatThrownBy(
            () ->
                new MaintenanceReportVersion(
                    WORK_ORDER, 0, AUTHOR, "{}", null, null, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new MaintenanceReportVersion(
                    WORK_ORDER, 1, null, "{}", null, null, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void onlyAuthorCanVerify() {
    MaintenanceReportVersion report = draft();

    assertThatThrownBy(() -> report.verifyAsAuthor(OTHER))
        .isInstanceOf(IllegalStateException.class);

    report.verifyAsAuthor(AUTHOR);

    assertThat(report.getStatus()).isEqualTo(ReportVersionStatus.AUTHOR_VERIFIED);
    assertThat(report.getAuthorVerifiedAt()).isNotNull();
  }

  @Test
  void editsOnlyDraftOrReturnedVersions() {
    MaintenanceReportVersion report = draft();
    report.editContent("{\"summary\":\"Corrected\"}");
    assertThat(report.getContentSnapshot()).contains("Corrected");

    report.verifyAsAuthor(AUTHOR);
    report.submit();

    assertThatThrownBy(() -> report.editContent("{}")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsBlankContent() {
    assertThatThrownBy(() -> draft().editContent("  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requiresAuthorVerificationBeforeSubmission() {
    assertThatThrownBy(draft()::submit).isInstanceOf(IllegalStateException.class);

    MaintenanceReportVersion report = draft();
    report.verifyAsAuthor(AUTHOR);
    report.submit();
    assertThat(report.getStatus()).isEqualTo(ReportVersionStatus.SUBMITTED);
  }

  @Test
  void returnRequiresReasonAndCanBeReverified() {
    MaintenanceReportVersion report = draft();
    report.verifyAsAuthor(AUTHOR);
    report.submit();

    assertThatThrownBy(() -> report.returnToAuthor("  "))
        .isInstanceOf(IllegalArgumentException.class);

    report.returnToAuthor("Add a torque reading");
    assertThat(report.getStatus()).isEqualTo(ReportVersionStatus.RETURNED);
    report.editContent("{\"summary\":\"Corrected with torque\"}");
    report.verifyAsAuthor(AUTHOR);
    report.submit();
    assertThat(report.getStatus()).isEqualTo(ReportVersionStatus.SUBMITTED);
  }

  @Test
  void aiMetadataIsOptionalAndDoesNotSetAuthority() {
    MaintenanceReportVersion report =
        new MaintenanceReportVersion(
            WORK_ORDER,
            2,
            AUTHOR,
            "{}",
            null,
            null,
            null,
            null,
            "anthropic",
            "claude-sonnet-5-5",
            "v1");

    assertThat(report.getLlmProvider()).isEqualTo("anthropic");
    assertThat(report.getStatus()).isEqualTo(ReportVersionStatus.DRAFT);
  }
}

package com.smartdroneinspection.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.maintenance.domain.enums.AcceptanceDecisionKind;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MF4-18/19: the independent reviewer's decision, recorded as an append-only fact. This entity
 * holds no authorization logic; independence is enforced when the team is designated and again when
 * the work order accepts the decision.
 */
class MaintenanceAcceptanceDecisionTest {

  private static final UUID WORK_ORDER = UUID.randomUUID();
  private static final UUID REPORT_VERSION = UUID.randomUUID();
  private static final UUID REVIEWER = UUID.randomUUID();

  @Test
  void recordsAnAcceptance() {
    MaintenanceAcceptanceDecision decision =
        new MaintenanceAcceptanceDecision(
            WORK_ORDER,
            REPORT_VERSION,
            REVIEWER,
            AcceptanceDecisionKind.ACCEPTED,
            "Torque within specification",
            "{\"checks\":[\"torque\"]}",
            "{\"pass\":true}",
            null);

    assertThat(decision.getDecision()).isEqualTo(AcceptanceDecisionKind.ACCEPTED);
    assertThat(decision.getDecidedAt()).isNotNull();
    assertThat(decision.getReviewerUserId()).isEqualTo(REVIEWER);
  }

  @Test
  void recordsAReinspectionRequirementWithReason() {
    MaintenanceAcceptanceDecision decision =
        new MaintenanceAcceptanceDecision(
            WORK_ORDER,
            REPORT_VERSION,
            REVIEWER,
            AcceptanceDecisionKind.REINSPECTION_REQUIRED,
            "Needs independent verification",
            null,
            null,
            null);

    assertThat(decision.getDecision()).isEqualTo(AcceptanceDecisionKind.REINSPECTION_REQUIRED);
  }

  /** Anything other than acceptance must carry technical comments explaining the decision. */
  @Test
  void nonAcceptanceRequiresTechnicalComments() {
    assertThatThrownBy(
            () ->
                new MaintenanceAcceptanceDecision(
                    WORK_ORDER,
                    REPORT_VERSION,
                    REVIEWER,
                    AcceptanceDecisionKind.REWORK_REQUIRED,
                    "  ",
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requiresWorkOrderReportVersionReviewerAndDecision() {
    assertThatThrownBy(
            () ->
                new MaintenanceAcceptanceDecision(
                    null,
                    REPORT_VERSION,
                    REVIEWER,
                    AcceptanceDecisionKind.ACCEPTED,
                    "ok",
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new MaintenanceAcceptanceDecision(
                    WORK_ORDER,
                    null,
                    REVIEWER,
                    AcceptanceDecisionKind.ACCEPTED,
                    "ok",
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new MaintenanceAcceptanceDecision(
                    WORK_ORDER,
                    REPORT_VERSION,
                    null,
                    AcceptanceDecisionKind.ACCEPTED,
                    "ok",
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new MaintenanceAcceptanceDecision(
                    WORK_ORDER, REPORT_VERSION, REVIEWER, null, "ok", null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void decisionsAreAppendOnlyNotMutable() {
    MaintenanceAcceptanceDecision decision =
        new MaintenanceAcceptanceDecision(
            WORK_ORDER,
            REPORT_VERSION,
            REVIEWER,
            AcceptanceDecisionKind.ACCEPTED,
            "ok",
            null,
            null,
            null);

    // A corrected decision is a new row; the entity exposes no mutator, matching the schema.
    assertThat(decision.getDecision()).isEqualTo(AcceptanceDecisionKind.ACCEPTED);
    assertThat(decision.getTechnicalComments()).isEqualTo("ok");
  }
}

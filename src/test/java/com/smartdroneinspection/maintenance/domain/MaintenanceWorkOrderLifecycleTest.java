package com.smartdroneinspection.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.maintenance.domain.enums.WorkOrderStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MF4-09 to MF4-21: execution, completion, independent acceptance, reconciliation and closure.
 *
 * <p>The recurring theme is that completion is not acceptance, and that the executing team can
 * never close its own work.
 */
class MaintenanceWorkOrderLifecycleTest {

  private static final UUID OWNER = UUID.randomUUID();
  private static final UUID APPROVER = UUID.randomUUID();
  private static final UUID LEAD = UUID.randomUUID();
  private static final UUID AUTHOR = UUID.randomUUID();
  private static final UUID REVIEWER = UUID.randomUUID();

  /** An approved, released work order ready to start. */
  private static MaintenanceWorkOrder approved() {
    MaintenanceWorkOrder order =
        new MaintenanceWorkOrder(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            OWNER,
            APPROVER);
    order.assignTeam(LEAD, AUTHOR, REVIEWER);
    order.submitForApproval();
    order.approveEstimate(APPROVER);
    return order;
  }

  /** A work order whose team has finished the physical work. */
  private static MaintenanceWorkOrder awaitingAcceptance() {
    MaintenanceWorkOrder order = approved();
    order.markReady();
    order.markInProgress();
    order.markWorkCompleted();
    order.markSubmittedForAcceptance();
    return order;
  }

  @Test
  void runsFromApprovedToSubmissionForAcceptance() {
    MaintenanceWorkOrder order = approved();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.APPROVED);

    order.markReady();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.READY);

    order.markInProgress();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);

    order.markWorkCompleted();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.WORK_COMPLETED);

    order.markSubmittedForAcceptance();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.SUBMITTED_FOR_ACCEPTANCE);
  }

  @Test
  void skipsExecutionStatesInTheWrongOrder() {
    MaintenanceWorkOrder order = approved();

    assertThatThrownBy(order::markInProgress).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(order::markWorkCompleted).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(order::markSubmittedForAcceptance).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void independentReviewerAccepts() {
    MaintenanceWorkOrder order = awaitingAcceptance();

    order.accept(REVIEWER);

    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.ACCEPTED);
  }

  /**
   * MF4-18: completion is not acceptance. Because designation already refuses a reviewer inside the
   * team, only the designated reviewer reaches the acceptance guard at all.
   */
  @Test
  void executingTeamCannotAccept() {
    MaintenanceWorkOrder order = awaitingAcceptance();

    assertThatThrownBy(() -> order.accept(LEAD))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("designated independent reviewer");
    assertThatThrownBy(() -> order.accept(AUTHOR)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void onlyTheDesignatedReviewerMayDecide() {
    MaintenanceWorkOrder order = awaitingAcceptance();

    assertThatThrownBy(() -> order.accept(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("designated independent reviewer");
  }

  @Test
  void reviewerReturnsWorkForReworkWithAReason() {
    MaintenanceWorkOrder order = awaitingAcceptance();

    order.requireReworkAfterAcceptance(REVIEWER, "Torque not recorded");

    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.REWORK_REQUIRED);
  }

  @Test
  void reworkDecisionRequiresAReason() {
    MaintenanceWorkOrder order = awaitingAcceptance();

    assertThatThrownBy(() -> order.requireReworkAfterAcceptance(REVIEWER, "  "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> order.requireReinspection(REVIEWER, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** Rework after acceptance resumes execution, not approval: scope was already approved. */
  @Test
  void reworkResumesExecution() {
    MaintenanceWorkOrder order = awaitingAcceptance();
    order.requireReworkAfterAcceptance(REVIEWER, "Torque not recorded");

    order.resumeExecutionAfterRework();
    order.markWorkCompleted();
    order.markSubmittedForAcceptance();
    order.accept(REVIEWER);

    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.ACCEPTED);
  }

  @Test
  void reviewerRequestsReinspection() {
    MaintenanceWorkOrder order = awaitingAcceptance();

    order.requireReinspection(REVIEWER, "Requires independent verification");

    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.REINSPECTION_REQUIRED);

    order.resumeAfterReinspection();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
  }

  @Test
  void reconcilesCostsThenCloses() {
    MaintenanceWorkOrder order = awaitingAcceptance();
    order.accept(REVIEWER);

    order.markCostsReconciled();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.COST_RECONCILED);

    order.close();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.CLOSED);
  }

  /** MF4-21: closure requires reconciliation first, so acceptance alone cannot close an order. */
  @Test
  void cannotCloseBeforeReconciliation() {
    MaintenanceWorkOrder order = awaitingAcceptance();
    order.accept(REVIEWER);

    assertThatThrownBy(order::close).isInstanceOf(IllegalStateException.class);
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.ACCEPTED);
  }

  @Test
  void cannotReconcileBeforeAcceptance() {
    MaintenanceWorkOrder order = awaitingAcceptance();

    assertThatThrownBy(order::markCostsReconciled).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void closedOrderAcceptsNoFurtherAction() {
    MaintenanceWorkOrder order = awaitingAcceptance();
    order.accept(REVIEWER);
    order.markCostsReconciled();
    order.close();

    assertThatThrownBy(order::close).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(order::markCostsReconciled).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(order::markReady).isInstanceOf(IllegalStateException.class);
  }
}

package com.smartdroneinspection.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.maintenance.domain.enums.CustomerMaintenanceDecision;
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
    order.markWorkCompleted(1, 1);
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

    order.markWorkCompleted(1, 1);
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.WORK_COMPLETED);

    order.markSubmittedForAcceptance();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.AWAITING_CUSTOMER_ACCEPTANCE);
  }

  @Test
  void cannotDeclareWorkCompletedUntilSubmittedLogsAreVerified() {
    MaintenanceWorkOrder order = approved();
    order.markReady();
    order.markInProgress();

    assertThatThrownBy(() -> order.markWorkCompleted(1, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("verified");

    order.markWorkCompleted(1, 1);
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.WORK_COMPLETED);
  }

  @Test
  void skipsExecutionStatesInTheWrongOrder() {
    MaintenanceWorkOrder order = approved();

    assertThatThrownBy(order::markInProgress).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> order.markWorkCompleted(0, 0))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(order::markSubmittedForAcceptance).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void customerOwnerAcceptsCompletedWork() {
    MaintenanceWorkOrder order = awaitingAcceptance();

    order.accept(OWNER);

    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.ACCEPTED);
  }

  @Test
  void executingTeamCannotAcceptWork() {
    MaintenanceWorkOrder order = awaitingAcceptance();

    assertThatThrownBy(() -> order.accept(LEAD))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("work-order customer");
    assertThatThrownBy(() -> order.accept(AUTHOR)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void onlyTheWorkOrderOwnerMayAccept() {
    MaintenanceWorkOrder order = awaitingAcceptance();

    assertThatThrownBy(() -> order.accept(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("work-order customer");
  }

  @Test
  void legacyReviewerReworkDecisionRequiresReason() {
    MaintenanceWorkOrder order = awaitingAcceptance();

    assertThatThrownBy(() -> order.requireReworkAfterAcceptance(REVIEWER, "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customerReturnReopensRepairWithAReason() {
    MaintenanceWorkOrder order = awaitingAcceptance();
    order.recordCustomerDecision(
        CustomerMaintenanceDecision.REPAIR_REQUIRED, "Torque not recorded");

    order.resumeSupplementWork();
    order.markWorkCompleted(1, 1);
    order.markSubmittedForAcceptance();
    order.accept(OWNER);

    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.ACCEPTED);
  }

  @Test
  void customerRequestAndDecisionDriveReinspectionLifecycle() {
    MaintenanceWorkOrder order = awaitingAcceptance();

    order.requestReinspection();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.AWAITING_REINSPECTION);

    order.markReinspectionAssigned();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.REINSPECTION_IN_PROGRESS);

    order.markAwaitingReinspectionResult();
    order.recordCustomerDecision(
        CustomerMaintenanceDecision.REPAIR_REQUIRED, "The finding remains visible");
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.REPAIR_SUPPLEMENT_REQUIRED);

    order.resumeSupplementWork();
    order.markWorkCompleted(1, 1);
    order.markSubmittedForAcceptance();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.AWAITING_CUSTOMER_ACCEPTANCE);
  }

  @Test
  void reconcilesCostsThenCloses() {
    MaintenanceWorkOrder order = awaitingAcceptance();
    order.accept(OWNER);

    order.markCostsReconciled(OWNER);
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.COST_RECONCILED);

    order.close();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.CLOSED);
  }

  /** MF4-21: closure requires reconciliation first, so acceptance alone cannot close an order. */
  @Test
  void cannotCloseBeforeReconciliation() {
    MaintenanceWorkOrder order = awaitingAcceptance();
    order.accept(OWNER);

    assertThatThrownBy(order::close).isInstanceOf(IllegalStateException.class);
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.ACCEPTED);
  }

  @Test
  void cannotReconcileBeforeAcceptance() {
    MaintenanceWorkOrder order = awaitingAcceptance();

    assertThatThrownBy(() -> order.markCostsReconciled(OWNER))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void closedOrderAcceptsNoFurtherAction() {
    MaintenanceWorkOrder order = awaitingAcceptance();
    order.accept(OWNER);
    order.markCostsReconciled(OWNER);
    order.close();

    assertThatThrownBy(order::close).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> order.markCostsReconciled(OWNER))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(order::markReady).isInstanceOf(IllegalStateException.class);
  }
}

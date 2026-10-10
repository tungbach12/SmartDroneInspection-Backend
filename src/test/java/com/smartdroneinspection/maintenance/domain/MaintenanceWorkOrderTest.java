package com.smartdroneinspection.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.maintenance.domain.enums.MaintenancePriority;
import com.smartdroneinspection.maintenance.domain.enums.WorkOrderStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MF4-01/02: work order creation, triage and the approval gate up to MF4-08. */
class MaintenanceWorkOrderTest {

  private static final UUID ORG = UUID.randomUUID();
  private static final UUID ASSET = UUID.randomUUID();
  private static final UUID REPORT_VERSION = UUID.randomUUID();
  private static final UUID FINDING = UUID.randomUUID();
  private static final UUID OWNER = UUID.randomUUID();
  private static final UUID APPROVER = UUID.randomUUID();
  private static final UUID LEAD = UUID.randomUUID();
  private static final UUID AUTHOR = UUID.randomUUID();
  private static final UUID REVIEWER = UUID.randomUUID();

  private static MaintenanceWorkOrder workOrder() {
    return new MaintenanceWorkOrder(ORG, ASSET, REPORT_VERSION, FINDING, OWNER, APPROVER);
  }

  @Test
  void createsDraftFromPublishedRepairRequiredFinding() {
    MaintenanceWorkOrder order = workOrder();

    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.DRAFT);
    assertThat(order.getOrganizationId()).isEqualTo(ORG);
    assertThat(order.getSourceFindingId()).isEqualTo(FINDING);
    assertThat(order.getAcceptingReviewerUserId()).isNull();
    assertThat(order.getTeamLeadUserId()).isNull();
  }

  @Test
  void requiresEverySourceIdentity() {
    assertThatThrownBy(
            () -> new MaintenanceWorkOrder(null, ASSET, REPORT_VERSION, FINDING, OWNER, APPROVER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new MaintenanceWorkOrder(ORG, null, REPORT_VERSION, FINDING, OWNER, APPROVER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MaintenanceWorkOrder(ORG, ASSET, null, FINDING, OWNER, APPROVER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new MaintenanceWorkOrder(ORG, ASSET, REPORT_VERSION, null, OWNER, APPROVER))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void recordsTriageOnDraftOnly() {
    MaintenanceWorkOrder order = workOrder();

    order.triage(
        MaintenancePriority.URGENT,
        Instant.now().plusSeconds(3600),
        "{\"summary\":\"x\"}",
        "{\"checks\":[]}");

    assertThat(order.getPriority()).isEqualTo(MaintenancePriority.URGENT);
    assertThat(order.getDueAt()).isNotNull();
    assertThat(order.getCorrectiveScope()).isEqualTo("{\"summary\":\"x\"}");
    assertThat(order.getAcceptanceCriteria()).isEqualTo("{\"checks\":[]}");
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.DRAFT);
  }

  @Test
  void rejectsTriageOnceApprovalWasRequested() {
    MaintenanceWorkOrder order = workOrder();
    order.assignTeam(LEAD, AUTHOR, REVIEWER);
    order.submitForApproval();

    assertThatThrownBy(() -> order.triage(MaintenancePriority.LOW, null, "{}", "[]"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void designatesAllThreeTeamRoles() {
    MaintenanceWorkOrder order = workOrder();

    order.assignTeam(LEAD, AUTHOR, REVIEWER);

    assertThat(order.getTeamLeadUserId()).isEqualTo(LEAD);
    assertThat(order.getReportAuthorUserId()).isEqualTo(AUTHOR);
    assertThat(order.getAcceptingReviewerUserId()).isEqualTo(REVIEWER);
  }

  @Test
  void rejectsNullTeamRole() {
    MaintenanceWorkOrder order = workOrder();

    assertThatThrownBy(() -> order.assignTeam(null, AUTHOR, REVIEWER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> order.assignTeam(LEAD, null, REVIEWER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> order.assignTeam(LEAD, AUTHOR, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * Separation of duties: the executing team cannot accept its own work. Report 3 §3.8.1 makes this
   * absolute, so it is a domain invariant rather than only a service check.
   */
  @Test
  void rejectsReviewerInsideTheExecutingTeam() {
    MaintenanceWorkOrder order = workOrder();

    assertThatThrownBy(() -> order.assignTeam(LEAD, AUTHOR, LEAD))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("independent");
    assertThatThrownBy(() -> order.assignTeam(LEAD, AUTHOR, AUTHOR))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsTeamDesignationOnceWorkStarted() {
    MaintenanceWorkOrder order = workOrder();
    order.assignTeam(LEAD, AUTHOR, REVIEWER);
    order.submitForApproval();

    assertThatThrownBy(() -> order.assignTeam(LEAD, AUTHOR, REVIEWER))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void requiresTeamBeforeApprovalRequest() {
    MaintenanceWorkOrder order = workOrder();

    assertThatThrownBy(order::submitForApproval)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("team");
  }

  @Test
  void movesToAwaitingApprovalThenApproved() {
    MaintenanceWorkOrder order = workOrder();
    order.assignTeam(LEAD, AUTHOR, REVIEWER);
    order.submitForApproval();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.AWAITING_APPROVAL);

    order.approveEstimate(APPROVER);

    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.APPROVED);
  }

  /** MF4-08: the budget approver must not have prepared the estimate they approve. */
  @Test
  void rejectsSelfApprovalByBudgetApprover() {
    MaintenanceWorkOrder order = workOrder();
    order.assignTeam(LEAD, AUTHOR, REVIEWER);
    order.submitForApproval();

    assertThatThrownBy(() -> order.approveEstimate(OWNER))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsApprovalFromExecutingTeamMember() {
    MaintenanceWorkOrder order = workOrder();
    order.assignTeam(LEAD, AUTHOR, REVIEWER);
    order.submitForApproval();

    assertThatThrownBy(() -> order.approveEstimate(AUTHOR))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void returnsApprovedWorkOrderForRework() {
    MaintenanceWorkOrder order = workOrder();
    order.assignTeam(LEAD, AUTHOR, REVIEWER);
    order.submitForApproval();
    order.approveEstimate(APPROVER);

    order.requestRework("Estimate incomplete");

    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.REWORK_REQUIRED);

    order.submitForApproval();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.AWAITING_APPROVAL);
  }

  @Test
  void budgetApproverMayReturnARejectedEstimateForRevision() {
    MaintenanceWorkOrder order = workOrder();
    order.assignTeam(LEAD, AUTHOR, REVIEWER);
    order.submitForApproval();

    order.returnEstimateForRework(APPROVER, "Estimate missing materials");

    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.REWORK_REQUIRED);
    order.submitForApproval();
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.AWAITING_APPROVAL);
  }

  /** MF4-08: the designated budget approver is still required after a rework cycle. */
  @Test
  void rejectsApprovalFromAnyoneOtherThanTheDesignatedApprover() {
    MaintenanceWorkOrder order = workOrder();
    order.assignTeam(LEAD, AUTHOR, REVIEWER);
    order.submitForApproval();

    assertThatThrownBy(() -> order.approveEstimate(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("designated budget approver");
  }

  @Test
  void rejectsRepeatedApprovalOnceApproved() {
    MaintenanceWorkOrder order = workOrder();
    order.assignTeam(LEAD, AUTHOR, REVIEWER);
    order.submitForApproval();
    order.approveEstimate(APPROVER);

    assertThatThrownBy(() -> order.approveEstimate(APPROVER))
        .isInstanceOf(IllegalStateException.class);
  }
}

package com.smartdroneinspection.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.maintenance.domain.enums.ChangeOrderStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MF4-12/13: additional work stays unauthorized until a decision is recorded. */
class MaintenanceChangeOrderTest {

  private static final UUID WORK_ORDER = UUID.randomUUID();
  private static final UUID LEAD = UUID.randomUUID();
  private static final UUID CUSTOMER = UUID.randomUUID();

  private static MaintenanceChangeOrder change(BigDecimal delta) {
    return new MaintenanceChangeOrder(WORK_ORDER, 1, "Hidden damage found", delta, LEAD);
  }

  @Test
  void startsAsDraft() {
    MaintenanceChangeOrder c = change(new BigDecimal("150000"));

    assertThat(c.getStatus()).isEqualTo(ChangeOrderStatus.DRAFT);
    assertThat(c.getProposedDelta()).isEqualByComparingTo("150000.00");
  }

  @Test
  void requiresReasonAndRequester() {
    assertThatThrownBy(() -> new MaintenanceChangeOrder(WORK_ORDER, 1, "  ", BigDecimal.ONE, LEAD))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MaintenanceChangeOrder(WORK_ORDER, 1, "Why", BigDecimal.ONE, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonPositiveChangeNumber() {
    assertThatThrownBy(() -> new MaintenanceChangeOrder(WORK_ORDER, 0, "Why", BigDecimal.ONE, LEAD))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void approvesAfterSubmission() {
    MaintenanceChangeOrder c = change(new BigDecimal("150000"));
    c.submit();
    assertThat(c.getStatus()).isEqualTo(ChangeOrderStatus.AWAITING_APPROVAL);

    c.approve(CUSTOMER, "Within contingency");

    assertThat(c.getStatus()).isEqualTo(ChangeOrderStatus.APPROVED);
    assertThat(c.getDecidedByUserId()).isEqualTo(CUSTOMER);
    assertThat(c.getDecidedAt()).isNotNull();
  }

  /** The whole point of change control: no decision recorded, no additional work. */
  @Test
  void cannotDecideWithoutSubmitting() {
    MaintenanceChangeOrder c = change(new BigDecimal("150000"));

    assertThatThrownBy(() -> c.approve(CUSTOMER, "Note")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> c.reject(CUSTOMER, "No")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> c.returnForClarification(CUSTOMER, "Explain"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectionRequiresAReason() {
    MaintenanceChangeOrder c = change(BigDecimal.ONE);
    c.submit();

    assertThatThrownBy(() -> c.reject(CUSTOMER, "  ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void returnedChangeCanBeResubmitted() {
    MaintenanceChangeOrder c = change(BigDecimal.ONE);
    c.submit();
    c.returnForClarification(CUSTOMER, "Quantify the additional hours");

    assertThat(c.getStatus()).isEqualTo(ChangeOrderStatus.RETURNED);

    c.submit();
    assertThat(c.getStatus()).isEqualTo(ChangeOrderStatus.AWAITING_APPROVAL);
  }

  @Test
  void cannotDecideTwice() {
    MaintenanceChangeOrder c = change(BigDecimal.ONE);
    c.submit();
    c.approve(CUSTOMER, "Approved");

    assertThatThrownBy(() -> c.reject(CUSTOMER, "Changed my mind"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> c.approve(CUSTOMER, "Again"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void supersedesOnlyAfterApproval() {
    MaintenanceChangeOrder draft = change(BigDecimal.ONE);
    assertThatThrownBy(draft::supersede).isInstanceOf(IllegalStateException.class);

    MaintenanceChangeOrder approved = change(BigDecimal.ONE);
    approved.submit();
    approved.approve(CUSTOMER, "Approved");
    approved.supersede();

    assertThat(approved.getStatus()).isEqualTo(ChangeOrderStatus.SUPERSEDED);
  }

  @Test
  void allowsANegativeDeltaToRecoverOverrun() {
    MaintenanceChangeOrder c = change(new BigDecimal("-50000"));

    assertThat(c.getProposedDelta()).isEqualByComparingTo("-50000.00");
  }
}

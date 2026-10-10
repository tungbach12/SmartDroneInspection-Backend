package com.smartdroneinspection.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.maintenance.domain.enums.CostLineKind;
import com.smartdroneinspection.maintenance.domain.enums.CostLineState;
import com.smartdroneinspection.maintenance.domain.enums.EstimateStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MF4-06/07/08: versioned estimate, SYSTEM totals, and an unfrozen baseline only before approval.
 */
class MaintenanceEstimateVersionTest {

  private static final UUID WORK_ORDER = UUID.randomUUID();
  private static final UUID LEAD = UUID.randomUUID();
  private static final UUID ORG_ADMIN = UUID.randomUUID();

  private static MaintenanceEstimateVersion version(BigDecimal total) {
    return new MaintenanceEstimateVersion(
        WORK_ORDER, 1, LEAD, "VND", null, null, "{\"lines\":[]}", total);
  }

  private static MaintenanceCostLine pricedLine(String quantity, String rate) {
    return new MaintenanceCostLine(
        WORK_ORDER,
        UUID.randomUUID(),
        null,
        CostLineKind.LABOR,
        CostLineState.ESTIMATE,
        "Work",
        new BigDecimal(quantity),
        "hour",
        new BigDecimal(rate),
        "VND",
        null,
        null,
        LEAD);
  }

  @Test
  void createsDraftVersion() {
    MaintenanceEstimateVersion v = version(new BigDecimal("1000000"));

    assertThat(v.getStatus()).isEqualTo(EstimateStatus.DRAFT);
    assertThat(v.getBaselineTotal()).isEqualByComparingTo("1000000.00");
    assertThat(v.isImmutable()).isFalse();
  }

  @Test
  void rejectsNegativeTotal() {
    assertThatThrownBy(() -> version(new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> version(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void submitsThenApproves() {
    MaintenanceEstimateVersion v = version(new BigDecimal("1000000"));
    v.submit();
    assertThat(v.getStatus()).isEqualTo(EstimateStatus.SUBMITTED);

    v.approve(ORG_ADMIN);

    assertThat(v.getStatus()).isEqualTo(EstimateStatus.APPROVED);
    assertThat(v.getApprovedByUserId()).isEqualTo(ORG_ADMIN);
    assertThat(v.getApprovedAt()).isNotNull();
    assertThat(v.isImmutable()).isTrue();
  }

  /** MF4-08 separation of duties: no estimate is self-approved. */
  @Test
  void rejectsSelfApproval() {
    MaintenanceEstimateVersion v = version(new BigDecimal("1000000"));
    v.submit();

    assertThatThrownBy(() -> v.approve(LEAD))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("preparer");
  }

  @Test
  void rejectsApprovalBeforeSubmission() {
    MaintenanceEstimateVersion v = version(new BigDecimal("1000000"));

    assertThatThrownBy(() -> v.approve(ORG_ADMIN)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsDoubleSubmission() {
    MaintenanceEstimateVersion v = version(new BigDecimal("1000000"));
    v.submit();

    assertThatThrownBy(v::submit).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectionRequiresReason() {
    MaintenanceEstimateVersion v = version(new BigDecimal("1000000"));
    v.submit();

    assertThatThrownBy(() -> v.reject(ORG_ADMIN, "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void supersedesOnlyAfterApproval() {
    MaintenanceEstimateVersion draft = version(new BigDecimal("1"));
    assertThatThrownBy(draft::supersede).isInstanceOf(IllegalStateException.class);

    MaintenanceEstimateVersion approved = version(new BigDecimal("1"));
    approved.submit();
    approved.approve(ORG_ADMIN);
    approved.supersede();

    assertThat(approved.getStatus()).isEqualTo(EstimateStatus.SUPERSEDED);
    assertThat(approved.isImmutable()).isTrue();
  }

  @Test
  void sumsPricedLines() {
    BigDecimal total =
        MaintenanceEstimateVersion.totalOf(
            List.of(pricedLine("2", "50000"), pricedLine("3", "25000")));

    assertThat(total).isEqualByComparingTo("175000.00");
  }

  @Test
  void totalsZeroForNoLines() {
    assertThat(MaintenanceEstimateVersion.totalOf(List.of())).isEqualByComparingTo("0.00");
    assertThat(MaintenanceEstimateVersion.totalOf(null)).isEqualByComparingTo("0.00");
  }
}

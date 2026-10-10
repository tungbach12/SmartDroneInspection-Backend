package com.smartdroneinspection.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.maintenance.domain.enums.WorkLogStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MF4-09 to MF4-11: the engineer records time and as-left condition; submission is not
 * verification.
 *
 * <p>The target schema has no narrative column for this table, so the engineer's account of the
 * work is carried inside the {@code test_readings} document under a {@code narrative} key.
 */
class MaintenanceWorkLogTest {

  private static final UUID WORK_ORDER = UUID.randomUUID();
  private static final UUID TASK = UUID.randomUUID();
  private static final UUID ENGINEER = UUID.randomUUID();

  private static MaintenanceWorkLog log() {
    return new MaintenanceWorkLog(WORK_ORDER, TASK, ENGINEER, Instant.now(), new BigDecimal("3.5"));
  }

  @Test
  void startsInProgress() {
    MaintenanceWorkLog l = log();

    assertThat(l.getStatus()).isEqualTo(WorkLogStatus.IN_PROGRESS);
    assertThat(l.getHours()).isEqualByComparingTo("3.5");
    assertThat(l.getEndedAt()).isNull();
  }

  @Test
  void requiresWorkOrderTaskEngineer() {
    Instant now = Instant.now();
    assertThatThrownBy(() -> new MaintenanceWorkLog(null, TASK, ENGINEER, now, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new MaintenanceWorkLog(WORK_ORDER, null, ENGINEER, now, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MaintenanceWorkLog(WORK_ORDER, TASK, null, now, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNegativeOrMissingHours() {
    assertThatThrownBy(
            () ->
                new MaintenanceWorkLog(
                    WORK_ORDER, TASK, ENGINEER, Instant.now(), new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new MaintenanceWorkLog(WORK_ORDER, TASK, ENGINEER, Instant.now(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** Hours are recorded per task, so zero is a legitimate value for a not-yet-started log. */
  @Test
  void acceptsZeroHours() {
    MaintenanceWorkLog l =
        new MaintenanceWorkLog(WORK_ORDER, TASK, ENGINEER, Instant.now(), BigDecimal.ZERO);

    assertThat(l.getHours()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void recordsProgressWithEvidenceAndNarrative() {
    MaintenanceWorkLog l = log();
    Instant start = l.getStartedAt();
    Instant end = start.plusSeconds(3600);

    l.recordProgress(
        start,
        end,
        new BigDecimal("4"),
        "{\"lines\":[]}",
        "Torque 40 Nm",
        "{\"narrative\":\"Replaced blade and torque checked\",\"torque\":40}");

    assertThat(l.getEndedAt()).isEqualTo(end);
    assertThat(l.getHours()).isEqualByComparingTo("4");
    assertThat(l.getAsLeftCondition()).isEqualTo("Torque 40 Nm");
    assertThat(l.getTestReadings()).contains("Replaced blade");
  }

  @Test
  void rejectsWindowRunningBackwards() {
    MaintenanceWorkLog l = log();
    Instant start = l.getStartedAt();

    assertThatThrownBy(
            () -> l.recordProgress(start, start.minusSeconds(60), BigDecimal.ONE, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void submitsWithAnEndTime() {
    MaintenanceWorkLog l = log();
    Instant end = l.getStartedAt().plusSeconds(1800);
    l.recordProgress(null, end, new BigDecimal("1"), null, null, null);

    l.submit();

    assertThat(l.getStatus()).isEqualTo(WorkLogStatus.SUBMITTED);
  }

  /** A timesheet with no end time is still open work, not a submission. */
  @Test
  void cannotSubmitWithoutAnEndTime() {
    assertThatThrownBy(log()::submit).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void cannotSubmitTwiceOrEditAfterSubmitting() {
    MaintenanceWorkLog l = log();
    Instant end = l.getStartedAt().plusSeconds(60);
    l.recordProgress(null, end, BigDecimal.ONE, null, null, null);
    l.submit();

    assertThatThrownBy(l::submit).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> l.recordProgress(null, end, BigDecimal.ONE, null, null, null))
        .isInstanceOf(IllegalStateException.class);
  }

  /** MF4-11: a change request pauses the log until the change is decided. */
  @Test
  void pausesAndResumesForChange() {
    MaintenanceWorkLog l = log();

    l.pauseForChange();
    assertThat(l.getStatus()).isEqualTo(WorkLogStatus.PAUSED_FOR_CHANGE);
    assertThatThrownBy(l::pauseForChange).isInstanceOf(IllegalStateException.class);

    l.resumeAfterChange();
    assertThat(l.getStatus()).isEqualTo(WorkLogStatus.IN_PROGRESS);
  }

  @Test
  void cannotResumeALogThatWasNeverPaused() {
    assertThatThrownBy(log()::resumeAfterChange).isInstanceOf(IllegalStateException.class);
  }
}

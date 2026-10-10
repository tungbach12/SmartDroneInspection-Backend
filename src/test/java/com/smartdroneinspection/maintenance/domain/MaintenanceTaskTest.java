package com.smartdroneinspection.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.maintenance.domain.enums.TaskStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MF4-05: the team lead divides approved scope into uniquely numbered tasks. */
class MaintenanceTaskTest {

  private static final UUID WORK_ORDER = UUID.randomUUID();
  private static final UUID LEAD = UUID.randomUUID();

  private static MaintenanceTask task(int number) {
    return new MaintenanceTask(
        WORK_ORDER,
        number,
        "Replace blade",
        "Torque to spec",
        LEAD,
        Instant.now(),
        Instant.now().plusSeconds(7200),
        "{\"checks\":[]}",
        0);
  }

  @Test
  void createsPlannedTask() {
    MaintenanceTask t = task(1);

    assertThat(t.getStatus()).isEqualTo(TaskStatus.PLANNED);
    assertThat(t.getWorkOrderId()).isEqualTo(WORK_ORDER);
    assertThat(t.getTaskNumber()).isEqualTo(1);
  }

  @Test
  void rejectsNonPositiveTaskNumber() {
    assertThatThrownBy(() -> task(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> task(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requiresName() {
    assertThatThrownBy(
            () -> new MaintenanceTask(WORK_ORDER, 1, "  ", "method", LEAD, null, null, null, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNegativeDisplayOrder() {
    assertThatThrownBy(
            () ->
                new MaintenanceTask(
                    WORK_ORDER, 1, "Replace blade", null, LEAD, null, null, null, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void replansAPlannedTask() {
    MaintenanceTask t = task(1);
    Instant start = Instant.now().plusSeconds(86400);
    Instant end = start.plusSeconds(3600);

    t.replan("New method", UUID.randomUUID(), start, end, "{\"checks\":[\"torque\"]}");

    assertThat(t.getMethod()).isEqualTo("New method");
    assertThat(t.getPlannedStartAt()).isEqualTo(start);
    assertThat(t.getPlannedEndAt()).isEqualTo(end);
  }

  @Test
  void rejectsWindowRunningBackwards() {
    MaintenanceTask t = task(1);
    Instant start = Instant.now();

    assertThatThrownBy(() -> t.replan(null, null, start, start, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> t.replan(null, null, start, start.minusSeconds(60), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsAbsentWindow() {
    MaintenanceTask t = task(1);

    t.replan(null, null, null, null, null);

    assertThat(t.getPlannedStartAt()).isNull();
    assertThat(t.getPlannedEndAt()).isNull();
  }
}

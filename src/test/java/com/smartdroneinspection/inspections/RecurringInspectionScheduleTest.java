package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.domain.RecurringInspectionSchedule;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecurringInspectionScheduleTest {

  @Test
  void pauseAndResumeDoNotAdvanceOrRewriteOccurrenceAnchor() {
    var schedule = schedule(null);
    Instant first = schedule.nextDueAt();
    schedule.pause();
    assertThat(schedule.isActive()).isFalse();
    schedule.resume();
    assertThat(schedule.nextDueAt()).isEqualTo(first);
    schedule.advance();
    assertThat(schedule.nextDueAt()).isEqualTo(Instant.parse("2028-02-29T02:00:00Z"));
    schedule.advance();
    assertThat(schedule.nextDueAt()).isEqualTo(Instant.parse("2028-03-31T02:00:00Z"));
  }

  @Test
  void stopsAfterInclusiveEndDateAndCannotResumeAnEndedSchedule() {
    var schedule = schedule(Instant.parse("2028-02-29T02:00:00Z"));
    schedule.advance();
    assertThat(schedule.isActive()).isTrue();
    schedule.advance();
    assertThat(schedule.isActive()).isFalse();
    assertThatThrownBy(schedule::resume).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void endingScheduleNeverAdvancesPastCurrentOccurrence() {
    var schedule = schedule(null);
    var due = schedule.nextDueAt();
    schedule.end();
    assertThat(schedule.isActive()).isFalse();
    assertThat(schedule.nextDueAt()).isEqualTo(due);
    assertThatThrownBy(schedule::advance).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsInvalidReminderAndWindow() {
    assertThatThrownBy(() -> create(31, 3600, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create(-1, 3600, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create(3, 0, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create(3, 3600, Instant.parse("2028-01-01T00:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private RecurringInspectionSchedule schedule(Instant endsAt) {
    return create(3, 3600, endsAt);
  }

  private RecurringInspectionSchedule create(
      int reminderDays, long durationSeconds, Instant endsAt) {
    return new RecurringInspectionSchedule(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        LocalDateTime.parse("2028-01-31T09:00:00"),
        "Asia/Ho_Chi_Minh",
        "MONTH",
        1,
        durationSeconds,
        reminderDays,
        endsAt);
  }
}

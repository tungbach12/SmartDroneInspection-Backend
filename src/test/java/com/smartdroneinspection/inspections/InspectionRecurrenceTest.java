package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.domain.InspectionRecurrence;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class InspectionRecurrenceTest {

  @Test
  void calculatesEachMonthFromOriginalAnchorWithoutDriftingAfterFebruary() {
    var recurrence =
        new InspectionRecurrence(
            LocalDateTime.parse("2028-01-31T09:00:00"), "Asia/Ho_Chi_Minh", "MONTH", 1);
    assertThat(recurrence.occurrenceAt(1).toString()).isEqualTo("2028-02-29T02:00:00Z");
    assertThat(recurrence.occurrenceAt(2).toString()).isEqualTo("2028-03-31T02:00:00Z");
  }

  @Test
  void preservesLocalTimeWhenWeeklyScheduleCrossesDaylightSavingTransition() {
    var recurrence =
        new InspectionRecurrence(
            LocalDateTime.parse("2026-03-01T09:00:00"), "America/New_York", "WEEK", 1);
    assertThat(recurrence.occurrenceAt(0).toString()).isEqualTo("2026-03-01T14:00:00Z");
    assertThat(recurrence.occurrenceAt(1).toString()).isEqualTo("2026-03-08T13:00:00Z");
  }

  @Test
  void preservesLeapDayAnchorAcrossYears() {
    var recurrence =
        new InspectionRecurrence(LocalDateTime.parse("2028-02-29T09:00:00"), "UTC", "YEAR", 1);
    assertThat(recurrence.occurrenceAt(1).toString()).isEqualTo("2029-02-28T09:00:00Z");
    assertThat(recurrence.occurrenceAt(4).toString()).isEqualTo("2032-02-29T09:00:00Z");
  }

  @Test
  void resolvesDaylightSavingGapForwardAndOverlapToEarlierOffset() {
    var gap =
        new InspectionRecurrence(
            LocalDateTime.parse("2026-03-01T02:30:00"), "America/New_York", "WEEK", 1);
    assertThat(gap.occurrenceAt(1).toString()).isEqualTo("2026-03-08T07:30:00Z");
    var overlap =
        new InspectionRecurrence(
            LocalDateTime.parse("2026-10-25T01:30:00"), "America/New_York", "WEEK", 1);
    assertThat(overlap.occurrenceAt(1).toString()).isEqualTo("2026-11-01T05:30:00Z");
  }

  @Test
  void rejectsUnknownTimezoneAndExcessiveInterval() {
    assertThatThrownBy(
            () -> new InspectionRecurrence(LocalDateTime.now(), "Invalid/Zone", "MONTH", 1))
        .isInstanceOf(java.time.DateTimeException.class);
    assertThatThrownBy(() -> new InspectionRecurrence(LocalDateTime.now(), "UTC", "MONTH", 121))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUnsupportedUnitNonPositiveIntervalAndNegativeOccurrence() {
    assertThatThrownBy(() -> new InspectionRecurrence(LocalDateTime.now(), "UTC", "DAY", 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new InspectionRecurrence(LocalDateTime.now(), "UTC", "MONTH", 0))
        .isInstanceOf(IllegalArgumentException.class);
    var recurrence = new InspectionRecurrence(LocalDateTime.now(), "UTC", "MONTH", 1);
    assertThatThrownBy(() -> recurrence.occurrenceAt(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

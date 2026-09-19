package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.assets.domain.InspectionSchedule;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.domain.enums.ChecklistTemplateStatus;
import com.smartdroneinspection.assets.domain.enums.InspectionFrequencyUnit;
import com.smartdroneinspection.assets.domain.enums.InspectionScheduleStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InspectionScheduleTest {

  @Test
  void activatesOnlyForActiveAssetAndTemplate() {
    InspectionSchedule schedule =
        new InspectionSchedule(
            UUID.randomUUID(),
            UUID.randomUUID(),
            InspectionFrequencyUnit.MONTH,
            6,
            Instant.parse("2026-10-01T00:00:00Z"),
            UUID.randomUUID());

    schedule.activate(AssetStatus.ACTIVE, ChecklistTemplateStatus.ACTIVE);
    assertThat(schedule.getStatus()).isEqualTo(InspectionScheduleStatus.ACTIVE);

    assertThatThrownBy(
            () -> schedule.activate(AssetStatus.INACTIVE, ChecklistTemplateStatus.ACTIVE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Active schedule requires an active asset and checklist template");
  }

  @Test
  void advancesOnlyAfterTheExpectedDueCycle() {
    InspectionSchedule schedule =
        new InspectionSchedule(
            UUID.randomUUID(),
            UUID.randomUUID(),
            InspectionFrequencyUnit.MONTH,
            1,
            Instant.parse("2026-10-01T00:00:00Z"),
            UUID.randomUUID());
    schedule.activate(AssetStatus.ACTIVE, ChecklistTemplateStatus.ACTIVE);

    schedule.markGenerated(LocalDate.parse("2026-10-01"), Instant.parse("2026-11-01T00:00:00Z"));

    assertThat(schedule.getLastGeneratedDueCycle()).isEqualTo(LocalDate.parse("2026-10-01"));
    assertThat(schedule.getNextDueAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
  }

  @Test
  void rejectsNonPositiveFrequencyIntervals() {
    assertThatThrownBy(
            () ->
                new InspectionSchedule(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    InspectionFrequencyUnit.MONTH,
                    0,
                    Instant.parse("2026-10-01T00:00:00Z"),
                    UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void pausesAndDisablesTheSchedule() {
    InspectionSchedule schedule =
        new InspectionSchedule(
            UUID.randomUUID(),
            UUID.randomUUID(),
            InspectionFrequencyUnit.MONTH,
            1,
            Instant.parse("2026-10-01T00:00:00Z"),
            UUID.randomUUID());

    assertThat(schedule.getStatus()).isEqualTo(InspectionScheduleStatus.PAUSED);
    schedule.activate(AssetStatus.ACTIVE, ChecklistTemplateStatus.ACTIVE);
    schedule.pause();
    assertThat(schedule.getStatus()).isEqualTo(InspectionScheduleStatus.PAUSED);
    schedule.disable();
    assertThat(schedule.getStatus()).isEqualTo(InspectionScheduleStatus.DISABLED);
  }

  @Test
  void generatesOnlyWhileActiveAndWithAdvancingValues() {
    InspectionSchedule schedule =
        new InspectionSchedule(
            UUID.randomUUID(),
            UUID.randomUUID(),
            InspectionFrequencyUnit.MONTH,
            1,
            Instant.parse("2026-10-01T00:00:00Z"),
            UUID.randomUUID());

    assertThatThrownBy(
            () ->
                schedule.markGenerated(
                    LocalDate.parse("2026-10-01"), Instant.parse("2026-11-01T00:00:00Z")))
        .isInstanceOf(IllegalStateException.class);

    schedule.activate(AssetStatus.ACTIVE, ChecklistTemplateStatus.ACTIVE);
    schedule.markGenerated(LocalDate.parse("2026-10-01"), Instant.parse("2026-11-01T00:00:00Z"));

    assertThatThrownBy(
            () ->
                schedule.markGenerated(
                    LocalDate.parse("2026-09-01"), Instant.parse("2026-12-01T00:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                schedule.markGenerated(
                    LocalDate.parse("2026-11-01"), Instant.parse("2026-11-01T00:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

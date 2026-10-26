package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.domain.RecurringInspectionSchedule;
import com.smartdroneinspection.inspections.repository.RecurringInspectionScheduleRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class RecurringInspectionSchedulerJobTest {

  private RecurringInspectionScheduleRepository scheduleRepository;
  private RecurringInspectionScheduleService scheduleService;

  private RecurringInspectionSchedulerJob job;

  @BeforeEach
  void setUp() {
    scheduleRepository = mock(RecurringInspectionScheduleRepository.class);
    scheduleService = mock(RecurringInspectionScheduleService.class);

    job = new RecurringInspectionSchedulerJob(scheduleRepository, scheduleService, true, 10, 14);
  }

  @Test
  void runsRollingGenerationAcrossActiveSchedulesAndHandlesErrorsGracefully() {
    UUID s1Id = UUID.randomUUID();
    UUID s2Id = UUID.randomUUID();

    RecurringInspectionSchedule s1 = mockSchedule(s1Id);
    RecurringInspectionSchedule s2 = mockSchedule(s2Id);

    when(scheduleRepository.findActiveSchedules(PageRequest.of(0, 10))).thenReturn(List.of(s1, s2));

    Instant now = Instant.now();
    doThrow(new RuntimeException("Generation failed"))
        .when(scheduleService)
        .generateOccurrencesInternal(eq(s1Id), eq(now), any(Instant.class));

    int processed = job.runRollingGeneration(now);

    assertThat(processed).isEqualTo(1); // s1 failed, s2 succeeded
    verify(scheduleService).generateOccurrencesInternal(eq(s1Id), eq(now), any(Instant.class));
    verify(scheduleService).generateOccurrencesInternal(eq(s2Id), eq(now), any(Instant.class));
  }

  private RecurringInspectionSchedule mockSchedule(UUID id) {
    RecurringInspectionSchedule s =
        new RecurringInspectionSchedule(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Bridge scan",
            null,
            null,
            null,
            LocalDateTime.now().plusDays(1),
            "UTC",
            "WEEK",
            1,
            3600L,
            3,
            null);
    ReflectionTestUtils.setField(s, "id", id);
    return s;
  }
}

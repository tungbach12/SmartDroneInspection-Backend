package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.RecurringInspectionSchedule;
import com.smartdroneinspection.inspections.repository.RecurringInspectionScheduleRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringInspectionSchedulerJob {

  private static final Logger log = LoggerFactory.getLogger(RecurringInspectionSchedulerJob.class);

  private final RecurringInspectionScheduleRepository scheduleRepository;
  private final RecurringInspectionScheduleService scheduleService;
  private final boolean enabled;
  private final int batchSize;
  private final int lookaheadDays;

  public RecurringInspectionSchedulerJob(
      RecurringInspectionScheduleRepository scheduleRepository,
      RecurringInspectionScheduleService scheduleService,
      @Value("${app.recurring-inspections.scheduler.enabled:true}") boolean enabled,
      @Value("${app.recurring-inspections.scheduler.batch-size:50}") int batchSize,
      @Value("${app.recurring-inspections.scheduler.lookahead-days:14}") int lookaheadDays) {
    this.scheduleRepository = scheduleRepository;
    this.scheduleService = scheduleService;
    this.enabled = enabled;
    this.batchSize = batchSize;
    this.lookaheadDays = lookaheadDays;
  }

  @Scheduled(
      fixedDelayString = "${app.recurring-inspections.scheduler.fixed-delay-ms:60000}",
      initialDelayString = "${app.recurring-inspections.scheduler.initial-delay-ms:10000}")
  public void runScheduled() {
    if (!enabled) {
      return;
    }
    runRollingGeneration(Instant.now());
  }

  public int runRollingGeneration(Instant now) {
    Instant horizon = now.plus(lookaheadDays, ChronoUnit.DAYS);
    List<RecurringInspectionSchedule> activeSchedules =
        scheduleRepository.findActiveSchedules(PageRequest.of(0, batchSize));

    int processed = 0;
    for (RecurringInspectionSchedule schedule : activeSchedules) {
      try {
        scheduleService.generateOccurrencesInternal(schedule.getId(), now, horizon);
        processed++;
      } catch (Exception ex) {
        log.error("Failed to generate occurrences for recurring schedule {}", schedule.getId(), ex);
      }
    }
    return processed;
  }
}

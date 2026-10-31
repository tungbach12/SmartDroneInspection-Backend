package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.ScheduleOccurrence;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.events.InspectionReminderDueEvent;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.ScheduleOccurrenceRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InspectionReminderJob {

  private static final Logger log = LoggerFactory.getLogger(InspectionReminderJob.class);

  private final ScheduleOccurrenceRepository occurrenceRepository;
  private final InspectionRepository inspectionRepository;
  private final UserAccess userAccess;
  private final ApplicationEventPublisher eventPublisher;
  private final boolean enabled;
  private final int batchSize;
  private final int reminderWindowDays;

  public InspectionReminderJob(
      ScheduleOccurrenceRepository occurrenceRepository,
      InspectionRepository inspectionRepository,
      UserAccess userAccess,
      ApplicationEventPublisher eventPublisher,
      @Value("${app.inspection-reminders.enabled:true}") boolean enabled,
      @Value("${app.inspection-reminders.batch-size:50}") int batchSize,
      @Value("${app.inspection-reminders.window-days:3}") int reminderWindowDays) {
    this.occurrenceRepository = occurrenceRepository;
    this.inspectionRepository = inspectionRepository;
    this.userAccess = userAccess;
    this.eventPublisher = eventPublisher;
    this.enabled = enabled;
    this.batchSize = batchSize;
    this.reminderWindowDays = reminderWindowDays;
  }

  @Scheduled(
      fixedDelayString = "${app.inspection-reminders.fixed-delay-ms:60000}",
      initialDelayString = "${app.inspection-reminders.initial-delay-ms:15000}")
  public void runScheduled() {
    if (!enabled) {
      return;
    }
    runReminderCheck(Instant.now());
  }

  public int runReminderCheck(Instant now) {
    Instant windowEnd = now.plus(reminderWindowDays, ChronoUnit.DAYS);
    List<ScheduleOccurrence> dueOccurrences =
        occurrenceRepository.findDueForReminder(now, windowEnd, PageRequest.of(0, batchSize));

    int sentCount = 0;
    for (ScheduleOccurrence occurrence : dueOccurrences) {
      try {
        if (processSingleReminder(occurrence.getId(), now)) {
          sentCount++;
        }
      } catch (Exception ex) {
        log.error("Failed to process reminder for occurrence {}", occurrence.getId(), ex);
      }
    }
    return sentCount;
  }

  @Transactional
  public boolean processSingleReminder(UUID occurrenceId, Instant now) {
    ScheduleOccurrence occurrence = occurrenceRepository.findById(occurrenceId).orElse(null);
    if (occurrence == null || occurrence.getReminderSentAt() != null) {
      return false;
    }

    if (occurrence.getInspectionId() == null) {
      return false;
    }

    Inspection inspection =
        inspectionRepository.findById(occurrence.getInspectionId()).orElse(null);
    if (inspection == null) {
      return false;
    }

    InspectionStatus status = inspection.getStatus();
    if (status != InspectionStatus.SCHEDULED) {
      return false;
    }

    if (inspection.getPlannedStartAt() == null || inspection.getPlannedStartAt().isBefore(now)) {
      return false;
    }

    boolean inspectorActive =
        userAccess
            .findActiveUser(inspection.getInspectorId())
            .filter(
                u ->
                    u.hasRole(Roles.INSPECTOR)
                        && (inspection.isPartnerAssignment()
                            ? u.isInspectorPartner()
                            : inspection.getOrganizationId().equals(u.organizationId())))
            .isPresent();
    if (!inspectorActive) {
      return false;
    }

    occurrence.markReminderSent(now);
    occurrenceRepository.saveAndFlush(occurrence);

    eventPublisher.publishEvent(
        new InspectionReminderDueEvent(
            inspection.getId(),
            inspection.getOrganizationId(),
            inspection.getInspectorId(),
            inspection.getPlannedStartAt()));

    return true;
  }
}

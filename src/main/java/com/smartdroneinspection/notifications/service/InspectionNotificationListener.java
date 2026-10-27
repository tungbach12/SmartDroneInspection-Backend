package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.inspections.events.InspectionAssignedEvent;
import com.smartdroneinspection.inspections.events.InspectionReminderDueEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Records in-app notifications for inspection assignments and scheduled reminders. */
@Component
public class InspectionNotificationListener {

  private static final Logger log = LoggerFactory.getLogger(InspectionNotificationListener.class);

  private final InspectionNotificationService inspectionNotificationService;

  public InspectionNotificationListener(
      InspectionNotificationService inspectionNotificationService) {
    this.inspectionNotificationService = inspectionNotificationService;
  }

  @ApplicationModuleListener
  public void onInspectionAssigned(InspectionAssignedEvent event) {
    log.info("Handling InspectionAssignedEvent for inspection {}", event.inspectionId());
    inspectionNotificationService.recordInspectionAssignment(
        event.inspectionId(), event.organizationId(), event.inspectorId(), event.plannedStartAt());
  }

  @ApplicationModuleListener
  public void onInspectionReminderDue(InspectionReminderDueEvent event) {
    log.info("Handling InspectionReminderDueEvent for inspection {}", event.inspectionId());
    inspectionNotificationService.recordInspectionReminder(
        event.inspectionId(), event.organizationId(), event.inspectorId(), event.plannedStartAt());
  }
}

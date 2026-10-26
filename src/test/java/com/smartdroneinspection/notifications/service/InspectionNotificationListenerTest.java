package com.smartdroneinspection.notifications.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.smartdroneinspection.inspections.events.InspectionAssignedEvent;
import com.smartdroneinspection.inspections.events.InspectionReminderDueEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InspectionNotificationListenerTest {

  private InspectionNotificationService notificationService;
  private InspectionNotificationListener listener;

  @BeforeEach
  void setUp() {
    notificationService = mock(InspectionNotificationService.class);
    listener = new InspectionNotificationListener(notificationService);
  }

  @Test
  void onInspectionAssignedDelegatesToService() {
    UUID inspectionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    Instant plannedStart = Instant.now();

    InspectionAssignedEvent event =
        new InspectionAssignedEvent(inspectionId, orgId, inspectorId, plannedStart);

    listener.onInspectionAssigned(event);

    verify(notificationService)
        .recordInspectionAssignment(inspectionId, orgId, inspectorId, plannedStart);
  }

  @Test
  void onInspectionReminderDueDelegatesToService() {
    UUID inspectionId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    Instant plannedStart = Instant.now();

    InspectionReminderDueEvent event =
        new InspectionReminderDueEvent(inspectionId, orgId, inspectorId, plannedStart);

    listener.onInspectionReminderDue(event);

    verify(notificationService)
        .recordInspectionReminder(inspectionId, orgId, inspectorId, plannedStart);
  }
}

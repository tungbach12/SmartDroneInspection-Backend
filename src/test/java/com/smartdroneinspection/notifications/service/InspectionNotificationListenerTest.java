package com.smartdroneinspection.notifications.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.smartdroneinspection.inspections.events.InspectionAssignedEvent;
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
    UUID ownerCustomerId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    Instant plannedStart = Instant.now();

    InspectionAssignedEvent event =
        new InspectionAssignedEvent(inspectionId, ownerCustomerId, inspectorId, plannedStart);

    listener.onInspectionAssigned(event);

    verify(notificationService)
        .recordInspectionAssignment(inspectionId, ownerCustomerId, inspectorId, plannedStart);
  }

  @Test
  void onFieldSessionAbortedDelegatesToService() {
    UUID inspectionId = UUID.randomUUID();
    UUID ownerCustomerId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    String reason = "Heavy thunderstorm";

    com.smartdroneinspection.inspections.events.FieldSessionAbortedEvent event =
        new com.smartdroneinspection.inspections.events.FieldSessionAbortedEvent(
            inspectionId, ownerCustomerId, sessionId, inspectorId, reason, Instant.now());

    listener.onFieldSessionAborted(event);

    verify(notificationService)
        .recordFieldSessionAborted(inspectionId, ownerCustomerId, inspectorId, reason);
  }
}

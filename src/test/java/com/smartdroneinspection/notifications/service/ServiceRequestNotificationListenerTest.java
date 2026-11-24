package com.smartdroneinspection.notifications.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.smartdroneinspection.contracts.events.ServiceRequestCreatedEvent;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceRequestNotificationListenerTest {

  private InspectionNotificationService notificationService;
  private ServiceRequestNotificationListener listener;

  @BeforeEach
  void setUp() {
    notificationService = mock(InspectionNotificationService.class);
    listener = new ServiceRequestNotificationListener(notificationService);
  }

  @Test
  void onServiceRequestCreatedDelegatesToService() {
    UUID requestId = UUID.randomUUID();
    UUID ownerCustomerId = UUID.randomUUID();
    LocalDate requestedFrom = LocalDate.of(2026, 12, 25);
    ServiceRequestCreatedEvent event =
        new ServiceRequestCreatedEvent(
            requestId, ownerCustomerId, "Tower inspection", requestedFrom);

    listener.onServiceRequestCreated(event);

    verify(notificationService)
        .recordServiceRequestCreated(requestId, "Tower inspection", requestedFrom);
  }
}

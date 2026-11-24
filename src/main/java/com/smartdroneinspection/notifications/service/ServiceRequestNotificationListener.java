package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.contracts.events.ServiceRequestCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Records in-app notices when a Customer creates a new service request. */
@Component
public class ServiceRequestNotificationListener {

  private static final Logger log =
      LoggerFactory.getLogger(ServiceRequestNotificationListener.class);

  private final InspectionNotificationService notificationService;

  public ServiceRequestNotificationListener(InspectionNotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @ApplicationModuleListener
  public void onServiceRequestCreated(ServiceRequestCreatedEvent event) {
    log.info("Handling ServiceRequestCreatedEvent for request {}", event.requestId());
    notificationService.recordServiceRequestCreated(
        event.requestId(), event.description(), event.requestedFrom());
  }
}

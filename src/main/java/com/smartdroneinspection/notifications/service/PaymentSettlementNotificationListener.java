package com.smartdroneinspection.notifications.service;

import com.smartdroneinspection.contracts.events.PaymentSettledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Records in-app notices when a manual or online payment settles an occurrence. */
@Component
public class PaymentSettlementNotificationListener {

  private static final Logger log =
      LoggerFactory.getLogger(PaymentSettlementNotificationListener.class);

  private final InspectionNotificationService notificationService;

  public PaymentSettlementNotificationListener(InspectionNotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @ApplicationModuleListener
  public void onPaymentSettled(PaymentSettledEvent event) {
    log.info("Handling PaymentSettledEvent for occurrence {}", event.occurrenceId());
    notificationService.recordPaymentSettled(
        event.occurrenceId(),
        event.inspectionId(),
        event.inspectorId(),
        event.paymentReference(),
        event.amount(),
        event.paymentMethod(),
        event.settledAt());
  }
}

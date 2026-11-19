package com.smartdroneinspection.notifications.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.smartdroneinspection.contracts.events.PaymentSettledEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentSettlementNotificationListenerTest {

  private InspectionNotificationService notificationService;
  private PaymentSettlementNotificationListener listener;

  @BeforeEach
  void setUp() {
    notificationService = mock(InspectionNotificationService.class);
    listener = new PaymentSettlementNotificationListener(notificationService);
  }

  @Test
  void onPaymentSettledDelegatesToNotificationService() {
    UUID occurrenceId = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    UUID inspectionId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    Instant settledAt = Instant.now();
    PaymentSettledEvent event =
        new PaymentSettledEvent(
            occurrenceId,
            requestId,
            inspectionId,
            inspectorId,
            "PAYOS-123",
            new BigDecimal("11000000.00"),
            "PAYOS",
            settledAt);

    listener.onPaymentSettled(event);

    verify(notificationService)
        .recordPaymentSettled(
            occurrenceId,
            inspectionId,
            inspectorId,
            "PAYOS-123",
            new BigDecimal("11000000.00"),
            "PAYOS",
            settledAt);
  }
}

package com.smartdroneinspection.contracts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServicePaymentTest {

  @Test
  void acceptedReportStartsOnePaymentDeadlineAndOverdueStampIsIdempotent() {
    ServiceRequestOccurrence occurrence = occurrence();
    Instant beforeAcceptance = Instant.now();
    occurrence.acceptReport(UUID.randomUUID(), UUID.randomUUID());
    occurrence.startPaymentDueWindow(Duration.ofDays(15));

    Instant deadline = occurrence.getPaymentDueAt();
    assertThat(deadline).isNotNull().isAfter(beforeAcceptance.plus(Duration.ofDays(14)));
    assertThat(occurrence.isPaymentOverdue(deadline)).isFalse();
    assertThat(occurrence.isPaymentOverdue(deadline.plusNanos(1))).isTrue();
    assertThat(occurrence.needsOverdueNotification(deadline.plusNanos(1))).isTrue();

    Instant notifiedAt = deadline.plus(Duration.ofDays(1));
    occurrence.markOverdueNotified(notifiedAt);
    assertThat(occurrence.needsOverdueNotification(notifiedAt.plusSeconds(60))).isFalse();

    // Re-accepting an already accepted report must not extend its original payment window.
    occurrence.acceptReport(UUID.randomUUID(), UUID.randomUUID());
    occurrence.startPaymentDueWindow(Duration.ofDays(15));
    assertThat(occurrence.getPaymentDueAt()).isEqualTo(deadline);
  }

  @Test
  void paymentConfirmationClearsOverdueDeadlineAndNotificationMarker() {
    ServiceRequestOccurrence occurrence = occurrence();
    occurrence.acceptReport(UUID.randomUUID(), UUID.randomUUID());
    occurrence.startPaymentDueWindow(Duration.ofDays(15));
    Instant deadline = occurrence.getPaymentDueAt();
    occurrence.markOverdueNotified(deadline.plus(Duration.ofDays(1)));

    occurrence.confirmOnlinePayment("SEPAY-123", new BigDecimal("100.00"), "SEPAY");

    assertThat(occurrence.getPaymentStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePaymentStatus.PAID);
    assertThat(occurrence.getPaymentDueAt()).isNull();
    assertThat(occurrence.getPaymentOverdueNotifiedAt()).isNull();
    assertThat(occurrence.needsOverdueNotification(Instant.now())).isFalse();
  }

  private static ServiceRequestOccurrence occurrence() {
    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(
            UUID.randomUUID(),
            1,
            Instant.parse("2027-01-01T08:00:00Z"),
            Instant.parse("2027-01-01T10:00:00Z"));
    occurrence.snapshotFinance(
        new BigDecimal("100.00"),
        new BigDecimal("0.00"),
        new BigDecimal("0.00"),
        new BigDecimal("0.00"));
    return occurrence;
  }
}

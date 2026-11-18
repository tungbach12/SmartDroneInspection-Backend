package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.events.PaymentOverdueEvent;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class PaymentDueExpiryServiceTest {

  @Test
  void overduePaymentEmitsOneEventAndStampsOccurrence() {
    ServiceRequestOccurrenceRepository occurrences = mock(ServiceRequestOccurrenceRepository.class);
    ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    UUID ownerId = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    ServiceRequestOccurrence occurrence = occurrence(requestId);
    Instant now = occurrence.getPaymentDueAt().plus(Duration.ofDays(1));
    ServiceRequest request = mock(ServiceRequest.class);
    when(occurrences.findOverduePayments(now)).thenReturn(List.of(occurrence));
    when(requests.findById(requestId)).thenReturn(Optional.of(request));
    when(request.getOwnerCustomerId()).thenReturn(ownerId);

    PaymentDueExpiryService service =
        new PaymentDueExpiryService(
            occurrences, requests, new PaymentDueProperties(Duration.ofDays(15), true), events);
    PaymentDueExpiryService.Summary summary = service.runOnce(now);

    assertThat(summary).isEqualTo(new PaymentDueExpiryService.Summary(1, 0));
    assertThat(occurrence.getPaymentOverdueNotifiedAt()).isEqualTo(now);
    verify(occurrences).saveAndFlush(occurrence);
    verify(events)
        .publishEvent(
            new PaymentOverdueEvent(
                occurrence.getId(),
                requestId,
                occurrence.getInspectionId(),
                ownerId,
                occurrence.getPaymentDueAt(),
                now));
  }

  @Test
  void onTimePaymentDoesNotEmitEvent() {
    ServiceRequestOccurrenceRepository occurrences = mock(ServiceRequestOccurrenceRepository.class);
    ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    Instant now = Instant.parse("2026-11-16T12:00:00Z");
    when(occurrences.findOverduePayments(now)).thenReturn(List.of());

    PaymentDueExpiryService service =
        new PaymentDueExpiryService(
            occurrences, requests, new PaymentDueProperties(Duration.ofDays(15), true), events);

    assertThat(service.runOnce(now)).isEqualTo(new PaymentDueExpiryService.Summary(0, 0));
    verify(events, never()).publishEvent(any());
  }

  private static ServiceRequestOccurrence occurrence(UUID requestId) {
    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(
            requestId,
            1,
            Instant.parse("2027-01-01T08:00:00Z"),
            Instant.parse("2027-01-01T10:00:00Z"));
    occurrence.snapshotFinance(
        new java.math.BigDecimal("100.00"),
        java.math.BigDecimal.ZERO,
        java.math.BigDecimal.ZERO,
        java.math.BigDecimal.ZERO);
    occurrence.acceptReport(UUID.randomUUID(), UUID.randomUUID());
    occurrence.startPaymentDueWindow(Duration.ofDays(15));
    return occurrence;
  }
}

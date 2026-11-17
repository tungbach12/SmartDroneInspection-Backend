package com.smartdroneinspection.contracts.service;

import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.events.PaymentOverdueEvent;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detects occurrences whose customer payment deadline has passed and raises overdue reminders.
 *
 * <p>Per the business decision for this release, an overdue occurrence is warned to the customer
 * and platform managers but is never automatically revoked or blocked: payment terms are
 * contractual, so the platform only nudges rather than gating access. Each occurrence is notified
 * once per overdue window; the notification listener records deduplicated in-app notifications and
 * the {@code payment_overdue_notified_at} stamp prevents a second reminder on the next daily run.
 */
@Service
@EnableConfigurationProperties(PaymentDueProperties.class)
public class PaymentDueExpiryService {

  private static final Logger log = LoggerFactory.getLogger(PaymentDueExpiryService.class);

  private final ServiceRequestOccurrenceRepository occurrences;
  private final ServiceRequestRepository requests;
  private final PaymentDueProperties properties;
  private final ApplicationEventPublisher eventPublisher;

  public PaymentDueExpiryService(
      ServiceRequestOccurrenceRepository occurrences,
      ServiceRequestRepository requests,
      PaymentDueProperties properties,
      ApplicationEventPublisher eventPublisher) {
    this.occurrences = occurrences;
    this.requests = requests;
    this.properties = properties;
    this.eventPublisher = eventPublisher;
  }

  /** Outcome of one scan, exposed for logging and assertions in tests. */
  public record Summary(int overdueNotified, int skipped) {}

  @Scheduled(cron = "${app.payment-due.expiry-cron:0 15 8 * * *}", zone = "Asia/Ho_Chi_Minh")
  public void scanOverduePayments() {
    if (!properties.enabled()) {
      log.debug("Payment overdue scan is disabled; skipping");
      return;
    }
    try {
      Summary summary = runOnce(Instant.now());
      if (summary.overdueNotified() > 0) {
        log.info(
            "Payment overdue scan finished: overdueNotified={}, skipped={}",
            summary.overdueNotified(),
            summary.skipped());
      }
    } catch (RuntimeException exception) {
      // A scheduler must never propagate an exception: the next daily run retries.
      log.warn("Payment overdue scan failed: {}", exception.getMessage());
    }
  }

  /**
   * Marks every overdue occurrence as notified and emits an event so the notifications module can
   * record the reminder. Returns the number of occurrences newly flagged.
   */
  @Transactional
  public Summary runOnce(Instant now) {
    List<ServiceRequestOccurrence> overdue = occurrences.findOverduePayments(now);
    int notified = 0;
    int skipped = 0;
    for (ServiceRequestOccurrence occurrence : overdue) {
      if (!occurrence.needsOverdueNotification(now)) {
        skipped++;
        continue;
      }
      UUID ownerCustomerId =
          occurrence.getRequestId() != null
              ? requests
                  .findById(occurrence.getRequestId())
                  .map(ServiceRequest::getOwnerCustomerId)
                  .orElse(null)
              : null;
      occurrence.markOverdueNotified(now);
      occurrences.saveAndFlush(occurrence);
      eventPublisher.publishEvent(
          new PaymentOverdueEvent(
              occurrence.getId(),
              occurrence.getRequestId(),
              ownerCustomerId,
              occurrence.getPaymentDueAt(),
              now));
      notified++;
    }
    return new Summary(notified, skipped);
  }
}

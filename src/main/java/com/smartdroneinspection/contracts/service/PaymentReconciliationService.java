package com.smartdroneinspection.contracts.service;

import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus;
import com.smartdroneinspection.contracts.gateway.PayOSGateway;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles open PayOS checkout orders against the gateway.
 *
 * <p>A customer can pay at PayOS while the webhook is lost, delayed, or rejected by an edge proxy.
 * Without reconciliation that real payment stays invisible to the platform: the occurrence sits in
 * {@code PAYMENT_DUE} forever and the Inspector payout is never released. The job re-reads the
 * authoritative gateway state for orders that have been open longer than the grace period and
 * applies a confirmed payment through the same idempotent path the webhook uses.
 *
 * <p>Only PayOS supports a status lookup. VNPay offers no merchant query endpoint in the integrated
 * API version, so VNPay orders cannot be reconciled from the gateway and are deliberately excluded
 * rather than silently reported as reconciled.
 */
@Service
public class PaymentReconciliationService {

  private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);

  private static final String PAYOS = "PAYOS";

  private final ServiceRequestOccurrenceRepository occurrences;
  private final PayOSGateway payOSGateway;
  private final PaymentReconciliationProperties properties;

  public PaymentReconciliationService(
      ServiceRequestOccurrenceRepository occurrences,
      PayOSGateway payOSGateway,
      PaymentReconciliationProperties properties) {
    this.occurrences = occurrences;
    this.payOSGateway = payOSGateway;
    this.properties = properties;
  }

  /** Reconciliation counters for one batch, logged and asserted by tests. */
  public record Summary(int scanned, int confirmed, int cancelled, int stillPending, int failed) {}

  /**
   * Reconciles one batch of stale PayOS orders. Each occurrence is processed in its own transaction
   * so a single gateway failure cannot roll back the payments already recorded in the batch.
   */
  public Summary reconcilePayOSOrders() {
    if (!properties.enabled()) {
      log.debug("PayOS reconciliation is disabled; skipping batch");
      return new Summary(0, 0, 0, 0, 0);
    }

    Instant openedBefore = Instant.now().minus(properties.gracePeriod());
    List<ServiceRequestOccurrence> stale =
        occurrences
            .findStaleGatewayOrders(
                PAYOS, openedBefore, PageRequest.of(0, Math.max(1, properties.batchSize())))
            .getContent();

    int confirmed = 0;
    int cancelled = 0;
    int stillPending = 0;
    int failed = 0;
    for (ServiceRequestOccurrence occurrence : stale) {
      try {
        Outcome outcome = reconcileOne(occurrence.getId());
        switch (outcome) {
          case CONFIRMED -> confirmed++;
          case CANCELLED -> cancelled++;
          case STILL_PENDING -> stillPending++;
        }
      } catch (RuntimeException exception) {
        failed++;
        log.warn(
            "PayOS reconciliation failed for occurrence {}: {}",
            occurrence.getId(),
            exception.getMessage());
      }
    }

    Summary summary = new Summary(stale.size(), confirmed, cancelled, stillPending, failed);
    if (summary.scanned() > 0) {
      log.info(
          "PayOS reconciliation batch finished: scanned={}, confirmed={}, cancelled={}, stillPending={}, failed={}",
          summary.scanned(),
          summary.confirmed(),
          summary.cancelled(),
          summary.stillPending(),
          summary.failed());
    }
    return summary;
  }

  /** Reconciles a single occurrence, locking it so the webhook cannot race the job. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Outcome reconcileOne(java.util.UUID occurrenceId) {
    ServiceRequestOccurrence occurrence =
        occurrences
            .findWithLockById(occurrenceId)
            .orElseThrow(() -> new IllegalStateException("Occurrence not found: " + occurrenceId));

    if (occurrence.getPaymentStatus() == OccurrencePaymentStatus.PAID) {
      return Outcome.CONFIRMED;
    }
    if (!occurrence.isGatewayOrderOpen()
        || !PAYOS.equalsIgnoreCase(occurrence.getPaymentMethod())) {
      return Outcome.STILL_PENDING;
    }

    long orderCode = occurrence.getPayosOrderCode();
    PayOSGateway.PayOSPaymentLink link = payOSGateway.getPaymentLink(orderCode);
    Instant checkedAt = Instant.now();
    if (link == null) {
      // The gateway could not be read; leave the order open and try again on the next run.
      occurrence.recordGatewayReconciliationCheck(checkedAt);
      occurrences.saveAndFlush(occurrence);
      return Outcome.STILL_PENDING;
    }

    if ("PAID".equalsIgnoreCase(link.status())) {
      if (occurrence.getTotalPrice() == null
          || link.amount() != occurrence.getTotalPrice().longValue()) {
        throw new IllegalStateException(
            "Gateway reports a paid order "
                + orderCode
                + " for amount "
                + link.amount()
                + " but the occurrence price is "
                + occurrence.getTotalPrice());
      }
      occurrence.confirmOnlinePayment(
          "PAYOS-" + orderCode + "-RECONCILED", occurrence.getTotalPrice(), PAYOS);
      occurrences.saveAndFlush(occurrence);
      log.info(
          "Reconciled PayOS order {} as PAID for occurrence {} (webhook was not received)",
          orderCode,
          occurrence.getId());
      return Outcome.CONFIRMED;
    }

    if ("CANCELLED".equalsIgnoreCase(link.status()) || "EXPIRED".equalsIgnoreCase(link.status())) {
      occurrence.clearUnpaidPaymentOrder(orderCode, PAYOS);
      occurrences.saveAndFlush(occurrence);
      log.info(
          "Released PayOS order {} for occurrence {} because the gateway reports status {}",
          orderCode,
          occurrence.getId(),
          link.status());
      return Outcome.CANCELLED;
    }

    occurrence.recordGatewayReconciliationCheck(checkedAt);
    occurrences.saveAndFlush(occurrence);
    return Outcome.STILL_PENDING;
  }

  public enum Outcome {
    CONFIRMED,
    CANCELLED,
    STILL_PENDING
  }
}

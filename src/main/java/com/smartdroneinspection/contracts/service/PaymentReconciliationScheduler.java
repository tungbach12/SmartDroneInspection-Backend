package com.smartdroneinspection.contracts.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Schedules the PayOS gateway reconciliation job.
 *
 * <p>The interval is configured via {@code app.payment-reconciliation.fixed-delay}; it defaults to
 * 15 minutes. Reconciliation only touches orders that have been open longer than the grace period
 * (default 10 minutes), so a freshly created order is never queried before its webhook has had a
 * chance to arrive.
 */
@Configuration
@EnableConfigurationProperties(PaymentReconciliationProperties.class)
public class PaymentReconciliationScheduler {

  private final PaymentReconciliationService reconciliationService;
  private final PaymentReconciliationProperties properties;

  public PaymentReconciliationScheduler(
      PaymentReconciliationService reconciliationService,
      PaymentReconciliationProperties properties) {
    this.reconciliationService = reconciliationService;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${app.payment-reconciliation.fixed-delay:15m}")
  public void run() {
    if (!properties.enabled()) {
      return;
    }
    try {
      reconciliationService.reconcilePayOSOrders();
    } catch (RuntimeException exception) {
      // A scheduler must never propagate an exception: the next run retries.
      org.slf4j.LoggerFactory.getLogger(PaymentReconciliationScheduler.class)
          .warn("PayOS reconciliation run failed: {}", exception.getMessage());
    }
  }
}

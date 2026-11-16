package com.smartdroneinspection.contracts.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the PayOS gateway reconciliation job.
 *
 * <p>Reconciliation is enabled by default because a lost webhook means a customer payment that the
 * platform never records. It does nothing when PayOS itself is disabled, since no gateway orders
 * exist in that mode.
 */
@ConfigurationProperties("app.payment-reconciliation")
public record PaymentReconciliationProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("10m") Duration gracePeriod,
    @DefaultValue("50") int batchSize,
    @DefaultValue("15m") Duration fixedDelay) {

  public PaymentReconciliationProperties {
    if (gracePeriod == null || gracePeriod.isNegative()) {
      gracePeriod = Duration.ofMinutes(10);
    }
    if (batchSize <= 0) {
      batchSize = 50;
    }
    if (fixedDelay == null || fixedDelay.isNegative() || fixedDelay.isZero()) {
      fixedDelay = Duration.ofMinutes(15);
    }
  }
}

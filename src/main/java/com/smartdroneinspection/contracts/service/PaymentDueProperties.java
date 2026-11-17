package com.smartdroneinspection.contracts.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the customer payment due-window policy.
 *
 * <p>When a customer accepts a report the occurrence becomes PAYMENT_DUE. The platform gives the
 * customer {@code due-window} to settle before an overdue reminder is raised. Overdue does not
 * revoke report access or block anything automatically: the job only warns the customer and
 * platform managers, matching the business decision that payment terms are contractual, not
 * technical gates.
 */
@ConfigurationProperties("app.payment-due")
public record PaymentDueProperties(
    @DefaultValue("15d") Duration dueWindow, @DefaultValue("true") boolean enabled) {

  public PaymentDueProperties() {
    this(Duration.ofDays(15), true);
  }

  public PaymentDueProperties {
    if (dueWindow == null || dueWindow.isNegative() || dueWindow.isZero()) {
      dueWindow = Duration.ofDays(15);
    }
  }
}

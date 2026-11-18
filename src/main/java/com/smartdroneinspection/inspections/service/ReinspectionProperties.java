package com.smartdroneinspection.inspections.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How long a RESOLVED defect verification stays authoritative.
 *
 * <p>Once this window passes, the defect must be re-verified before it can support closing the
 * linked work. The default of 90 days matches a typical warranty period for structural repairs.
 */
@ConfigurationProperties("app.reinspection")
public record ReinspectionProperties(@DefaultValue("90d") Duration resolvedValidity) {

  public ReinspectionProperties {
    if (resolvedValidity == null || resolvedValidity.isNegative() || resolvedValidity.isZero()) {
      resolvedValidity = Duration.ofDays(90);
    }
  }
}

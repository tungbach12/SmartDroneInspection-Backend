package com.smartdroneinspection.workforce.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operational thresholds for workforce credential management.
 *
 * <p>The 60-day expiring-soon horizon is a deployment-overridable display warning, not a statutory
 * renewal rule.
 */
@ConfigurationProperties(prefix = "app.workforce")
public class WorkforceProperties {

  private Duration credentialExpiringSoonWindow = Duration.ofDays(60);

  public Duration getCredentialExpiringSoonWindow() {
    return credentialExpiringSoonWindow;
  }

  public void setCredentialExpiringSoonWindow(Duration credentialExpiringSoonWindow) {
    this.credentialExpiringSoonWindow = credentialExpiringSoonWindow;
  }
}

package com.smartdroneinspection.workforce.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment-overridable presentation thresholds for workforce credentials. */
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

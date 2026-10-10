package com.smartdroneinspection.assets.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Operational thresholds for organization asset and Drone management.
 *
 * <p>The Drone document upload cap and its accepted content types are deployment policy rather than
 * domain rules, so they are configuration and can be tightened per environment.
 */
@ConfigurationProperties(prefix = "app.assets")
public class AssetProperties {

  private DataSize droneDocumentMaxFileSize = DataSize.ofMegabytes(10);

  public DataSize getDroneDocumentMaxFileSize() {
    return droneDocumentMaxFileSize;
  }

  public void setDroneDocumentMaxFileSize(DataSize droneDocumentMaxFileSize) {
    this.droneDocumentMaxFileSize = droneDocumentMaxFileSize;
  }
}

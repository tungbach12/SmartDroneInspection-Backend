package com.smartdroneinspection.inspections.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.evidence")
public class EvidenceUploadProperties {

  private long maxImageSizeBytes = 10L * 1024 * 1024;
  private long maxVideoSizeBytes = 2L * 1024 * 1024 * 1024;
  private long maxSingleUploadVideoSizeBytes = 20L * 1024 * 1024;
  private int chunkSizeBytes = 10 * 1024 * 1024;
  private Duration uploadSessionTtl = Duration.ofHours(24);
  private Duration mediaTicketTtl = Duration.ofMinutes(5);
  private String mediaTicketSecret = "";

  public long getMaxImageSizeBytes() {
    return maxImageSizeBytes;
  }

  public void setMaxImageSizeBytes(long maxImageSizeBytes) {
    this.maxImageSizeBytes = maxImageSizeBytes;
  }

  public long getMaxVideoSizeBytes() {
    return maxVideoSizeBytes;
  }

  public void setMaxVideoSizeBytes(long maxVideoSizeBytes) {
    this.maxVideoSizeBytes = maxVideoSizeBytes;
  }

  public long getMaxSingleUploadVideoSizeBytes() {
    return maxSingleUploadVideoSizeBytes;
  }

  public void setMaxSingleUploadVideoSizeBytes(long maxSingleUploadVideoSizeBytes) {
    this.maxSingleUploadVideoSizeBytes = maxSingleUploadVideoSizeBytes;
  }

  public int getChunkSizeBytes() {
    return chunkSizeBytes;
  }

  public void setChunkSizeBytes(int chunkSizeBytes) {
    this.chunkSizeBytes = chunkSizeBytes;
  }

  public Duration getUploadSessionTtl() {
    return uploadSessionTtl;
  }

  public void setUploadSessionTtl(Duration uploadSessionTtl) {
    this.uploadSessionTtl = uploadSessionTtl;
  }

  public Duration getMediaTicketTtl() {
    return mediaTicketTtl;
  }

  public void setMediaTicketTtl(Duration mediaTicketTtl) {
    this.mediaTicketTtl = mediaTicketTtl;
  }

  public String getMediaTicketSecret() {
    return mediaTicketSecret;
  }

  public void setMediaTicketSecret(String mediaTicketSecret) {
    this.mediaTicketSecret = mediaTicketSecret;
  }
}

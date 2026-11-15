package com.smartdroneinspection.infrastructure.media;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.evidence.processing")
public record MediaProcessingProperties(
    String ffmpegBinary,
    String ffprobeBinary,
    Duration commandTimeout,
    int maxConcurrentJobs,
    boolean enabled) {

  public MediaProcessingProperties {
    if (ffmpegBinary == null || ffmpegBinary.isBlank()) {
      ffmpegBinary = "ffmpeg";
    }
    if (ffprobeBinary == null || ffprobeBinary.isBlank()) {
      ffprobeBinary = "ffprobe";
    }
    if (commandTimeout == null || commandTimeout.isNegative() || commandTimeout.isZero()) {
      commandTimeout = Duration.ofMinutes(15);
    }
    if (maxConcurrentJobs <= 0) {
      maxConcurrentJobs = 2;
    }
  }
}

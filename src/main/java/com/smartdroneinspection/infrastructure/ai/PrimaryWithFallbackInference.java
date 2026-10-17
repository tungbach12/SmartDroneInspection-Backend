package com.smartdroneinspection.infrastructure.ai;

import com.smartdroneinspection.inspections.spi.AiInferencePort;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Advisory detection with an optional second detector behind it.
 *
 * <p>MF3-05 keeps the self-hosted YOLO detector as the default and treats the compatible vision
 * endpoint as an option: when it is configured it answers only for images the primary detector
 * could not process, so a detector outage degrades the advisory step instead of removing it. An
 * image the primary detector processed with no detections is a result, not a failure, and is not
 * retried against the fallback.
 */
class PrimaryWithFallbackInference implements AiInferencePort {

  private static final Logger log = LoggerFactory.getLogger(PrimaryWithFallbackInference.class);

  private final AiInferencePort primary;
  private final Optional<AiInferencePort> fallback;

  PrimaryWithFallbackInference(AiInferencePort primary, Optional<AiInferencePort> fallback) {
    this.primary = primary;
    this.fallback = fallback;
  }

  /** The detector tried first. */
  AiInferencePort primary() {
    return primary;
  }

  /** The detector that answers when the primary one cannot. */
  Optional<AiInferencePort> fallback() {
    return fallback;
  }

  @Override
  public List<Detection> analyze(byte[] image, String contentType) throws IOException {
    try {
      return primary.analyze(image, contentType);
    } catch (IOException failure) {
      if (fallback.isEmpty()) {
        throw failure;
      }
      log.warn(
          "Primary detector failed ({}); using the configured fallback detector",
          failure.getMessage());
      return fallback.get().analyze(image, contentType);
    }
  }
}

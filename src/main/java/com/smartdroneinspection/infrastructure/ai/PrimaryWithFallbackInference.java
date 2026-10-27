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
 * <p>MF3-05 keeps the self-hosted YOLO detector as the default and uses the compatible vision
 * endpoint when it is configured and YOLO either cannot process an image or returns no detections.
 * A human still reviews all results before they become findings.
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
    return analyze(image, contentType, true);
  }

  @Override
  public List<Detection> analyze(byte[] image, String contentType, boolean fallbackEnabled)
      throws IOException {
    List<Detection> detections;
    try {
      detections = primary.analyze(image, contentType);
    } catch (IOException failure) {
      if (!fallbackEnabled || fallback.isEmpty()) {
        throw failure;
      }
      log.warn(
          "Primary detector failed ({}); using the configured fallback detector",
          failure.getMessage());
      return fallback.get().analyze(image, contentType);
    }

    if (detections.isEmpty() && fallbackEnabled && fallback.isPresent()) {
      log.info(
          "Primary detector returned no detections; checking with the configured vision fallback");
      return fallback.get().analyze(image, contentType);
    }
    return detections;
  }
}

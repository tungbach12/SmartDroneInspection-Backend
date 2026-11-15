package com.smartdroneinspection.inspections.spi;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Outbound port for asynchronous video media transcoding and poster extraction.
 *
 * <p>Implementations live in {@code infrastructure/media} and keep external process execution out
 * of the {@code inspections} domain/service module.
 */
public interface VideoTranscodePort {

  TranscodeResult transcodeTo1080pFaststart(Path rawVideoFile, Path workDirectory)
      throws IOException;

  record TranscodeResult(
      Path processedVideoFile,
      String processedContentType,
      long processedSizeBytes,
      Path posterFile,
      String posterContentType,
      long posterSizeBytes,
      int width,
      int height,
      double durationSeconds) {}
}

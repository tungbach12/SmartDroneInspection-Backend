package com.smartdroneinspection.inspections.spi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Outbound port for asynchronous video media transcoding, frame extraction, and poster creation.
 *
 * <p>Implementations live in {@code infrastructure/media} and keep external process execution out
 * of the {@code inspections} domain/service module.
 */
public interface VideoTranscodePort {

  TranscodeResult transcodeTo1080pFaststart(Path rawVideoFile, Path workDirectory)
      throws IOException;

  default List<SampledFrame> sampleFrames(
      Path videoFile, Path workDirectory, double intervalSeconds, int maxFrames)
      throws IOException {
    throw new UnsupportedOperationException("Video frame sampling is not supported");
  }

  default byte[] extractFrameAtTimestamp(
      Path videoFile, double timestampSeconds, Path workDirectory) throws IOException {
    throw new UnsupportedOperationException("Video frame extraction is not supported");
  }

  record SampledFrame(double timestampSeconds, Path imageFile) {}

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

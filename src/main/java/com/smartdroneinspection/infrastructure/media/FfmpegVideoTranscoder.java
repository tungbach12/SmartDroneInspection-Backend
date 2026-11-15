package com.smartdroneinspection.infrastructure.media;

import com.smartdroneinspection.inspections.spi.VideoTranscodePort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * FFmpeg-backed {@link VideoTranscodePort} adapter. Uses argument arrays with {@link
 * ProcessBuilder} (no shell interpolation), bounded timeouts, and explicit output validation.
 */
public class FfmpegVideoTranscoder implements VideoTranscodePort {

  private final MediaProcessingProperties properties;
  private final ObjectMapper objectMapper;

  public FfmpegVideoTranscoder(MediaProcessingProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  public TranscodeResult transcodeTo1080pFaststart(Path rawVideoFile, Path workDirectory)
      throws IOException {
    if (!Files.isRegularFile(rawVideoFile) || Files.size(rawVideoFile) <= 0) {
      throw new IOException("Raw video source file is missing or empty");
    }
    Files.createDirectories(workDirectory);

    Path processedFile = workDirectory.resolve("processed.mp4");
    Path posterFile = workDirectory.resolve("poster.jpg");

    runCommand(
        List.of(
            properties.ffmpegBinary(),
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            rawVideoFile.toAbsolutePath().toString(),
            "-vf",
            "scale='min(1920,iw)':'min(1080,ih)':force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2",
            "-c:v",
            "libx264",
            "-preset",
            "veryfast",
            "-crf",
            "23",
            "-pix_fmt",
            "yuv420p",
            "-movflags",
            "+faststart",
            "-c:a",
            "aac",
            "-b:a",
            "128k",
            "-map",
            "0:v:0",
            "-map",
            "0:a?",
            processedFile.toAbsolutePath().toString()),
        properties.commandTimeout());

    if (!Files.isRegularFile(processedFile) || Files.size(processedFile) <= 0) {
      throw new IOException("FFmpeg did not produce a valid processed MP4 file");
    }

    VideoProbe metadata = probeVideo(processedFile);

    // Extract poster near 1 second, falling back to the first frame for short clips.
    String seekTimestamp = metadata.durationSeconds() >= 1.5 ? "00:00:01" : "00:00:00";
    runCommand(
        List.of(
            properties.ffmpegBinary(),
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-ss",
            seekTimestamp,
            "-i",
            processedFile.toAbsolutePath().toString(),
            "-frames:v",
            "1",
            "-vf",
            "scale='min(480,iw)':'min(480,ih)':force_original_aspect_ratio=decrease",
            "-q:v",
            "4",
            posterFile.toAbsolutePath().toString()),
        Duration.ofMinutes(2));

    if (!Files.isRegularFile(posterFile) || Files.size(posterFile) <= 0) {
      // Fallback to frame 0 if seek missed a valid frame.
      runCommand(
          List.of(
              properties.ffmpegBinary(),
              "-hide_banner",
              "-loglevel",
              "error",
              "-y",
              "-i",
              processedFile.toAbsolutePath().toString(),
              "-frames:v",
              "1",
              "-vf",
              "scale='min(480,iw)':'min(480,ih)':force_original_aspect_ratio=decrease",
              "-q:v",
              "4",
              posterFile.toAbsolutePath().toString()),
          Duration.ofMinutes(2));
    }

    if (!Files.isRegularFile(posterFile) || Files.size(posterFile) <= 0) {
      throw new IOException("FFmpeg did not produce a valid video poster image");
    }

    return new TranscodeResult(
        processedFile,
        "video/mp4",
        Files.size(processedFile),
        posterFile,
        "image/jpeg",
        Files.size(posterFile),
        metadata.width(),
        metadata.height(),
        metadata.durationSeconds());
  }

  private VideoProbe probeVideo(Path videoFile) throws IOException {
    String json =
        runCommand(
            List.of(
                properties.ffprobeBinary(),
                "-v",
                "error",
                "-select_streams",
                "v:0",
                "-show_entries",
                "stream=width,height:format=duration",
                "-of",
                "json",
                videoFile.toAbsolutePath().toString()),
            Duration.ofMinutes(1));
    try {
      JsonNode root = objectMapper.readTree(json);
      JsonNode streams = root.path("streams");
      if (!streams.isArray() || streams.isEmpty()) {
        throw new IOException("Processed video contains no video stream");
      }
      int width = streams.get(0).path("width").asInt(0);
      int height = streams.get(0).path("height").asInt(0);
      double duration = root.path("format").path("duration").asDouble(0.0);
      if (width <= 0 || height <= 0) {
        throw new IOException("Processed video has invalid dimensions");
      }
      return new VideoProbe(width, height, Math.max(0.0, duration));
    } catch (IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Could not parse ffprobe output", ex);
    }
  }

  private String runCommand(List<String> command, Duration timeout) throws IOException {
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(false);
    Process process = builder.start();

    String stdout;
    String stderr;
    try (var outStream = process.getInputStream();
        var errStream = process.getErrorStream()) {
      byte[] outBytes = outStream.readAllBytes();
      byte[] errBytes = errStream.readAllBytes();
      boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new IOException("Media processing command timed out after " + timeout);
      }
      stdout = new String(outBytes, StandardCharsets.UTF_8);
      stderr = new String(errBytes, StandardCharsets.UTF_8);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      throw new IOException("Media processing interrupted", ex);
    }

    if (process.exitValue() != 0) {
      String summary = stderr.isBlank() ? "exit code " + process.exitValue() : stderr.trim();
      if (summary.length() > 500) {
        summary = summary.substring(0, 500);
      }
      throw new IOException("FFmpeg command failed: " + summary);
    }
    return stdout;
  }

  private record VideoProbe(int width, int height, double durationSeconds) {}
}

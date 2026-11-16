package com.smartdroneinspection.infrastructure.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.spi.VideoTranscodePort;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class FfmpegVideoTranscoderTest {

  @TempDir Path tempDir;

  @Test
  void transcodesOversizedSyntheticVideoTo1080pFaststartAndPoster() throws Exception {
    Path rawVideo = tempDir.resolve("input-4k-ish.mp4");
    Process generate =
        new ProcessBuilder(
                List.of(
                    "ffmpeg",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-y",
                    "-f",
                    "lavfi",
                    "-i",
                    "testsrc=duration=2:size=2560x1440:rate=10",
                    "-f",
                    "lavfi",
                    "-i",
                    "sine=frequency=440:duration=2",
                    "-c:v",
                    "libx264",
                    "-preset",
                    "ultrafast",
                    "-pix_fmt",
                    "yuv420p",
                    "-c:a",
                    "aac",
                    rawVideo.toAbsolutePath().toString()))
            .start();
    assertThat(generate.waitFor()).isZero();

    FfmpegVideoTranscoder transcoder =
        new FfmpegVideoTranscoder(
            new MediaProcessingProperties("ffmpeg", "ffprobe", Duration.ofMinutes(2), 1, true),
            new ObjectMapper());

    Path workDir = tempDir.resolve("out");
    VideoTranscodePort.TranscodeResult result =
        transcoder.transcodeTo1080pFaststart(rawVideo, workDir);

    assertThat(result.processedContentType()).isEqualTo("video/mp4");
    assertThat(result.posterContentType()).isEqualTo("image/jpeg");
    assertThat(result.width()).isLessThanOrEqualTo(1920);
    assertThat(result.height()).isLessThanOrEqualTo(1080);
    assertThat(result.durationSeconds()).isGreaterThan(1.0);
    assertThat(Files.size(result.processedVideoFile())).isEqualTo(result.processedSizeBytes());
    assertThat(Files.size(result.posterFile())).isEqualTo(result.posterSizeBytes());

    BufferedImage poster = ImageIO.read(result.posterFile().toFile());
    assertThat(poster).isNotNull();
    assertThat(Math.max(poster.getWidth(), poster.getHeight())).isLessThanOrEqualTo(480);
  }

  @Test
  void throwsIoExceptionOnCorruptVideoInput() throws Exception {
    Path corrupt = tempDir.resolve("corrupt.mp4");
    Files.writeString(corrupt, "not a real mp4 container");
    FfmpegVideoTranscoder transcoder =
        new FfmpegVideoTranscoder(
            new MediaProcessingProperties("ffmpeg", "ffprobe", Duration.ofSeconds(30), 1, true),
            new ObjectMapper());

    assertThatThrownBy(() -> transcoder.transcodeTo1080pFaststart(corrupt, tempDir.resolve("work")))
        .isInstanceOf(IOException.class);
  }

  @Test
  void samplesFramesAtRequestedIntervalAndExtractsSingleFrame() throws Exception {
    Path rawVideo = tempDir.resolve("orbit-sample.mp4");
    Process generate =
        new ProcessBuilder(
                List.of(
                    "ffmpeg",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-y",
                    "-f",
                    "lavfi",
                    "-i",
                    "testsrc=duration=3:size=640x360:rate=10",
                    "-c:v",
                    "libx264",
                    "-preset",
                    "ultrafast",
                    "-pix_fmt",
                    "yuv420p",
                    rawVideo.toAbsolutePath().toString()))
            .start();
    assertThat(generate.waitFor()).isZero();

    FfmpegVideoTranscoder transcoder =
        new FfmpegVideoTranscoder(
            new MediaProcessingProperties("ffmpeg", "ffprobe", Duration.ofMinutes(2), 1, true),
            new ObjectMapper());

    List<VideoTranscodePort.SampledFrame> sampled =
        transcoder.sampleFrames(rawVideo, tempDir.resolve("sample-work"), 1.0, 10);
    assertThat(sampled).hasSizeGreaterThanOrEqualTo(3);
    assertThat(sampled.get(0).timestampSeconds()).isEqualTo(0.0);
    assertThat(sampled.get(1).timestampSeconds()).isEqualTo(1.0);
    assertThat(Files.size(sampled.get(0).imageFile())).isGreaterThan(0);

    byte[] extracted =
        transcoder.extractFrameAtTimestamp(rawVideo, 1.5, tempDir.resolve("extract-work"));
    assertThat(extracted.length).isGreaterThan(100);
    BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(extracted));
    assertThat(image).isNotNull();
    assertThat(image.getWidth()).isEqualTo(640);
    assertThat(image.getHeight()).isEqualTo(360);
  }
}

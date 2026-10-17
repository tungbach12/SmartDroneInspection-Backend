package com.smartdroneinspection.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.spi.AiInferencePort;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PrimaryWithFallbackInferenceTest {

  private static final AiInferencePort.Detection DETECTION =
      new AiInferencePort.Detection("model", "v1", "crack", BigDecimal.valueOf(0.9), "{}");

  @Test
  void answersFromThePrimaryDetectorWithoutTouchingTheFallback() throws IOException {
    RecordingPort primary = new RecordingPort(List.of(DETECTION), null);
    RecordingPort fallback = new RecordingPort(List.of(), null);

    List<AiInferencePort.Detection> detections =
        new PrimaryWithFallbackInference(primary, Optional.of(fallback))
            .analyze(new byte[] {1}, "image/jpeg");

    assertThat(detections).containsExactly(DETECTION);
    assertThat(primary.calls).isEqualTo(1);
    assertThat(fallback.calls).isZero();
  }

  @Test
  void anEmptyResultIsAnAnswerAndDoesNotReachTheFallback() throws IOException {
    RecordingPort primary = new RecordingPort(List.of(), null);
    RecordingPort fallback = new RecordingPort(List.of(DETECTION), null);

    List<AiInferencePort.Detection> detections =
        new PrimaryWithFallbackInference(primary, Optional.of(fallback))
            .analyze(new byte[] {1}, "image/jpeg");

    assertThat(detections).isEmpty();
    assertThat(fallback.calls).isZero();
  }

  @Test
  void fallsBackWhenThePrimaryDetectorCannotAnswer() throws IOException {
    RecordingPort primary = new RecordingPort(List.of(), new IOException("detector is down"));
    RecordingPort fallback = new RecordingPort(List.of(DETECTION), null);

    List<AiInferencePort.Detection> detections =
        new PrimaryWithFallbackInference(primary, Optional.of(fallback))
            .analyze(new byte[] {1}, "image/jpeg");

    assertThat(detections).containsExactly(DETECTION);
    assertThat(fallback.calls).isEqualTo(1);
  }

  @Test
  void reportsThePrimaryFailureWhenNoFallbackIsConfigured() {
    RecordingPort primary = new RecordingPort(List.of(), new IOException("detector is down"));

    assertThatThrownBy(
            () ->
                new PrimaryWithFallbackInference(primary, Optional.empty())
                    .analyze(new byte[] {1}, "image/jpeg"))
        .isInstanceOf(IOException.class)
        .hasMessage("detector is down");
  }

  private static final class RecordingPort implements AiInferencePort {

    private final List<Detection> answer;
    private final IOException failure;
    private int calls;

    private RecordingPort(List<Detection> answer, IOException failure) {
      this.answer = answer;
      this.failure = failure;
    }

    @Override
    public List<Detection> analyze(byte[] image, String contentType) throws IOException {
      calls++;
      if (failure != null) {
        throw failure;
      }
      return answer;
    }
  }
}

package com.smartdroneinspection.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class YoloInferenceConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(YoloInferenceConfiguration.class);

  @Test
  void isDisabledByDefault() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(YoloInferenceClient.class));
  }

  @Test
  void createsInferenceAdapterWithValidEndpointAndTimeouts() {
    contextRunner
        .withPropertyValues(
            "app.ai.yolo.enabled=true",
            "app.ai.yolo.base-url=http://localhost:8000",
            "app.ai.yolo.predict-path=/predict",
            "app.ai.yolo.connect-timeout=2s",
            "app.ai.yolo.read-timeout=30s")
        .run(context -> assertThat(context).hasSingleBean(YoloInferenceClient.class));
  }

  @Test
  void rejectsInvalidInferenceTimeoutsWhenEnabled() {
    contextRunner
        .withPropertyValues(
            "app.ai.yolo.enabled=true",
            "app.ai.yolo.base-url=http://localhost:8000",
            "app.ai.yolo.predict-path=/predict",
            "app.ai.yolo.connect-timeout=0s",
            "app.ai.yolo.read-timeout=30s")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .hasRootCauseMessage(
                      "YOLO inference endpoint and positive timeouts are required.");
            });
  }
}

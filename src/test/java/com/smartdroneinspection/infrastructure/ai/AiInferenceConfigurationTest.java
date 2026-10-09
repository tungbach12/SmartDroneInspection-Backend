package com.smartdroneinspection.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.inspections.spi.AiInferencePort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiInferenceConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(AiInferenceConfiguration.class, YoloInferenceConfiguration.class);

  @Test
  void noInferenceAdapterExistsUnlessAProviderIsEnabled() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(AiInferencePort.class);
          assertThat(context).doesNotHaveBean(CompatibleVisionProperties.class);
          assertThat(context).doesNotHaveBean(YoloInferenceProperties.class);
        });
  }

  @Test
  void theCompatibleChatProviderBecomesTheInferenceAdapter() {
    contextRunner
        .withPropertyValues(
            "app.ai.compatible-vision.enabled=true",
            "app.ai.compatible-vision.base-url=https://vision.example.test",
            "app.ai.compatible-vision.model=capstone",
            "app.ai.compatible-vision.api-key=key",
            "app.ai.compatible-vision.timeout=60s")
        .run(context -> assertThat(context).hasSingleBean(AiInferencePort.class));
  }

  @Test
  void bothProvidersCannotBeEnabledAtOnce() {
    contextRunner
        .withPropertyValues(
            "app.ai.yolo.enabled=true",
            "app.ai.yolo.base-url=http://localhost:8000",
            "app.ai.yolo.predict-path=/predict",
            "app.ai.yolo.connect-timeout=2s",
            "app.ai.yolo.read-timeout=30s",
            "app.ai.compatible-vision.enabled=true",
            "app.ai.compatible-vision.base-url=https://vision.example.test",
            "app.ai.compatible-vision.model=capstone",
            "app.ai.compatible-vision.api-key=key",
            "app.ai.compatible-vision.timeout=60s")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(
                        "Only one advisory image inference provider may be enabled at a time."));
  }

  @Test
  void theCompatibleChatProviderRejectsIncompleteSettings() {
    contextRunner
        .withPropertyValues(
            "app.ai.compatible-vision.enabled=true",
            "app.ai.compatible-vision.base-url=https://vision.example.test",
            "app.ai.compatible-vision.timeout=60s")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage(
                        "Compatible vision endpoint, model name, API key, and positive timeout are required."));
  }
}

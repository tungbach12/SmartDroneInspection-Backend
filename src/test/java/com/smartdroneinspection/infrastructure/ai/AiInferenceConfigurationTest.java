package com.smartdroneinspection.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.inspections.spi.AiInferencePort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiInferenceConfigurationTest {

  private static final String[] YOLO_SETTINGS = {
    "app.ai.yolo.enabled=true",
    "app.ai.yolo.base-url=http://localhost:8000",
    "app.ai.yolo.predict-path=/predict",
    "app.ai.yolo.connect-timeout=2s",
    "app.ai.yolo.read-timeout=30s"
  };

  private static final String[] VISION_SETTINGS = {
    "app.ai.compatible-vision.enabled=true",
    "app.ai.compatible-vision.base-url=https://vision.example.test",
    "app.ai.compatible-vision.model=capstone",
    "app.ai.compatible-vision.api-key=key",
    "app.ai.compatible-vision.timeout=60s"
  };

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              AiInferenceConfiguration.class,
              YoloInferenceConfiguration.class,
              AiInferenceRoutingConfiguration.class);

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
  void theCompatibleChatProviderAnswersWhenNoDetectorIsConfigured() {
    contextRunner
        .withPropertyValues(VISION_SETTINGS)
        .run(
            context -> {
              PrimaryWithFallbackInference routing =
                  (PrimaryWithFallbackInference) context.getBean(AiInferencePort.class);
              assertThat(routing.primary()).isInstanceOf(OpenAiCompatibleInferenceClient.class);
              assertThat(routing.fallback()).isEmpty();
            });
  }

  @Test
  void theDetectorIsTheDefaultAndTheVisionEndpointIsTheOptionBehindIt() {
    contextRunner
        .withPropertyValues(YOLO_SETTINGS)
        .withPropertyValues(VISION_SETTINGS)
        .run(
            context -> {
              assertThat(context.getStartupFailure()).isNull();
              PrimaryWithFallbackInference routing =
                  (PrimaryWithFallbackInference) context.getBean(AiInferencePort.class);
              assertThat(routing.primary()).isInstanceOf(YoloInferenceClient.class);
              assertThat(routing.fallback().orElseThrow())
                  .isInstanceOf(OpenAiCompatibleInferenceClient.class);
            });
  }

  @Test
  void theReportDraftModelIsReusedAsTheVisionFallbackWhenNoSeparateVisionSettingsAreGiven() {
    contextRunner
        .withPropertyValues(YOLO_SETTINGS)
        .withPropertyValues(
            "app.report-draft.enabled=true",
            "app.report-draft.base-url=https://report.example.test",
            "app.report-draft.model=vision-report-model",
            "app.report-draft.api-key=report-key",
            "app.report-draft.timeout=45s",
            "app.ai.compatible-vision.enabled=${app.report-draft.enabled}",
            "app.ai.compatible-vision.base-url=${app.report-draft.base-url}",
            "app.ai.compatible-vision.model=${app.report-draft.model}",
            "app.ai.compatible-vision.api-key=${app.report-draft.api-key}",
            "app.ai.compatible-vision.timeout=${app.report-draft.timeout}")
        .withUserConfiguration(ReportDraftConfiguration.class)
        .run(
            context -> {
              assertThat(context.getStartupFailure()).isNull();
              PrimaryWithFallbackInference routing =
                  (PrimaryWithFallbackInference) context.getBean(AiInferencePort.class);
              assertThat(routing.primary()).isInstanceOf(YoloInferenceClient.class);
              assertThat(routing.fallback().orElseThrow())
                  .isInstanceOf(OpenAiCompatibleInferenceClient.class);
            });
  }

  @Test
  void theDetectorAnswersAloneWhenTheVisionOptionIsDisabled() {
    contextRunner
        .withPropertyValues(YOLO_SETTINGS)
        .withPropertyValues("app.report-draft.enabled=false")
        .run(
            context -> {
              PrimaryWithFallbackInference routing =
                  (PrimaryWithFallbackInference) context.getBean(AiInferencePort.class);
              assertThat(routing.primary()).isInstanceOf(YoloInferenceClient.class);
              assertThat(routing.fallback()).isEmpty();
            });
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

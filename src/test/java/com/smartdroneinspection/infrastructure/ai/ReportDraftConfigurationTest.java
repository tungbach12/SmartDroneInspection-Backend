package com.smartdroneinspection.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.inspections.spi.ReportDraftPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

/**
 * The draft client must not exist unless it is fully configured. A partially configured drafting
 * service would otherwise surface at the first manual report, after the Inspector has already
 * gathered evidence, rather than at startup.
 */
class ReportDraftConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(ReportDraftConfiguration.class);

  @Test
  void noDraftClientExistsUnlessDraftingIsEnabled() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(ReportDraftPort.class);
          assertThat(context).doesNotHaveBean(ReportDraftProperties.class);
        });
  }

  @Test
  void aFullyConfiguredEndpointBecomesTheDraftPort() {
    contextRunner
        .withPropertyValues(
            "app.report-draft.enabled=true",
            "app.report-draft.base-url=https://draft.example.test",
            "app.report-draft.model=draft-model",
            "app.report-draft.api-key=key",
            "app.report-draft.timeout=60s")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ReportDraftPort.class);
              assertThat(context).hasSingleBean(RestClient.class);
            });
  }

  @Test
  void draftingIsRefusedWhenTheApiKeyIsMissing() {
    contextRunner
        .withPropertyValues(
            "app.report-draft.enabled=true",
            "app.report-draft.base-url=https://draft.example.test",
            "app.report-draft.model=draft-model",
            "app.report-draft.timeout=60s")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage(
                        "Report draft endpoint, API key, model name, and positive timeout are required."));
  }

  @Test
  void draftingIsRefusedWithoutAnEndpoint() {
    contextRunner
        .withPropertyValues(
            "app.report-draft.enabled=true",
            "app.report-draft.model=draft-model",
            "app.report-draft.api-key=key",
            "app.report-draft.timeout=60s")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage(
                        "Report draft endpoint, API key, model name, and positive timeout are required."));
  }

  @Test
  void draftingIsRefusedWithoutAModelName() {
    contextRunner
        .withPropertyValues(
            "app.report-draft.enabled=true",
            "app.report-draft.base-url=https://draft.example.test",
            "app.report-draft.api-key=key",
            "app.report-draft.timeout=60s")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage(
                        "Report draft endpoint, API key, model name, and positive timeout are required."));
  }

  /** A zero timeout would fail every request at runtime rather than refusing to start. */
  @Test
  void draftingIsRefusedWhenTheTimeoutIsNotPositive() {
    contextRunner
        .withPropertyValues(
            "app.report-draft.enabled=true",
            "app.report-draft.base-url=https://draft.example.test",
            "app.report-draft.model=draft-model",
            "app.report-draft.api-key=key",
            "app.report-draft.timeout=0s")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage(
                        "Report draft endpoint, API key, model name, and positive timeout are required."));
  }
}

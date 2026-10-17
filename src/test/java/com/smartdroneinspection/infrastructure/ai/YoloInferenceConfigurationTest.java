package com.smartdroneinspection.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
  void theDetectorClientSpeaksHttp11SoAnAsgiServerAcceptsTheRequest() throws Exception {
    // The ai-inference service is served by uvicorn, which answers the JDK client's default h2c
    // upgrade attempt on a plain-http endpoint with 400 "Unsupported upgrade request". This server
    // refuses the upgrade the same way, so the assertion fails if the client stops pinning
    // HTTP/1.1.
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/predict",
        exchange -> {
          boolean upgradeAttempt = exchange.getRequestHeaders().getFirst("Upgrade") != null;
          byte[] body = (upgradeAttempt ? "unsupported" : "[]").getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(upgradeAttempt ? 400 : 200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      contextRunner
          .withPropertyValues(
              "app.ai.yolo.enabled=true",
              "app.ai.yolo.base-url=http://127.0.0.1:" + server.getAddress().getPort(),
              "app.ai.yolo.predict-path=/predict",
              "app.ai.yolo.connect-timeout=2s",
              "app.ai.yolo.read-timeout=10s")
          .run(
              context ->
                  assertThat(
                          context
                              .getBean(YoloInferenceClient.class)
                              .analyze(new byte[] {1}, "image/png"))
                      .isEmpty());
    } finally {
      server.stop(0);
    }
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

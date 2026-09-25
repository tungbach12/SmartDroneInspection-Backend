package com.smartdroneinspection.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class YoloInferenceClientTest {

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void postsImageBytesAndMapsDetectionProvenance() throws Exception {
    AtomicReference<String> contentType = new AtomicReference<>();
    AtomicReference<byte[]> requestBody = new AtomicReference<>();
    startServer(
        exchange -> {
          contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
          requestBody.set(exchange.getRequestBody().readAllBytes());
          respond(
              exchange,
              200,
              "[{\"modelName\":\"yolo\",\"modelVersion\":\"v3\","
                  + "\"predictedLabel\":\"crack\",\"confidence\":0.91,"
                  + "\"boundingBox\":\"{\\\"x\\\":0.1}\"}]");
        });
    byte[] image = new byte[] {1, 2, 3, 4};

    var detections = client().analyze(image, "image/png");

    assertThat(contentType.get()).isEqualTo("image/png");
    assertThat(requestBody.get()).containsExactly(image);
    assertThat(detections)
        .singleElement()
        .satisfies(
            detection -> {
              assertThat(detection.modelName()).isEqualTo("yolo");
              assertThat(detection.modelVersion()).isEqualTo("v3");
              assertThat(detection.predictedLabel()).isEqualTo("crack");
              assertThat(detection.confidence()).isEqualByComparingTo("0.91");
            });
  }

  @Test
  void convertsHttpFailuresAndEmptyResponsesToIoErrors() throws Exception {
    startServer(exchange -> respond(exchange, 503, "offline"));
    assertThatThrownBy(() -> client().analyze(new byte[] {1}, "image/png"))
        .isInstanceOf(IOException.class)
        .hasMessage("YOLO inference request failed");

    server.stop(0);
    startServer(
        exchange -> {
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    assertThatThrownBy(() -> client().analyze(new byte[] {1}, "image/png"))
        .isInstanceOf(IOException.class)
        .hasMessage("YOLO service returned an empty response");
  }

  private YoloInferenceClient client() {
    return new YoloInferenceClient(
        RestClient.builder().baseUrl("http://localhost:" + server.getAddress().getPort()).build(),
        "/predict");
  }

  private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/predict", handler);
    server.start();
  }

  private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}

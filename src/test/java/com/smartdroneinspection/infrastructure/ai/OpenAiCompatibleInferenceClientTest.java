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

class OpenAiCompatibleInferenceClientTest {

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void sendsTheImageAsADetectionRequestAndMapsModelProvenance() throws Exception {
    AtomicReference<String> path = new AtomicReference<>();
    AtomicReference<String> authHeader = new AtomicReference<>();
    AtomicReference<String> requestBody = new AtomicReference<>();
    startServer(
        exchange -> {
          path.set(exchange.getRequestURI().getPath());
          authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          respond(
              exchange,
              200,
              """
              {
                "model": "gemini-3.8-flash-n",
                "choices": [
                  {
                    "message": {
                      "role": "assistant",
                      "content": "{\\"detections\\":[{\\"predictedLabel\\":\\"coating blistering\\",\\"confidence\\":0.82,\\"boundingBox\\":{\\"xMin\\":0.12,\\"yMin\\":0.34,\\"xMax\\":0.41,\\"yMax\\":0.58}}]}"
                    }
                  }
                ]
              }
              """);
        });

    var detections = client("capstone", "test-key").analyze(new byte[] {1, 2, 3}, "image/png");

    assertThat(path.get()).isEqualTo("/v1/chat/completions");
    assertThat(authHeader.get()).isEqualTo("Bearer test-key");
    // Streaming stays off because compatible endpoints answer with SSE frames otherwise.
    assertThat(requestBody.get()).contains("\"stream\":false").contains("\"model\":\"capstone\"");
    assertThat(requestBody.get())
        .contains(
            "data:image/png;base64,"
                + java.util.Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
    assertThat(detections)
        .singleElement()
        .satisfies(
            detection -> {
              assertThat(detection.modelName()).isEqualTo("capstone");
              assertThat(detection.modelVersion()).isEqualTo("gemini-3.8-flash-n");
              assertThat(detection.predictedLabel()).isEqualTo("coating blistering");
              assertThat(detection.confidence()).isEqualByComparingTo("0.82");
              assertThat(detection.boundingBox()).contains("xMin").contains("yMax");
            });
  }

  @Test
  void aModelThatFindsNoDefectReturnsNoCandidates() throws Exception {
    startServer(
        exchange ->
            respond(
                exchange,
                200,
                """
                {
                  "model": "gemini-3.8-flash-n",
                  "choices": [
                    { "message": { "content": "{\\"detections\\":[]}" } }
                  ]
                }
                """));

    assertThat(client("capstone", "key").analyze(new byte[] {1}, "image/png")).isEmpty();
  }

  @Test
  void unusableModelOutputBecomesAnInferenceFailure() throws Exception {
    startServer(
        exchange ->
            respond(
                exchange,
                200,
                """
                {
                  "model": "gemini-3.8-flash-n",
                  "choices": [
                    { "message": { "content": "I can see a bridge, but not a defect." } }
                  ]
                }
                """));

    assertThatThrownBy(() -> client("capstone", "key").analyze(new byte[] {1}, "image/png"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("did not return a detection payload");

    server.stop(0);
    startServer(
        exchange ->
            respond(
                exchange,
                200,
                """
                {
                  "model": "gemini-3.8-flash-n",
                  "choices": [
                    { "message": { "content": "{\\"detections\\":[{\\"predictedLabel\\":\\"crack\\",\\"confidence\\":1.4,\\"boundingBox\\":{}}]}" } }
                  ]
                }
                """));

    assertThatThrownBy(() -> client("capstone", "key").analyze(new byte[] {1}, "image/png"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("confidence");

    server.stop(0);
    startServer(
        exchange ->
            respond(
                exchange,
                200,
                """
                {
                  "model": "gemini-3.8-flash-n",
                  "choices": [
                    { "message": { "content": "{\\"detections\\":[{\\"predictedLabel\\":\\"crack\\",\\"confidence\\":0.7,\\"boundingBox\\":{}}]}" } }
                  ]
                }
                """));

    assertThatThrownBy(() -> client("capstone", "key").analyze(new byte[] {1}, "image/png"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("bounding box");
  }

  @Test
  void rejectsBoundingBoxesWithMissingOrOutOfRangeCoordinates() throws Exception {
    startServer(
        exchange ->
            respond(
                exchange,
                200,
                """
                {
                  "model": "gemini-3.8-flash-n",
                  "choices": [
                    { "message": { "content": "{\\"detections\\":[{\\"predictedLabel\\":\\"crack\\",\\"confidence\\":0.7,\\"boundingBox\\":{\\"xMin\\":0.2,\\"yMin\\":0.1,\\"xMax\\":1.2,\\"yMax\\":0.9}}]}" } }
                  ]
                }
                """));

    assertThatThrownBy(() -> client("capstone", "key").analyze(new byte[] {1}, "image/png"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("bounding box");
  }

  @Test
  void httpFailuresBecomeInferenceFailures() throws Exception {
    startServer(exchange -> respond(exchange, 429, "rate limited"));

    assertThatThrownBy(() -> client("capstone", "key").analyze(new byte[] {1}, "image/png"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("inference request failed");
  }

  private OpenAiCompatibleInferenceClient client(String model, String apiKey) {
    return new OpenAiCompatibleInferenceClient(
        RestClient.builder().baseUrl("http://localhost:" + server.getAddress().getPort()).build(),
        apiKey,
        model,
        new tools.jackson.databind.ObjectMapper());
  }

  private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/", handler);
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

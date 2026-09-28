package com.smartdroneinspection.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.spi.ReportDraftPort.ChecklistEntrySummary;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.DraftContext;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.EvidenceSummary;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.FindingSummary;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ReportDraftClientTest {

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void postsSanitizedPromptAndExtractsChoiceMessageContent() throws Exception {
    AtomicReference<String> authHeader = new AtomicReference<>();
    AtomicReference<String> requestBody = new AtomicReference<>();
    startServer(
        exchange -> {
          authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          respond(
              exchange,
              200,
              """
              {
                "id": "chatcmpl-123",
                "object": "chat.completion",
                "choices": [
                  {
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": "Inspection Report Summary: Superficial crack noted on girder."
                    },
                    "finish_reason": "stop"
                  }
                ]
              }
              """);
        });

    DraftContext context =
        new DraftContext(
            List.of(new ChecklistEntrySummary("Expansion joint condition", "PASS", "Normal wear")),
            List.of(new FindingSummary("Surface Crack", "MEDIUM", "Hairline crack observed")),
            List.of(new EvidenceSummary("girder-01.png", "image/png")));

    ReportDraftClient client = client("test-secret-key", "gpt-4o-mini");
    String narrative = client.generateDraft(context);

    assertThat(authHeader.get()).isEqualTo("Bearer test-secret-key");
    assertThat(requestBody.get())
        .contains("Surface Crack")
        .doesNotContain("latitude")
        .doesNotContain("longitude")
        .doesNotContain("uploader")
        .doesNotContain("123e4567-e89b-12d3-a456-426614174000");
    assertThat(narrative)
        .isEqualTo("Inspection Report Summary: Superficial crack noted on girder.");
  }

  @Test
  void throwsIOExceptionOnHttpErrorOrEmptyChoices() throws Exception {
    startServer(exchange -> respond(exchange, 500, "Internal Server Error"));
    ReportDraftClient client = client("key", "model");

    DraftContext context = new DraftContext(List.of(), List.of(), List.of());

    assertThatThrownBy(() -> client.generateDraft(context))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Report draft generation failed");

    server.stop(0);
    startServer(exchange -> respond(exchange, 200, "{\"choices\":[]}"));
    ReportDraftClient emptyClient = client("key", "model");

    assertThatThrownBy(() -> emptyClient.generateDraft(context))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("empty or blank response");
  }

  @Test
  void throwsIOExceptionWhenContentIsBlank() throws Exception {
    startServer(
        exchange ->
            respond(
                exchange,
                200,
                """
                {
                  "choices": [
                    { "message": { "role": "assistant", "content": "   " } }
                  ]
                }
                """));
    ReportDraftClient client = client("key", "model");

    DraftContext context = new DraftContext(List.of(), List.of(), List.of());

    assertThatThrownBy(() -> client.generateDraft(context))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("empty or blank response");
  }

  private ReportDraftClient client(String apiKey, String model) {
    return new ReportDraftClient(
        RestClient.builder().baseUrl("http://localhost:" + server.getAddress().getPort()).build(),
        apiKey,
        model);
  }

  private void startServer(HttpHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/v1/chat/completions", handler);
    server.start();
  }

  private void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}

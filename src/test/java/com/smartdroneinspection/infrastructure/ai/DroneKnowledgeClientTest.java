package com.smartdroneinspection.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.inspections.spi.DraftFindingNoteCommand;
import com.smartdroneinspection.inspections.spi.DraftFindingNoteCommand.CaseReference;
import com.smartdroneinspection.inspections.spi.DraftFindingNoteCommand.GuidelineReference;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Verifies the advisory chat/vision payload and that every failure mode degrades cleanly. */
class DroneKnowledgeClientTest {

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void sendsImageAndRagContextAndReturnsTheDraft() throws Exception {
    AtomicReference<String> path = new AtomicReference<>();
    AtomicReference<String> authHeader = new AtomicReference<>();
    AtomicReference<String> requestBody = new AtomicReference<>();
    String expectedNote =
        "The facade crack is visible within the marked region. Reference TCVN 9343:2012 advises"
            + " classify visible cracks by width.";
    startServer(
        exchange -> {
          path.set(exchange.getRequestURI().getPath());
          authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          respond(exchange, 200, chatResponse(expectedNote));
        });

    Optional<String> note = client("draft-model").draftFindingNote(command("vi", true));

    assertThat(note).hasValue(expectedNote);
    assertThat(path.get()).isEqualTo("/v1/chat/completions");
    assertThat(authHeader.get()).isEqualTo("Bearer test-key");
    assertThat(requestBody.get())
        .contains("\"model\":\"draft-model\"")
        .contains("\"stream\":false")
        .contains("Vietnamese")
        .contains("Facade crack")
        .contains("[0.1, 0.2, 0.4, 0.6]")
        .contains("TCVN 9343:2012")
        .contains("Crack observed near the mid-span")
        .contains(
            "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
  }

  @Test
  void textOnlyDraftDoesNotAttachAnImage() throws Exception {
    AtomicReference<String> requestBody = new AtomicReference<>();
    startServer(
        exchange -> {
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          respond(exchange, 200, chatResponse("A concise note."));
        });

    assertThat(client("draft-model").draftFindingNote(command("en", false)))
        .hasValue("A concise note.");
    assertThat(requestBody.get())
        .contains("No photograph was available")
        .doesNotContain("image_url");
  }

  @Test
  void httpErrorsAndBlankOutputDegradeToAnEmptyDraft() throws Exception {
    startServer(exchange -> respond(exchange, 429, "rate limited"));
    assertThat(client("draft-model").draftFindingNote(command("en", true))).isEmpty();

    server.stop(0);
    startServer(exchange -> respond(exchange, 200, chatResponse("   ")));
    assertThat(client("draft-model").draftFindingNote(command("en", true))).isEmpty();
  }

  @Test
  void missingChatModelAndMalformedResponsesNeverFail() throws Exception {
    AtomicBoolean called = new AtomicBoolean(false);
    startServer(
        exchange -> {
          called.set(true);
          respond(exchange, 200, "not json");
        });

    assertThat(client(null).draftFindingNote(command("en", true))).isEmpty();
    assertThat(called).isFalse();
    assertThat(client("draft-model").draftFindingNote(command("en", true))).isEmpty();
    assertThat(called).isTrue();
  }

  @Test
  void embeddingsStillRoundTripAndUseDedicatedEmbeddingEndpointWhenConfigured() throws Exception {
    AtomicReference<String> embedPath = new AtomicReference<>();
    AtomicReference<String> embedAuth = new AtomicReference<>();
    startServer(
        exchange -> {
          embedPath.set(exchange.getRequestURI().getPath());
          embedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
          StringBuilder values = new StringBuilder();
          for (int i = 0; i < 384; i++) {
            if (i > 0) {
              values.append(',');
            }
            values.append(i == 0 ? "0.25" : "0");
          }
          respond(exchange, 200, "{\"data\":[{\"embedding\":[" + values + "]}]}");
        });

    DroneKnowledgeClient dedicatedClient =
        new DroneKnowledgeClient(
            null,
            null,
            RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .build(),
            "",
            "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
            null,
            new ObjectMapper());
    Optional<float[]> embedding = dedicatedClient.embed("Facade crack HIGH");

    assertThat(embedding).isPresent();
    float[] vector = embedding.orElseThrow();
    assertThat(vector).hasSize(384);
    assertThat(vector[0]).isEqualTo(0.25f);
    assertThat(embedPath.get()).isEqualTo("/v1/embeddings");
    assertThat(embedAuth.get()).isNull();
    assertThat(dedicatedClient.draftFindingNote(command("vi", false))).isEmpty();
  }

  private DroneKnowledgeClient client(String chatModel) {
    return new DroneKnowledgeClient(
        RestClient.builder().baseUrl("http://localhost:" + server.getAddress().getPort()).build(),
        "test-key",
        "embedding-model",
        chatModel,
        new ObjectMapper());
  }

  private static DraftFindingNoteCommand command(String locale, boolean withImage) {
    return new DraftFindingNoteCommand(
        "Facade crack",
        "HIGH",
        "{\"xMin\":0.1,\"yMin\":0.2,\"xMax\":0.4,\"yMax\":0.6}",
        "Crack observed near the mid-span.",
        locale,
        "HIGH_RISE_OFFICE",
        "Main tower",
        null,
        withImage ? new byte[] {1, 2, 3} : null,
        withImage ? "image/png" : null,
        List.of(
            new CaseReference(
                "Facade crack", "Observed during a prior field session", "PUBLISHED")),
        List.of(
            new GuidelineReference(
                "TCVN 9343:2012",
                "Concrete surface crack assessment",
                "Classify visible cracks by width. Record their exact location.")));
  }

  private static String chatResponse(String content) {
    String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
    return "{\"model\":\"draft-model\",\"choices\":[{\"message\":{\"content\":\""
        + escaped
        + "\"}}]}";
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

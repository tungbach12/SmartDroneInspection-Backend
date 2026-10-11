package com.smartdroneinspection.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.spi.ReportDraftPort.ChecklistEntrySummary;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.DraftContext;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.EvidenceSummary;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.FindingSummary;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.SectionDraft;
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

  /** The section values a compliant model returns, as it would in a real reply. */
  private static final String SECTION_REPLY =
      """
      {"executiveSummary":"The north span girder G4 shows coating blistering.",\
      "purposeScopeCriteria":"Objective: confirm coating integrity. Excluded: substructure.",\
      "methodEquipmentConditions":"Visual and thermal optical survey at 15m AGL.",\
      "conclusionsRecommendations":"Close inspection of G4 within 90 days.",\
      "overallCondition":"POOR",\
      "omissionDisclosure":"Submerged components were not observed."}""";

  private HttpServer server;

  /** Embeds a reply inside the chat-completion envelope, escaping it as a JSON string. */
  private static String chatCompletionBody(String content) {
    return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
        + content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        + "\"}}]}";
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void returnsEachProposedSectionAsItsOwnField() throws Exception {
    AtomicReference<String> requestBody = new AtomicReference<>();
    startServer(
        exchange -> {
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          respond(exchange, 200, chatCompletionBody(SECTION_REPLY));
        });

    SectionDraft draft = client("key", "model").draftSections(context());

    assertThat(draft.executiveSummary())
        .isEqualTo("The north span girder G4 shows coating blistering.");
    assertThat(draft.purposeScopeCriteria()).contains("confirm coating integrity");
    assertThat(draft.methodEquipmentConditions()).contains("15m AGL");
    assertThat(draft.conclusionsRecommendations()).contains("Close inspection of G4");
    assertThat(draft.overallCondition()).isEqualTo("POOR");
    assertThat(draft.omissionDisclosure()).contains("Submerged components");

    // The prompt must carry the finding's component and location, not just its label, so the model
    // can write the specific note the documentation practice requires.
    assertThat(requestBody.get())
        .contains("Surface Crack")
        .contains("Lower chord")
        .contains("Span 2")
        // Streaming must stay off, otherwise a compatible endpoint answers with SSE frames.
        .contains("\"stream\":false")
        .doesNotContain("latitude")
        .doesNotContain("longitude")
        .doesNotContain("uploader");
  }

  @Test
  void leavesAnAbsentSectionNullRatherThanFillingItWithAnEmptyValue() throws Exception {
    startServer(
        exchange ->
            respond(
                exchange,
                200,
                chatCompletionBody(
                    "{\"executiveSummary\":\"Span surveyed.\",\"overallCondition\":null}")));

    SectionDraft draft = client("key", "model").draftSections(context());

    assertThat(draft.executiveSummary()).isEqualTo("Span surveyed.");
    assertThat(draft.overallCondition()).isNull();
    assertThat(draft.purposeScopeCriteria()).isNull();
    assertThat(draft.omissionDisclosure()).isNull();
  }

  @Test
  void toleratesAMarkdownFencedJsonReply() throws Exception {
    startServer(
        exchange ->
            respond(
                exchange,
                200,
                chatCompletionBody("```json\n{\"executiveSummary\":\"Fenced but usable.\"}\n```")));

    assertThat(client("key", "model").draftSections(context()).executiveSummary())
        .isEqualTo("Fenced but usable.");
  }

  @Test
  void treatsAnUnusableReplyAsAFailureRatherThanDraftingPlaceholderText() throws Exception {
    startServer(exchange -> respond(exchange, 200, chatCompletionBody("I cannot help with that.")));

    assertThatThrownBy(() -> client("key", "model").draftSections(context()))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("did not return a JSON object");
  }

  @Test
  void throwsIOExceptionOnHttpErrorOrEmptyChoices() throws Exception {
    startServer(exchange -> respond(exchange, 500, "Internal Server Error"));
    ReportDraftClient client = client("key", "model");

    assertThatThrownBy(() -> client.draftSections(context()))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Report draft generation failed");

    server.stop(0);
    startServer(exchange -> respond(exchange, 200, "{\"choices\":[]}"));
    ReportDraftClient emptyClient = client("key", "model");

    assertThatThrownBy(() -> emptyClient.draftSections(context()))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("empty or blank response");
  }

  @Test
  void throwsIOExceptionWhenContentIsBlank() throws Exception {
    startServer(exchange -> respond(exchange, 200, chatCompletionBody("   ")));

    assertThatThrownBy(() -> client("key", "model").draftSections(context()))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("empty or blank response");
  }

  private static DraftContext context() {
    return new DraftContext(
        List.of(new ChecklistEntrySummary("Expansion joint condition", "PASS", "Normal wear")),
        List.of(
            new FindingSummary(
                "Surface Crack",
                "MEDIUM",
                "Lower chord",
                "Span 2, girder G3",
                "Hairline crack observed",
                "Monitor and re-inspect")),
        List.of(new EvidenceSummary("girder-01.png", "image/png")));
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

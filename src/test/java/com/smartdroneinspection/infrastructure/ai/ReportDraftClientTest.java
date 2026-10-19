package com.smartdroneinspection.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.spi.ReportDraftPort.ChecklistEntrySummary;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.DraftContext;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.EvidenceQualitySummary;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.EvidenceSummary;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.FindingSummary;
import com.smartdroneinspection.inspections.spi.ReportDraftPort.InspectionSummary;
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
    assertThat(draft.purposeScopeCriteria()).isNull();
    assertThat(requestBody.get()).doesNotContain("purposeScopeCriteria");
    assertThat(draft.methodEquipmentConditions()).contains("15m AGL");
    assertThat(draft.conclusionsRecommendations()).contains("Close inspection of G4");
    assertThat(draft.overallCondition()).isEqualTo("POOR");
    assertThat(draft.omissionDisclosure()).contains("Submerged components");

    // The prompt must carry the finding's component and location, not just its label, so the model
    String request = requestBody.get();
    String prompt =
        new tools.jackson.databind.ObjectMapper()
            .readTree(request)
            .path("messages")
            .get(1)
            .path("content")
            .asString();
    assertThat(prompt)
        .contains(
            "Inspector-confirmed Findings (official findings only; confirmation is not independent validation):\n- Component: Lower chord")
        .contains("Defect: Surface Crack")
        .contains("AI Detection Candidates (advisory, not official findings)")
        .contains("Candidate status (not official finding status): PENDING")
        .contains("Bounding-box JSON: {\"x\":1}")
        .contains("Recorded start: 2026-10-12T10:00:00Z")
        .contains("Assigned drone reference (not proof used): drone-1")
        .contains("Surface Crack")
        .contains("Lower chord")
        .contains("Span 2")
        .contains("Inspect main-span coating")
        .contains("North span")
        .contains("Main girder G4")
        .contains("No blistering over 50mm")
        .contains("Planned start (not actual inspection time)")
        .contains("Expansion joint condition")
        .contains("Capture time: 2026-10-12T09:30:00Z")
        .contains("GPS: 10.7769, 106.7009")
        .contains("Field session: session-1")
        .doesNotContain("latitude")
        .doesNotContain("longitude")
        .contains("Inspector-recorded Evidence Quality Assessment (not independent proof)")
        .contains("Inspector-recorded decision: ACCEPTED")
        .contains(
            "Shot-list comparison entered by Inspector: Both walkways and the central span were covered")
        .contains("Inspector-recorded limitation reason: Wind limited north access")
        .contains("not independent proof beyond the entered comparison")
        .doesNotContain("inspector-42")
        .doesNotContain("12:34:56Z")
        .doesNotContain("Inspector ID:")
        .doesNotContain("readiness-1")
        .doesNotContain("checklist-1")
        .doesNotContain("uploader");
    assertThat(request).contains("\"stream\":false");
    assertThat(
            prompt.indexOf(
                "Inspector-confirmed Findings (official findings only; confirmation is not independent validation):"))
        .isLessThan(prompt.indexOf("AI Detection Candidates (advisory, not official findings):"));
    assertThat(prompt.indexOf("AI Detection Candidates (advisory, not official findings):"))
        .isLessThan(prompt.indexOf("Recorded Field Sessions (platform records"));
    assertThat(
            prompt.substring(
                prompt.indexOf("AI Detection Candidates"),
                prompt.indexOf("Recorded Field Sessions")))
        .doesNotContain("Component: Lower chord");
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
        new InspectionSummary(
            "Inspect main-span coating",
            "{\"summary\":\"North span\"}",
            "[\"Main girder G4\"]",
            "{\"criteria\":\"No blistering over 50mm\"}",
            "2026-10-12T09:00:00Z",
            "2026-10-12T17:00:00Z",
            null),
        List.of(new ChecklistEntrySummary("Expansion joint condition", "PASS", "Normal wear")),
        List.of(
            new FindingSummary(
                "Surface Crack",
                "MEDIUM",
                "Lower chord",
                "Span 2, girder G3",
                "Hairline crack observed",
                "Monitor and re-inspect",
                null,
                null,
                null,
                null)),
        List.of(
            new EvidenceSummary(
                "girder-01.png",
                "image/png",
                "2026-10-12T09:30:00Z",
                "10.7769",
                "106.7009",
                "WEB_UPLOAD",
                "session-1",
                null,
                null)),
        List.of(
            new com.smartdroneinspection.inspections.spi.ReportDraftPort.CandidateSummary(
                "candidate-1",
                "evidence-1",
                "provider-a",
                "model-a",
                "v2",
                "Crack",
                "0.91",
                "{\"x\":1}",
                "PENDING")),
        List.of(
            new com.smartdroneinspection.inspections.spi.ReportDraftPort.FieldSessionSummary(
                "session-1",
                "IN_PROGRESS",
                "2026-10-12T10:00:00Z",
                null,
                null,
                null,
                null,
                null,
                "drone-1")),
        new EvidenceQualitySummary(
            "ACCEPTED",
            "Both walkways and the central span were covered",
            "Wind limited north access"));
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

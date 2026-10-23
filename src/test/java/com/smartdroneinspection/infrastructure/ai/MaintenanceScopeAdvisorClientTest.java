package com.smartdroneinspection.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort.KnowledgeChunkSummary;
import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort.ScopeAdvisorContext;
import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort.SuggestedScope;
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

class MaintenanceScopeAdvisorClientTest {

  private static final String ADVISOR_REPLY =
      """
      {
        "correctiveScope": "Bơm keo epoxy Sikadur-752 xử lý nứt",
        "acceptanceCriteria": "UPV phục hồi >= 90%, bám dính kéo đứt >= 1.5 MPa",
        "referenceStandards": ["TCVN 9343:2012", "TCVN 9349"],
        "suggestedTasks": [
          {"taskNumber": 1, "name": "Gắn bát bơm", "method": "Keo trám Sikadur-731", "acceptanceCriteria": "Kín khít"},
          {"taskNumber": 2, "name": "Bơm keo", "method": "Bơm áp lực thấp", "acceptanceCriteria": "Điền đầy >= 90%"}
        ]
      }
      """;

  private HttpServer server;

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
  void parsesSuggestedScopeAndTasksSuccessfully() throws Exception {
    AtomicReference<String> requestBody = new AtomicReference<>();
    startServer(
        exchange -> {
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          respond(exchange, 200, chatCompletionBody(ADVISOR_REPLY));
        });

    MaintenanceScopeAdvisorClient client =
        new MaintenanceScopeAdvisorClient(
            RestClient.builder().baseUrl("http://localhost:" + server.getAddress().getPort()).build(),
            "test-key",
            "test-model");

    ScopeAdvisorContext context =
        new ScopeAdvisorContext(
            "FND-01",
            "Nứt dầm bê tông",
            "HIGH",
            "Dầm T",
            "Nhịp chính",
            "Vết nứt dọc",
            "Trám keo",
            "0.3mm",
            List.of(
                new KnowledgeChunkSummary(
                    "TCVN 9343:2012", "Bơm keo epoxy", "CONCRETE_CRACK", "Hướng dẫn bơm keo")));

    SuggestedScope result = client.suggestScope(context);

    assertThat(result.correctiveScope()).contains("Sikadur-752");
    assertThat(result.acceptanceCriteria()).contains("1.5 MPa");
    assertThat(result.referenceStandards()).contains("TCVN 9343:2012");
    assertThat(result.suggestedTasks()).hasSize(2);
    assertThat(requestBody.get()).contains("Finding Details:").contains("Nứt dầm bê tông");
  }

  @Test
  void throwsIOExceptionOnHttpError() throws Exception {
    startServer(exchange -> respond(exchange, 500, "{\"error\": \"Internal server error\"}"));

    MaintenanceScopeAdvisorClient client =
        new MaintenanceScopeAdvisorClient(
            RestClient.builder().baseUrl("http://localhost:" + server.getAddress().getPort()).build(),
            "test-key",
            "test-model");

    ScopeAdvisorContext context =
        new ScopeAdvisorContext("FND-01", "Defect", "LOW", null, null, null, null, null, List.of());

    assertThatThrownBy(() -> client.suggestScope(context))
        .isInstanceOf(IOException.class);
  }

  private void startServer(HttpHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/v1/chat/completions", handler);
    server.start();
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}

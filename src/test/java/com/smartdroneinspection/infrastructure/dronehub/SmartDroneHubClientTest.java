package com.smartdroneinspection.infrastructure.dronehub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.spi.SmartDroneHubPort;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class SmartDroneHubClientTest {

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) server.stop(0);
  }

  @Test
  void createsMissionUsingHubEnvelopeContract() throws Exception {
    AtomicReference<String> method = new AtomicReference<>();
    AtomicReference<String> path = new AtomicReference<>();
    AtomicReference<String> body = new AtomicReference<>();
    UUID inspectionId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    startServer(
        exchange -> {
          method.set(exchange.getRequestMethod());
          path.set(exchange.getRequestURI().getPath());
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          respond(
              exchange,
              200,
              "{\"success\":true,\"message\":\"Success\",\"data\":{"
                  + "\"inspectionId\":\""
                  + inspectionId
                  + "\","
                  + "\"fieldSessionId\":\""
                  + sessionId
                  + "\","
                  + "\"missionState\":\"READY\",\"telemetrySource\":\"SMARTDRONEHUB_SIMULATION\","
                  + "\"connected\":true,\"lastUpdateAt\":null,\"latitude\":10.8,"
                  + "\"longitude\":106.7,\"altitudeMeters\":0.0,\"groundSpeedMps\":0.0,"
                  + "\"headingDeg\":null,\"batteryPercent\":100,\"flightMode\":\"SIMULATED\","
                  + "\"armed\":false,\"currentWaypoint\":0,\"distanceFlownMeters\":0.0}} ");
        });

    SmartDroneHubPort port = client();
    SmartDroneHubPort.Mission mission =
        port.createMission(
            new SmartDroneHubPort.MissionRequest(
                inspectionId, sessionId, "Bridge", null, null, List.of(), "AUTO"));

    assertThat(method.get()).isEqualTo("POST");
    assertThat(path.get()).isEqualTo("/api/v1/missions");
    assertThat(body.get()).contains(inspectionId.toString(), sessionId.toString(), "AUTO");
    assertThat(mission.inspectionId()).isEqualTo(inspectionId);
    assertThat(mission.telemetrySource()).isEqualTo("SMARTDRONEHUB_SIMULATION");
    assertThat(mission.connected()).isTrue();
  }

  @Test
  void requestsFlightReportWithPersistedFieldSessionEndTime() throws Exception {
    AtomicReference<String> path = new AtomicReference<>();
    UUID inspectionId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    java.time.Instant endedAt = java.time.Instant.parse("2026-11-02T10:15:30Z");
    startServer(
        exchange -> {
          path.set(exchange.getRequestURI().toString());
          respond(
              exchange,
              200,
              "{\"success\":true,\"message\":\"Success\",\"data\":{"
                  + "\"inspectionId\":\""
                  + inspectionId
                  + "\",\"fieldSessionId\":\""
                  + sessionId
                  + "\",\"telemetrySource\":\"SMARTDRONEHUB_SIMULATION\","
                  + "\"startedAt\":\"2026-11-02T10:00:00Z\",\"endedAt\":\""
                  + endedAt
                  + "\",\"durationSeconds\":930,\"distanceFlownMeters\":20.5,"
                  + "\"maxAltitudeMeters\":10.0,\"startBatteryPercent\":100,"
                  + "\"endBatteryPercent\":100,\"finalState\":\"LANDED\",\"note\":\"demo\"}}");
        });

    SmartDroneHubPort.FlightReport report = client().flightReport(inspectionId, sessionId, endedAt);

    assertThat(path.get())
        .isEqualTo(
            "/api/v1/missions/"
                + inspectionId
                + "/field-sessions/"
                + sessionId
                + "/flight-report?endedAt=2026-11-02T10%3A15%3A30Z");
    assertThat(report.endedAt()).isEqualTo(endedAt);
  }

  @Test
  void mapsHubHttpFailureToGatewayError() throws Exception {
    startServer(exchange -> respond(exchange, 404, "{\"detail\":\"Mission not found\"}"));
    SmartDroneHubPort port = client();
    assertThatThrownBy(() -> port.missionTelemetry(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(SmartDroneHubUnavailableException.class)
        .hasMessageContaining("HTTP 404");
  }

  @Test
  void retrievesMissionRecordingAndSnapshotFromHub() throws Exception {
    UUID inspectionId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    String base64Payload =
        java.util.Base64.getEncoder()
            .encodeToString("fake-media-bytes".getBytes(StandardCharsets.UTF_8));
    startServer(
        exchange -> {
          respond(
              exchange,
              200,
              "{\"success\":true,\"message\":\"Success\",\"data\":{"
                  + "\"fileName\":\"mission.mp4\",\"contentType\":\"video/mp4\","
                  + "\"contentBase64\":\""
                  + base64Payload
                  + "\",\"capturedAt\":\"2026-11-03T06:00:00Z\","
                  + "\"latitude\":10.762622,\"longitude\":106.660172,"
                  + "\"locationDescription\":\"Gazebo 3D Mission Video\"}}");
        });

    SmartDroneHubPort.CapturedMedia recording = client().missionRecording(inspectionId, sessionId);

    assertThat(recording.fileName()).isEqualTo("mission.mp4");
    assertThat(recording.contentType()).isEqualTo("video/mp4");
    assertThat(new String(recording.content(), StandardCharsets.UTF_8))
        .isEqualTo("fake-media-bytes");
  }

  private SmartDroneHubClient client() {
    return new SmartDroneHubClient(
        RestClient.builder().baseUrl("http://localhost:" + server.getAddress().getPort()).build());
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

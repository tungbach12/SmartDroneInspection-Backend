package com.smartdroneinspection.infrastructure.dronehub;

import com.smartdroneinspection.inspections.spi.SmartDroneHubPort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** HTTP adapter to the local telemetry-only SmartDroneHub companion service. */
public class SmartDroneHubClient implements SmartDroneHubPort {

  private static final ParameterizedTypeReference<Envelope<Mission>> MISSION_TYPE =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<Envelope<Telemetry>> TELEMETRY_TYPE =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<Envelope<FlightReport>> FLIGHT_REPORT_TYPE =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<Envelope<MediaPayload>> MEDIA_PAYLOAD_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient client;

  SmartDroneHubClient(RestClient client) {
    this.client = client;
  }

  @Override
  public Mission createMission(MissionRequest request) {
    try {
      Envelope<Mission> envelope =
          client.post().uri("/api/v1/missions").body(request).retrieve().body(MISSION_TYPE);
      if (envelope == null || envelope.data() == null) {
        throw new SmartDroneHubUnavailableException(
            "SmartDroneHub returned an empty mission response");
      }
      return envelope.data();
    } catch (RestClientResponseException exception) {
      throw mapped(exception);
    } catch (SmartDroneHubUnavailableException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw new SmartDroneHubUnavailableException(
          "SmartDroneHub mission creation failed", exception);
    }
  }

  @Override
  public Telemetry missionTelemetry(UUID inspectionId, UUID fieldSessionId) {
    try {
      Envelope<Telemetry> envelope =
          client
              .get()
              .uri(
                  "/api/v1/missions/{inspectionId}/field-sessions/{fieldSessionId}/telemetry",
                  inspectionId,
                  fieldSessionId)
              .retrieve()
              .body(TELEMETRY_TYPE);
      if (envelope == null || envelope.data() == null) {
        throw new SmartDroneHubUnavailableException("SmartDroneHub returned no mission telemetry");
      }
      Telemetry value = envelope.data();
      return new Telemetry(
          inspectionId,
          fieldSessionId,
          value.missionState(),
          value.telemetrySource(),
          value.connected(),
          value.lastUpdateAt(),
          value.latitude(),
          value.longitude(),
          value.altitudeMeters(),
          value.groundSpeedMps(),
          value.headingDeg(),
          value.batteryPercent(),
          value.flightMode(),
          value.armed(),
          value.currentWaypoint(),
          value.distanceFlownMeters());
    } catch (RestClientResponseException exception) {
      throw mapped(exception);
    } catch (SmartDroneHubUnavailableException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw new SmartDroneHubUnavailableException(
          "SmartDroneHub mission telemetry is unavailable", exception);
    }
  }

  @Override
  public FlightReport flightReport(UUID inspectionId, UUID fieldSessionId, Instant endedAt) {
    try {
      Envelope<FlightReport> envelope =
          client
              .get()
              .uri(
                  "/api/v1/missions/{inspectionId}/field-sessions/{fieldSessionId}/flight-report?endedAt={endedAt}",
                  inspectionId,
                  fieldSessionId,
                  endedAt != null ? endedAt.toString() : "")
              .retrieve()
              .body(FLIGHT_REPORT_TYPE);
      if (envelope == null || envelope.data() == null) {
        throw new SmartDroneHubUnavailableException("SmartDroneHub returned no flight report");
      }
      FlightReport report = envelope.data();
      return new FlightReport(
          inspectionId,
          fieldSessionId,
          report.telemetrySource(),
          report.startedAt(),
          report.endedAt(),
          report.durationSeconds(),
          report.distanceFlownMeters(),
          report.maxAltitudeMeters(),
          report.startBatteryPercent(),
          report.endBatteryPercent(),
          report.finalState(),
          report.note(),
          report.flightTrace() != null ? report.flightTrace() : java.util.List.of(),
          report.flightVideoEvidenceId());
    } catch (RestClientResponseException exception) {
      throw mapped(exception);
    } catch (SmartDroneHubUnavailableException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw new SmartDroneHubUnavailableException(
          "SmartDroneHub flight report is unavailable", exception);
    }
  }

  @Override
  public CapturedMedia missionRecording(UUID inspectionId, UUID fieldSessionId) {
    try {
      Envelope<MediaPayload> envelope =
          client
              .get()
              .uri(
                  "/api/v1/missions/{inspectionId}/field-sessions/{fieldSessionId}/recording",
                  inspectionId,
                  fieldSessionId)
              .retrieve()
              .body(MEDIA_PAYLOAD_TYPE);
      if (envelope == null || envelope.data() == null || envelope.data().contentBase64() == null) {
        throw new SmartDroneHubUnavailableException(
            "SmartDroneHub returned no mission recording video");
      }
      return toCapturedMedia(envelope.data());
    } catch (RestClientResponseException exception) {
      throw mapped(exception);
    } catch (SmartDroneHubUnavailableException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw new SmartDroneHubUnavailableException(
          "SmartDroneHub mission recording is unavailable", exception);
    }
  }

  @Override
  public CapturedMedia captureSnapshot(UUID inspectionId, UUID fieldSessionId) {
    try {
      Envelope<MediaPayload> envelope =
          client
              .post()
              .uri(
                  "/api/v1/missions/{inspectionId}/field-sessions/{fieldSessionId}/capture",
                  inspectionId,
                  fieldSessionId)
              .retrieve()
              .body(MEDIA_PAYLOAD_TYPE);
      if (envelope == null || envelope.data() == null || envelope.data().contentBase64() == null) {
        throw new SmartDroneHubUnavailableException("SmartDroneHub returned no camera snapshot");
      }
      return toCapturedMedia(envelope.data());
    } catch (RestClientResponseException exception) {
      throw mapped(exception);
    } catch (SmartDroneHubUnavailableException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw new SmartDroneHubUnavailableException(
          "SmartDroneHub camera snapshot is unavailable", exception);
    }
  }

  private static CapturedMedia toCapturedMedia(MediaPayload payload) {
    byte[] bytes = java.util.Base64.getDecoder().decode(payload.contentBase64());
    return new CapturedMedia(
        payload.fileName(),
        payload.contentType(),
        bytes,
        payload.capturedAt() != null ? payload.capturedAt() : Instant.now(),
        payload.latitude(),
        payload.longitude(),
        payload.locationDescription());
  }

  private record MediaPayload(
      String fileName,
      String contentType,
      String contentBase64,
      Instant capturedAt,
      java.math.BigDecimal latitude,
      java.math.BigDecimal longitude,
      String locationDescription) {}

  private static SmartDroneHubUnavailableException mapped(RestClientResponseException exception) {
    HttpStatusCode status = exception.getStatusCode();
    String detail = "SmartDroneHub request was rejected (HTTP " + status.value() + ")";
    return new SmartDroneHubUnavailableException(detail, exception);
  }

  private record Envelope<T>(boolean success, String message, T data) {}
}

package com.smartdroneinspection.inspections.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** An individual flight / sortie recorded inside a multi-flight or multi-day field session. */
@Entity
@Table(name = "field_session_flights")
public class FieldSessionFlight {

  @Id @GeneratedValue private UUID id;

  @Column(name = "field_session_id", nullable = false, updatable = false)
  private UUID fieldSessionId;

  @Column(name = "inspection_id", nullable = false, updatable = false)
  private UUID inspectionId;

  @Column(name = "flight_number", nullable = false)
  private int flightNumber;

  @Column(name = "flight_date", nullable = false)
  private LocalDate flightDate;

  @Column(name = "flight_label", length = 160)
  private String flightLabel;

  @Column(name = "telemetry_source", length = 64)
  private String telemetrySource;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "flight_duration_seconds")
  private Integer flightDurationSeconds;

  @Column(name = "distance_flown_meters", precision = 10, scale = 2)
  private BigDecimal distanceFlownMeters;

  @Column(name = "max_altitude_meters", precision = 8, scale = 2)
  private BigDecimal maxAltitudeMeters;

  @Column(name = "start_battery_percent")
  private Integer startBatteryPercent;

  @Column(name = "end_battery_percent")
  private Integer endBatteryPercent;

  @Column(name = "flight_final_state", length = 32)
  private String flightFinalState;

  @Column(name = "flight_report_note", length = 1000)
  private String flightReportNote;

  @Column(name = "flight_trace_json", columnDefinition = "TEXT")
  private String flightTraceJson;

  @Column(name = "flight_video_evidence_id")
  private UUID flightVideoEvidenceId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected FieldSessionFlight() {}

  public FieldSessionFlight(
      UUID fieldSessionId,
      UUID inspectionId,
      int flightNumber,
      LocalDate flightDate,
      String flightLabel) {
    this.fieldSessionId = Objects.requireNonNull(fieldSessionId, "Field session ID is required");
    this.inspectionId = Objects.requireNonNull(inspectionId, "Inspection ID is required");
    if (flightNumber < 1) {
      throw new IllegalArgumentException("Flight number must be >= 1");
    }
    this.flightNumber = flightNumber;
    this.flightDate = flightDate != null ? flightDate : LocalDate.now();
    this.flightLabel = normalizeLabel(flightLabel, flightNumber);
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void updateMetadata(String flightLabel, LocalDate flightDate) {
    if (flightLabel != null && !flightLabel.isBlank()) {
      this.flightLabel = normalizeLabel(flightLabel, this.flightNumber);
    }
    if (flightDate != null) {
      this.flightDate = flightDate;
    }
    this.updatedAt = Instant.now();
  }

  public void recordReport(
      String telemetrySource,
      Instant startedAt,
      Instant endedAt,
      Integer durationSeconds,
      Double distanceFlownMeters,
      Double maxAltitudeMeters,
      Integer startBatteryPercent,
      Integer endBatteryPercent,
      String finalState,
      String note,
      String flightTraceJson) {
    this.telemetrySource = telemetrySource;
    if (startedAt != null) {
      this.startedAt = startedAt;
      this.flightDate = startedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate();
    }
    if (endedAt != null) {
      this.endedAt = endedAt;
    }
    this.flightDurationSeconds = durationSeconds == null ? null : Math.max(0, durationSeconds);
    this.distanceFlownMeters =
        distanceFlownMeters == null
            ? null
            : BigDecimal.valueOf(Math.max(0.0, distanceFlownMeters))
                .setScale(2, RoundingMode.HALF_UP);
    this.maxAltitudeMeters =
        maxAltitudeMeters == null
            ? null
            : BigDecimal.valueOf(maxAltitudeMeters).setScale(2, RoundingMode.HALF_UP);
    this.startBatteryPercent = startBatteryPercent;
    this.endBatteryPercent = endBatteryPercent;
    this.flightFinalState = finalState;
    this.flightReportNote = note != null && note.length() > 1000 ? note.substring(0, 1000) : note;
    if (flightTraceJson != null && !flightTraceJson.isBlank()) {
      this.flightTraceJson = flightTraceJson;
    }
    this.updatedAt = Instant.now();
  }

  public void recordVideoEvidenceId(UUID evidenceId) {
    this.flightVideoEvidenceId = evidenceId;
    this.updatedAt = Instant.now();
  }

  public boolean hasPersistedReport() {
    return telemetrySource != null && !"NONE".equals(telemetrySource);
  }

  private static String normalizeLabel(String raw, int number) {
    if (raw == null || raw.isBlank()) {
      return "Flight #" + number;
    }
    String trimmed = raw.trim();
    return trimmed.length() > 160 ? trimmed.substring(0, 160) : trimmed;
  }

  public UUID getId() {
    return id;
  }

  public UUID getFieldSessionId() {
    return fieldSessionId;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public int getFlightNumber() {
    return flightNumber;
  }

  public LocalDate getFlightDate() {
    return flightDate;
  }

  public String getFlightLabel() {
    return flightLabel;
  }

  public String getTelemetrySource() {
    return telemetrySource;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public Integer getFlightDurationSeconds() {
    return flightDurationSeconds;
  }

  public BigDecimal getDistanceFlownMeters() {
    return distanceFlownMeters;
  }

  public BigDecimal getMaxAltitudeMeters() {
    return maxAltitudeMeters;
  }

  public Integer getStartBatteryPercent() {
    return startBatteryPercent;
  }

  public Integer getEndBatteryPercent() {
    return endBatteryPercent;
  }

  public String getFlightFinalState() {
    return flightFinalState;
  }

  public String getFlightReportNote() {
    return flightReportNote;
  }

  public String getFlightTraceJson() {
    return flightTraceJson;
  }

  public UUID getFlightVideoEvidenceId() {
    return flightVideoEvidenceId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

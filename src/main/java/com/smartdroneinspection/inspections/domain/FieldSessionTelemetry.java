package com.smartdroneinspection.inspections.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/** SmartDroneHub flight telemetry summary, GPS trace, and recorded flight video link. */
@Entity
@Table(name = "field_session_telemetry")
public class FieldSessionTelemetry {

  @Id
  @Column(name = "field_session_id")
  private UUID fieldSessionId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "field_session_id")
  private FieldSession fieldSession;

  @Column(name = "telemetry_source", length = 64)
  private String telemetrySource;

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

  protected FieldSessionTelemetry() {}

  FieldSessionTelemetry(FieldSession fieldSession) {
    this.fieldSession = Objects.requireNonNull(fieldSession, "Field session is required");
  }

  void recordReport(
      String telemetrySource,
      Integer durationSeconds,
      Double distanceFlownMeters,
      Double maxAltitudeMeters,
      Integer startBatteryPercent,
      Integer endBatteryPercent,
      String finalState,
      String note,
      String flightTraceJson) {
    this.telemetrySource = telemetrySource;
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
  }

  void recordVideoEvidenceId(UUID evidenceId) {
    this.flightVideoEvidenceId = evidenceId;
  }

  boolean hasPersistedReport() {
    return telemetrySource != null && !"NONE".equals(telemetrySource);
  }

  public UUID getFieldSessionId() {
    return fieldSessionId;
  }

  public String getTelemetrySource() {
    return telemetrySource;
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
}

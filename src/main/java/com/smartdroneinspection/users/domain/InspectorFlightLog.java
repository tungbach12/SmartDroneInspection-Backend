package com.smartdroneinspection.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inspector_flight_logs")
public class InspectorFlightLog {

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspector_user_id", nullable = false)
  private UUID inspectorUserId;

  @Column(name = "flight_code", nullable = false, length = 64)
  private String flightCode;

  @Column(name = "flight_date", nullable = false)
  private LocalDate flightDate;

  @Column(name = "mission_title", nullable = false, length = 255)
  private String missionTitle;

  @Column(name = "drone_model", nullable = false, length = 255)
  private String droneModel;

  @Column(name = "drone_serial_number", length = 128)
  private String droneSerialNumber;

  @Column(name = "location", nullable = false, length = 500)
  private String location;

  @Column(name = "flight_duration_minutes", nullable = false)
  private int flightDurationMinutes;

  @Column(name = "inspection_type", nullable = false, length = 128)
  private String inspectionType;

  @Column(name = "safety_incident", nullable = false)
  private boolean safetyIncident = false;

  @Column(name = "notes", columnDefinition = "text")
  private String notes;

  @Column(name = "recorded_by_user_id")
  private UUID recordedByUserId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected InspectorFlightLog() {}

  public InspectorFlightLog(
      UUID inspectorUserId,
      String flightCode,
      LocalDate flightDate,
      String missionTitle,
      String droneModel,
      String droneSerialNumber,
      String location,
      int flightDurationMinutes,
      String inspectionType,
      boolean safetyIncident,
      String notes,
      UUID recordedByUserId) {
    this.inspectorUserId = inspectorUserId;
    this.flightCode = flightCode;
    this.flightDate = flightDate;
    this.missionTitle = missionTitle;
    this.droneModel = droneModel;
    this.droneSerialNumber = droneSerialNumber;
    this.location = location;
    this.flightDurationMinutes = flightDurationMinutes;
    this.inspectionType = inspectionType;
    this.safetyIncident = safetyIncident;
    this.notes = notes;
    this.recordedByUserId = recordedByUserId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectorUserId() {
    return inspectorUserId;
  }

  public String getFlightCode() {
    return flightCode;
  }

  public LocalDate getFlightDate() {
    return flightDate;
  }

  public String getMissionTitle() {
    return missionTitle;
  }

  public String getDroneModel() {
    return droneModel;
  }

  public String getDroneSerialNumber() {
    return droneSerialNumber;
  }

  public String getLocation() {
    return location;
  }

  public int getFlightDurationMinutes() {
    return flightDurationMinutes;
  }

  public String getInspectionType() {
    return inspectionType;
  }

  public boolean isSafetyIncident() {
    return safetyIncident;
  }

  public String getNotes() {
    return notes;
  }

  public UUID getRecordedByUserId() {
    return recordedByUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

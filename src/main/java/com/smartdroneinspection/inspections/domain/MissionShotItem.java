package com.smartdroneinspection.inspections.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ordered capture instruction for one inspection shot or waypoint (GAP-D-09, BR-07).
 *
 * <p>Waypoints are optional when manual flight is used. Target structure/component, camera heading,
 * gimbal pitch, and target GSD provide engineering guidance for flight execution.
 */
@Entity
@Table(name = "mission_shot_items")
public class MissionShotItem {

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "mission_plan_id", nullable = false)
  private DroneMissionPlan missionPlan;

  @Column(name = "sequence_number", nullable = false)
  private int sequenceNumber;

  @Column(name = "component_reference", nullable = false, length = 200)
  private String componentReference;

  @Column(name = "waypoint_latitude", precision = 10, scale = 7)
  private BigDecimal waypointLatitude;

  @Column(name = "waypoint_longitude", precision = 10, scale = 7)
  private BigDecimal waypointLongitude;

  @Column(name = "waypoint_altitude_m", precision = 10, scale = 3)
  private BigDecimal waypointAltitudeM;

  @Column(name = "camera_heading_degrees", precision = 7, scale = 3)
  private BigDecimal cameraHeadingDegrees;

  @Column(name = "gimbal_pitch_degrees", precision = 7, scale = 3)
  private BigDecimal gimbalPitchDegrees;

  @Column(name = "target_gsd_mm_per_pixel", precision = 12, scale = 6)
  private BigDecimal targetGsdMmPerPixel;

  @Column(name = "capture_instructions", length = 2000)
  private String captureInstructions;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MissionShotItem() {}

  public MissionShotItem(
      int sequenceNumber,
      String componentReference,
      BigDecimal waypointLatitude,
      BigDecimal waypointLongitude,
      BigDecimal waypointAltitudeM,
      BigDecimal cameraHeadingDegrees,
      BigDecimal gimbalPitchDegrees,
      BigDecimal targetGsdMmPerPixel,
      String captureInstructions) {
    if (sequenceNumber <= 0) {
      throw new IllegalArgumentException("Shot item sequence number must be positive");
    }
    if (componentReference == null || componentReference.isBlank()) {
      throw new IllegalArgumentException("Component reference must not be blank");
    }
    validateWaypoints(waypointLatitude, waypointLongitude);

    this.sequenceNumber = sequenceNumber;
    this.componentReference = componentReference.trim();
    this.waypointLatitude = waypointLatitude;
    this.waypointLongitude = waypointLongitude;
    this.waypointAltitudeM = waypointAltitudeM;
    this.cameraHeadingDegrees = cameraHeadingDegrees;
    this.gimbalPitchDegrees = gimbalPitchDegrees;
    this.targetGsdMmPerPixel = targetGsdMmPerPixel;
    this.captureInstructions = captureInstructions;
    this.createdAt = Instant.now();
  }

  private static void validateWaypoints(BigDecimal latitude, BigDecimal longitude) {
    if ((latitude == null && longitude != null) || (latitude != null && longitude == null)) {
      throw new IllegalArgumentException(
          "Waypoint latitude and longitude must both be provided or both be null");
    }
    if (latitude != null) {
      if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
          || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
        throw new IllegalArgumentException("Latitude must be between -90 and 90");
      }
      if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
          || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
        throw new IllegalArgumentException("Longitude must be between -180 and 180");
      }
    }
  }

  public UUID getId() {
    return id;
  }

  void assignTo(DroneMissionPlan plan) {
    this.missionPlan = plan;
  }

  public DroneMissionPlan getMissionPlan() {
    return missionPlan;
  }

  public int getSequenceNumber() {
    return sequenceNumber;
  }

  public String getComponentReference() {
    return componentReference;
  }

  public BigDecimal getWaypointLatitude() {
    return waypointLatitude;
  }

  public BigDecimal getWaypointLongitude() {
    return waypointLongitude;
  }

  public BigDecimal getWaypointAltitudeM() {
    return waypointAltitudeM;
  }

  public BigDecimal getCameraHeadingDegrees() {
    return cameraHeadingDegrees;
  }

  public BigDecimal getGimbalPitchDegrees() {
    return gimbalPitchDegrees;
  }

  public BigDecimal getTargetGsdMmPerPixel() {
    return targetGsdMmPerPixel;
  }

  public String getCaptureInstructions() {
    return captureInstructions;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

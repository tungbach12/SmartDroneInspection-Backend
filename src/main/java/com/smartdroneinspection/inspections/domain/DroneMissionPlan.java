package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.AirspaceCheckStatus;
import com.smartdroneinspection.inspections.domain.enums.MissionPlanStatus;
import com.smartdroneinspection.shared.exception.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Versioned operational flight and engineering mission plan (GAP-F-08, GAP-D-09, BR-07, BR-08,
 * BR-16).
 *
 * <p>Captures mission-specific SOW engineering targets (GSD, overlap, equipment, AGL altitude, shot
 * items). Approval is strictly blocked until SOW completeness is satisfied and airspace clearance /
 * flight permit is verified.
 */
@Entity
@Table(name = "drone_mission_plans")
public class DroneMissionPlan {

  @Id @GeneratedValue private UUID id;

  @Column(name = "service_order_id", nullable = false)
  private UUID serviceOrderId;

  @Column(name = "provider_id", nullable = false)
  private UUID providerId;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Column(name = "previous_version_id")
  private UUID previousVersionId;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Column(name = "drone_registration_id", length = 128)
  private String droneRegistrationId;

  @Column(name = "pilot_user_id")
  private UUID pilotUserId;

  @Column(name = "flight_permit_reference", length = 128)
  private String flightPermitReference;

  @Column(name = "camera_model", length = 200)
  private String cameraModel;

  @Column(name = "sensor_width_mm", precision = 10, scale = 4)
  private BigDecimal sensorWidthMm;

  @Column(name = "focal_length_mm", precision = 10, scale = 4)
  private BigDecimal focalLengthMm;

  @Column(name = "image_width_px")
  private Integer imageWidthPx;

  @Column(name = "target_gsd_mm_per_pixel", precision = 12, scale = 6)
  private BigDecimal targetGsdMmPerPixel;

  @Column(name = "planned_agl_m", precision = 10, scale = 3)
  private BigDecimal plannedAglM;

  @Column(name = "forward_overlap_percent", precision = 5, scale = 2)
  private BigDecimal forwardOverlapPercent;

  @Column(name = "side_overlap_percent", precision = 5, scale = 2)
  private BigDecimal sideOverlapPercent;

  @Enumerated(EnumType.STRING)
  @Column(name = "airspace_check_status", nullable = false, length = 32)
  private AirspaceCheckStatus airspaceCheckStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private MissionPlanStatus status;

  @Column(name = "approved_by_user_id")
  private UUID approvedByUserId;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  @OneToMany(
      mappedBy = "missionPlan",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("sequenceNumber ASC")
  private List<MissionShotItem> shotItems = new ArrayList<>();

  protected DroneMissionPlan() {}

  public DroneMissionPlan(
      UUID serviceOrderId,
      UUID providerId,
      int versionNumber,
      UUID previousVersionId,
      UUID createdByUserId) {
    this.serviceOrderId = Objects.requireNonNull(serviceOrderId, "Service order ID is required");
    this.providerId = Objects.requireNonNull(providerId, "Provider ID is required");
    if (versionNumber <= 0) {
      throw new IllegalArgumentException("Version number must be positive");
    }
    this.versionNumber = versionNumber;
    this.previousVersionId = previousVersionId;
    this.createdByUserId = Objects.requireNonNull(createdByUserId, "Creator user ID is required");
    this.airspaceCheckStatus = AirspaceCheckStatus.NOT_CHECKED;
    this.status = MissionPlanStatus.DRAFT;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void setEquipmentAndSow(
      String cameraModel,
      String droneRegistrationId,
      UUID pilotUserId,
      BigDecimal targetGsdMmPerPixel,
      BigDecimal plannedAglM,
      BigDecimal forwardOverlapPercent,
      BigDecimal sideOverlapPercent,
      BigDecimal sensorWidthMm,
      BigDecimal focalLengthMm,
      Integer imageWidthPx) {
    ensureEditable();
    this.cameraModel = cameraModel;
    this.droneRegistrationId = droneRegistrationId;
    this.pilotUserId = pilotUserId;
    this.targetGsdMmPerPixel = targetGsdMmPerPixel;
    this.plannedAglM = plannedAglM;
    this.forwardOverlapPercent = forwardOverlapPercent;
    this.sideOverlapPercent = sideOverlapPercent;
    this.sensorWidthMm = sensorWidthMm;
    this.focalLengthMm = focalLengthMm;
    this.imageWidthPx = imageWidthPx;
    this.updatedAt = Instant.now();
  }

  public void addShotItem(MissionShotItem item) {
    ensureEditable();
    MissionShotItem shotItem = Objects.requireNonNull(item, "Shot item is required");
    shotItem.assignTo(this);
    this.shotItems.add(shotItem);
    this.updatedAt = Instant.now();
  }

  public void clearShotItems() {
    ensureEditable();
    this.shotItems.clear();
    this.updatedAt = Instant.now();
  }

  public void recordAirspacePreCheck(AirspaceCheckStatus checkStatus) {
    ensureEditable();
    this.airspaceCheckStatus =
        Objects.requireNonNull(checkStatus, "Airspace check status is required");
    this.updatedAt = Instant.now();
  }

  public void verifyFlightPermit(String permitReference) {
    ensureEditable();
    if (permitReference == null || permitReference.isBlank()) {
      throw new IllegalArgumentException("Flight permit reference must not be blank");
    }
    this.flightPermitReference = permitReference.trim();
    this.airspaceCheckStatus = AirspaceCheckStatus.CLEARED;
    this.updatedAt = Instant.now();
  }

  public void submit() {
    ensureStatus(MissionPlanStatus.DRAFT, MissionPlanStatus.REVISION_REQUIRED);
    this.status = MissionPlanStatus.SUBMITTED;
    this.updatedAt = Instant.now();
  }

  public void approve(UUID providerManagerUserId) {
    ensureStatus(MissionPlanStatus.DRAFT, MissionPlanStatus.SUBMITTED);
    validateSowCompleteness();
    validateAirspaceClearance();
    validateDossier();

    this.approvedByUserId =
        Objects.requireNonNull(providerManagerUserId, "Approving Provider Manager is required");
    this.approvedAt = Instant.now();
    this.status = MissionPlanStatus.APPROVED;
    this.updatedAt = this.approvedAt;
  }

  private void validateSowCompleteness() {
    boolean missingSow =
        targetGsdMmPerPixel == null
            || targetGsdMmPerPixel.signum() <= 0
            || plannedAglM == null
            || plannedAglM.signum() <= 0
            || forwardOverlapPercent == null
            || forwardOverlapPercent.signum() <= 0
            || sideOverlapPercent == null
            || sideOverlapPercent.signum() <= 0
            || cameraModel == null
            || cameraModel.isBlank()
            || shotItems.isEmpty();

    if (missingSow) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "SOW_SPECIFICATION_INCOMPLETE",
          "Mission approval requires complete SOW engineering targets (GSD, overlap, equipment, AGL, and shot items).");
    }
  }

  private void validateAirspaceClearance() {
    if (airspaceCheckStatus == null || !airspaceCheckStatus.isFlightCleared()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "AIRSPACE_CLEARANCE_REQUIRED",
          "Mission approval is blocked until airspace clearance or flight permit is verified.");
    }
    if (airspaceCheckStatus.requiresPermit()
        && (flightPermitReference == null || flightPermitReference.isBlank())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "FLIGHT_PERMIT_REQUIRED",
          "Clearance-required airspace cannot be approved without an authority-issued flight permit reference.");
    }
  }

  private void validateDossier() {
    if (droneRegistrationId == null || droneRegistrationId.isBlank() || pilotUserId == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "MISSION_DOSSIER_INCOMPLETE",
          "Mission approval requires a registered airframe (droneRegistrationId) and pilotUserId.");
    }
  }

  private void ensureEditable() {
    if (this.status == MissionPlanStatus.APPROVED || this.status == MissionPlanStatus.SUPERSEDED) {
      throw new IllegalStateException("Approved or superseded mission plan cannot be edited");
    }
  }

  private void ensureStatus(MissionPlanStatus... allowed) {
    for (MissionPlanStatus s : allowed) {
      if (this.status == s) {
        return;
      }
    }
    throw new IllegalStateException("Invalid mission plan transition from " + this.status);
  }

  public UUID getId() {
    return id;
  }

  public UUID getServiceOrderId() {
    return serviceOrderId;
  }

  public UUID getProviderId() {
    return providerId;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public UUID getPreviousVersionId() {
    return previousVersionId;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public String getDroneRegistrationId() {
    return droneRegistrationId;
  }

  public UUID getPilotUserId() {
    return pilotUserId;
  }

  public String getFlightPermitReference() {
    return flightPermitReference;
  }

  public String getCameraModel() {
    return cameraModel;
  }

  public BigDecimal getSensorWidthMm() {
    return sensorWidthMm;
  }

  public BigDecimal getFocalLengthMm() {
    return focalLengthMm;
  }

  public Integer getImageWidthPx() {
    return imageWidthPx;
  }

  public BigDecimal getTargetGsdMmPerPixel() {
    return targetGsdMmPerPixel;
  }

  public BigDecimal getPlannedAglM() {
    return plannedAglM;
  }

  public BigDecimal getForwardOverlapPercent() {
    return forwardOverlapPercent;
  }

  public BigDecimal getSideOverlapPercent() {
    return sideOverlapPercent;
  }

  public AirspaceCheckStatus getAirspaceCheckStatus() {
    return airspaceCheckStatus;
  }

  public MissionPlanStatus getStatus() {
    return status;
  }

  public UUID getApprovedByUserId() {
    return approvedByUserId;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public List<MissionShotItem> getShotItems() {
    return Collections.unmodifiableList(shotItems);
  }
}

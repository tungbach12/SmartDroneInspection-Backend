package com.smartdroneinspection.contracts.domain;

import com.smartdroneinspection.shared.api.PlatformOperationsPolicyPort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Platform-wide operational policies configured by Admin (e.g., Step 4 timeout and lead days). */
@Entity
@Table(name = "platform_operations_settings")
public class PlatformOperationsSettings {

  public static final int MIN_TIMEOUT_HOURS = 1;
  public static final int MAX_TIMEOUT_HOURS = 720;
  public static final int MIN_SERVICE_REQUEST_LEAD_DAYS = 0;
  public static final int MAX_SERVICE_REQUEST_LEAD_DAYS = 365;

  @Id @GeneratedValue private UUID id;

  @Column(name = "assignment_acceptance_timeout_hours", nullable = false)
  private int assignmentAcceptanceTimeoutHours =
      PlatformOperationsPolicyPort.DEFAULT_ASSIGNMENT_ACCEPTANCE_TIMEOUT_HOURS;

  @Column(name = "service_request_lead_days", nullable = false)
  private int serviceRequestLeadDays =
      PlatformOperationsPolicyPort.DEFAULT_SERVICE_REQUEST_LEAD_DAYS;

  @Column(name = "updated_by_user_id")
  private UUID updatedByUserId;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected PlatformOperationsSettings() {}

  public PlatformOperationsSettings(int assignmentAcceptanceTimeoutHours, UUID updatedByUserId) {
    this(
        assignmentAcceptanceTimeoutHours,
        PlatformOperationsPolicyPort.DEFAULT_SERVICE_REQUEST_LEAD_DAYS,
        updatedByUserId);
  }

  public PlatformOperationsSettings(
      int assignmentAcceptanceTimeoutHours, int serviceRequestLeadDays, UUID updatedByUserId) {
    updateSettings(assignmentAcceptanceTimeoutHours, serviceRequestLeadDays, updatedByUserId);
  }

  public void updateTimeoutHours(int assignmentAcceptanceTimeoutHours, UUID updatedByUserId) {
    updateSettings(assignmentAcceptanceTimeoutHours, this.serviceRequestLeadDays, updatedByUserId);
  }

  public void updateSettings(
      int assignmentAcceptanceTimeoutHours, int serviceRequestLeadDays, UUID updatedByUserId) {
    if (assignmentAcceptanceTimeoutHours < MIN_TIMEOUT_HOURS
        || assignmentAcceptanceTimeoutHours > MAX_TIMEOUT_HOURS) {
      throw new IllegalArgumentException(
          "Assignment acceptance timeout must be between "
              + MIN_TIMEOUT_HOURS
              + " and "
              + MAX_TIMEOUT_HOURS
              + " hours");
    }
    if (serviceRequestLeadDays < MIN_SERVICE_REQUEST_LEAD_DAYS
        || serviceRequestLeadDays > MAX_SERVICE_REQUEST_LEAD_DAYS) {
      throw new IllegalArgumentException(
          "Service request minimum lead days must be between "
              + MIN_SERVICE_REQUEST_LEAD_DAYS
              + " and "
              + MAX_SERVICE_REQUEST_LEAD_DAYS
              + " days");
    }
    this.assignmentAcceptanceTimeoutHours = assignmentAcceptanceTimeoutHours;
    this.serviceRequestLeadDays = serviceRequestLeadDays;
    this.updatedByUserId = updatedByUserId;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public int getAssignmentAcceptanceTimeoutHours() {
    return assignmentAcceptanceTimeoutHours;
  }

  public int getServiceRequestLeadDays() {
    return serviceRequestLeadDays;
  }

  public UUID getUpdatedByUserId() {
    return updatedByUserId;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

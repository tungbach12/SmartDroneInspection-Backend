package com.smartdroneinspection.contracts.domain;

import com.smartdroneinspection.shared.api.PlatformOperationsPolicyPort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Platform-wide operational dispatch policies configured by Admin (e.g., Step 4 timeout). */
@Entity
@Table(name = "platform_operations_settings")
public class PlatformOperationsSettings {

  public static final int MIN_TIMEOUT_HOURS = 1;
  public static final int MAX_TIMEOUT_HOURS = 720;

  @Id @GeneratedValue private UUID id;

  @Column(name = "assignment_acceptance_timeout_hours", nullable = false)
  private int assignmentAcceptanceTimeoutHours =
      PlatformOperationsPolicyPort.DEFAULT_ASSIGNMENT_ACCEPTANCE_TIMEOUT_HOURS;

  @Column(name = "updated_by_user_id")
  private UUID updatedByUserId;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected PlatformOperationsSettings() {}

  public PlatformOperationsSettings(int assignmentAcceptanceTimeoutHours, UUID updatedByUserId) {
    updateTimeoutHours(assignmentAcceptanceTimeoutHours, updatedByUserId);
  }

  public void updateTimeoutHours(int assignmentAcceptanceTimeoutHours, UUID updatedByUserId) {
    if (assignmentAcceptanceTimeoutHours < MIN_TIMEOUT_HOURS
        || assignmentAcceptanceTimeoutHours > MAX_TIMEOUT_HOURS) {
      throw new IllegalArgumentException(
          "Assignment acceptance timeout must be between "
              + MIN_TIMEOUT_HOURS
              + " and "
              + MAX_TIMEOUT_HOURS
              + " hours");
    }
    this.assignmentAcceptanceTimeoutHours = assignmentAcceptanceTimeoutHours;
    this.updatedByUserId = updatedByUserId;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public int getAssignmentAcceptanceTimeoutHours() {
    return assignmentAcceptanceTimeoutHours;
  }

  public UUID getUpdatedByUserId() {
    return updatedByUserId;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

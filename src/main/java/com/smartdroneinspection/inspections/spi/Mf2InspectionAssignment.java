package com.smartdroneinspection.inspections.spi;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Assignment command accepted from MF2 after its commercial and partner checks pass. */
public interface Mf2InspectionAssignment {

  boolean hasPlatformScheduleConflict(
      UUID inspectorId, UUID droneId, Instant plannedStartAt, Instant plannedEndAt);

  default boolean hasPlatformScheduleConflictExcluding(
      UUID inspectorId,
      UUID droneId,
      Instant plannedStartAt,
      Instant plannedEndAt,
      UUID excludeInspectionId) {
    return hasPlatformScheduleConflict(inspectorId, droneId, plannedStartAt, plannedEndAt);
  }

  default void validateReinspectionSource(
      UUID ownerCustomerId, UUID assetId, UUID sourceInspectionId) {}

  /**
   * The Inspector's answer to a dispatch assignment. MF2 shows this in step 4 so the Manager knows
   * whether the flight is confirmed or must be reassigned.
   */
  record AssignmentAcceptance(
      String assignmentStatus,
      boolean reassignmentRequired,
      String declineReason,
      Instant assignmentRequestedAt) {

    public AssignmentAcceptance(
        String assignmentStatus, boolean reassignmentRequired, String declineReason) {
      this(assignmentStatus, reassignmentRequired, declineReason, null);
    }
  }

  /** Empty until dispatch has created the platform inspection for this occurrence. */
  default Optional<AssignmentAcceptance> findAssignmentState(UUID inspectionId) {
    return Optional.empty();
  }

  UUID assign(
      UUID ownerCustomerId,
      UUID assetId,
      UUID inspectorId,
      UUID droneId,
      Instant plannedStartAt,
      Instant plannedEndAt,
      UUID assignedByUserId);

  default UUID assign(
      UUID ownerCustomerId,
      UUID assetId,
      UUID inspectorId,
      UUID droneId,
      Instant plannedStartAt,
      Instant plannedEndAt,
      UUID assignedByUserId,
      UUID sourceInspectionId) {
    return assign(
        ownerCustomerId,
        assetId,
        inspectorId,
        droneId,
        plannedStartAt,
        plannedEndAt,
        assignedByUserId);
  }
}

package com.smartdroneinspection.shared.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared boundary port allowing {@code inspections} dispatch recovery operations (reschedule,
 * reassign, cancel, acceptance timeout) to stay synchronized with linked MF2 contract occurrences
 * without creating a module dependency cycle.
 */
public interface InspectionContractSyncPort {

  record TimeoutRevertResult(UUID requestId, UUID assignedManagerId, int occurrenceIndex) {}

  boolean hasScheduleConflict(
      UUID inspectorId, UUID droneId, Instant start, Instant end, UUID excludeInspectionId);

  void syncRescheduleFromInspection(
      UUID inspectionId, Instant plannedStartAt, Instant plannedEndAt);

  void syncReassignFromInspection(UUID inspectionId, UUID newInspectorId, UUID newDroneId);

  void syncCancelFromInspection(UUID inspectionId, String reason);

  default Optional<TimeoutRevertResult> syncTimeoutRevertFromInspection(
      UUID inspectionId, UUID timedOutInspectorId, Instant timedOutAt) {
    return Optional.empty();
  }

  boolean isLinkedOccurrencePaidOrAccepted(UUID inspectionId);

  boolean hasActiveReinspectionRequest(UUID ownerCustomerId, UUID assetId, UUID sourceInspectionId);
}

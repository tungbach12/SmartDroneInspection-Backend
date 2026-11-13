package com.smartdroneinspection.inspections.spi;

import java.time.Instant;
import java.util.UUID;

/** Assignment command accepted from MF2 after its commercial and partner checks pass. */
public interface Mf2InspectionAssignment {

  boolean hasPlatformScheduleConflict(
      UUID inspectorId, UUID droneId, Instant plannedStartAt, Instant plannedEndAt);

  UUID assign(
      UUID ownerCustomerId,
      UUID assetId,
      UUID inspectorId,
      UUID droneId,
      Instant plannedStartAt,
      Instant plannedEndAt,
      UUID assignedByUserId);
}

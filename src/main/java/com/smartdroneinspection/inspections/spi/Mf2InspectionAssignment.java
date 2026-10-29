package com.smartdroneinspection.inspections.spi;

import java.time.Instant;
import java.util.UUID;

/** Assignment command accepted from MF2 after its commercial and partner checks pass. */
public interface Mf2InspectionAssignment {

  UUID assign(
      UUID customerOrganizationId,
      UUID assetId,
      UUID inspectorId,
      UUID droneId,
      Instant plannedStartAt,
      Instant plannedEndAt,
      UUID assignedByUserId);
}

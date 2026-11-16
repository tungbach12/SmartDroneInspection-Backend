package com.smartdroneinspection.inspections.api.dto.request;

import java.time.Instant;
import java.util.UUID;

/** Optional scheduling and partner override parameters when creating a quick re-inspection. */
public record CreateReinspectionRequest(
    UUID inspectorId, UUID droneId, Instant plannedStartAt, Instant plannedEndAt) {

  public static CreateReinspectionRequest empty() {
    return new CreateReinspectionRequest(null, null, null, null);
  }
}

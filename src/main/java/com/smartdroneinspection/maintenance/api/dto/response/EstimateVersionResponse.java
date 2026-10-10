package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.MaintenanceEstimateVersion;
import com.smartdroneinspection.maintenance.domain.enums.EstimateStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** MF4-06/07: an estimate version with the total computed by the SYSTEM from its priced lines. */
public record EstimateVersionResponse(
    UUID id,
    int versionNo,
    EstimateStatus status,
    UUID preparedByUserId,
    UUID approvedByUserId,
    Instant approvedAt,
    String currency,
    BigDecimal baselineTotal,
    String assumptions,
    String snapshot,
    Instant createdAt) {

  public static EstimateVersionResponse from(MaintenanceEstimateVersion version) {
    return new EstimateVersionResponse(
        version.getId(),
        version.getVersionNo(),
        version.getStatus(),
        version.getPreparedByUserId(),
        version.getApprovedByUserId(),
        version.getApprovedAt(),
        version.getCurrency(),
        version.getBaselineTotal(),
        version.getAssumptions(),
        version.getSnapshot(),
        version.getCreatedAt());
  }
}

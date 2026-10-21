package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.AssetPairAssignment;
import com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus;
import com.smartdroneinspection.assets.domain.enums.AssignmentResponse;
import java.time.Instant;
import java.util.UUID;

public record AssetPairAdminResponse(
    UUID id,
    UUID assetId,
    UUID inspectorUserId,
    UUID droneId,
    Instant validFrom,
    Instant validUntil,
    AssetPairAssignmentStatus status,
    String reason,
    UUID assignedByUserId,
    Instant assignedAt,
    AssignmentResponse assignmentResponse,
    Instant respondedAt) {

  public static AssetPairAdminResponse from(AssetPairAssignment assignment) {
    return new AssetPairAdminResponse(
        assignment.getId(),
        assignment.getAssetId(),
        assignment.getInspectorUserId(),
        assignment.getDroneId(),
        assignment.getValidFrom(),
        assignment.getValidUntil(),
        assignment.getStatus(),
        assignment.getReason(),
        assignment.getAssignedByUserId(),
        assignment.getAssignedAt(),
        assignment.getAssignmentResponse(),
        assignment.getRespondedAt());
  }
}

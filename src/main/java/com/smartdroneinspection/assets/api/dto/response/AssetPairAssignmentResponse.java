package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus;
import com.smartdroneinspection.assets.domain.enums.AssignmentResponse;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import java.time.Instant;
import java.util.UUID;

/**
 * What MF2-01 shows an inspector when they open a pairing: the asset, the assigned drone and the
 * scope they are being asked to accept.
 */
public record AssetPairAssignmentResponse(
    UUID id,
    UUID organizationId,
    UUID assetId,
    String assetName,
    UUID inspectorUserId,
    UUID droneId,
    String droneSerialNumber,
    DroneServiceability droneServiceability,
    Instant validFrom,
    Instant validUntil,
    AssetPairAssignmentStatus status,
    String reason,
    AssignmentResponse assignmentResponse,
    Instant respondedAt,
    Instant assignedAt) {}

package com.smartdroneinspection.assets.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AssetDefaultPairResponse(
    UUID pairAssignmentId,
    UUID inspectorUserId,
    String inspectorName,
    UUID droneId,
    String droneSerialNumber,
    String droneModel,
    String status,
    Instant validFrom,
    Instant validUntil) {}

package com.smartdroneinspection.users.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record InspectorApplicationResponse(
    UUID id,
    String email,
    String fullName,
    String phoneNumber,
    String citizenId,
    String address,
    String pilotLicenseNumber,
    String pilotLicenseClass,
    Instant pilotLicenseExpiresAt,
    boolean hasLicenseScan,
    boolean hasSignedContract,
    String signedContractSha256,
    boolean hasCleanupFailure,
    String status,
    UUID approvedUserId,
    UUID reviewedByUserId,
    Instant reviewedAt,
    String rejectionReason,
    Instant createdAt) {}

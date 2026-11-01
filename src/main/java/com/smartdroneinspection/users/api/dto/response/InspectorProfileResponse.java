package com.smartdroneinspection.users.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InspectorProfileResponse(
    UUID id,
    String email,
    String fullName,
    String phoneNumber,
    String avatarUrl,
    String status,
    String actorZone,
    String citizenId,
    LocalDate dateOfBirth,
    String address,
    String notes,
    // Pilot License
    String pilotLicenseNumber,
    String pilotLicenseClass,
    LocalDate pilotLicenseIssuedDate,
    Instant pilotLicenseExpiresAt,
    String pilotLicenseAuthority,
    String pilotLicenseFileKey,
    boolean hasLicenseScan,
    String licenseExpirationStatus,
    Long licenseRemainingDays,
    // Flight Experience
    BigDecimal priorFlightHours,
    BigDecimal companyFlightHours,
    BigDecimal totalFlightHours,
    int companyFlightsCount,
    String certifiedDroneModels,
    String specializations,
    String experienceSummary,
    Instant createdAt,
    Instant updatedAt) {}

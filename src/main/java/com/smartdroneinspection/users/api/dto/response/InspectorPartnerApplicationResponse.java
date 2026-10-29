package com.smartdroneinspection.users.api.dto.response;

import com.smartdroneinspection.users.domain.InspectorPartnerApplication;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InspectorPartnerApplicationResponse(
    UUID id,
    String email,
    String fullName,
    String phone,
    String pilotLicenseNumber,
    String pilotLicenseClass,
    LocalDate pilotLicenseExpiresAt,
    String pilotLicenseFileKey,
    String partnerContractFileKey,
    String status,
    String reviewNote,
    UUID reviewedByUserId,
    Instant reviewedAt,
    Instant createdAt) {
  public static InspectorPartnerApplicationResponse from(InspectorPartnerApplication application) {
    return new InspectorPartnerApplicationResponse(
        application.getId(),
        application.getEmail(),
        application.getFullName(),
        application.getPhone(),
        application.getPilotLicenseNumber(),
        application.getPilotLicenseClass(),
        application.getPilotLicenseExpiresAt(),
        application.getPilotLicenseFileKey(),
        application.getPartnerContractFileKey(),
        application.getStatus(),
        application.getReviewNote(),
        application.getReviewedByUserId(),
        application.getReviewedAt(),
        application.getCreatedAt());
  }
}

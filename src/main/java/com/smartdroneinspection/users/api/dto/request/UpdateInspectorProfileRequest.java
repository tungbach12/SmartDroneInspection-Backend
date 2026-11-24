package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record UpdateInspectorProfileRequest(
    @NotBlank @Size(max = 200) String fullName,
    @Size(max = 32) String phoneNumber,
    @Size(max = 32) String citizenId,
    LocalDate dateOfBirth,
    @Size(max = 500) String address,
    @Size(max = 2000) String notes,
    // Pilot License
    @Size(max = 128) String pilotLicenseNumber,
    @Size(max = 64) String pilotLicenseClass,
    LocalDate pilotLicenseIssuedDate,
    Instant pilotLicenseExpiresAt,
    @Size(max = 200) String pilotLicenseAuthority,
    // Flight Experience
    BigDecimal priorFlightHours,
    @Size(max = 500) String certifiedDroneModels,
    @Size(max = 500) String specializations,
    String experienceSummary,
    // Payout Bank Details
    @Size(max = 120) String payoutBankName,
    @Size(max = 64) String payoutBankAccountNumber,
    @Size(max = 200) String payoutBankAccountHolder,
    @Size(max = 200) String payoutBankBranch) {

  public UpdateInspectorProfileRequest(
      String fullName,
      String phoneNumber,
      String citizenId,
      LocalDate dateOfBirth,
      String address,
      String notes,
      String pilotLicenseNumber,
      String pilotLicenseClass,
      LocalDate pilotLicenseIssuedDate,
      Instant pilotLicenseExpiresAt,
      String pilotLicenseAuthority,
      BigDecimal priorFlightHours,
      String certifiedDroneModels,
      String specializations,
      String experienceSummary) {
    this(
        fullName,
        phoneNumber,
        citizenId,
        dateOfBirth,
        address,
        notes,
        pilotLicenseNumber,
        pilotLicenseClass,
        pilotLicenseIssuedDate,
        pilotLicenseExpiresAt,
        pilotLicenseAuthority,
        priorFlightHours,
        certifiedDroneModels,
        specializations,
        experienceSummary,
        null,
        null,
        null,
        null);
  }
}

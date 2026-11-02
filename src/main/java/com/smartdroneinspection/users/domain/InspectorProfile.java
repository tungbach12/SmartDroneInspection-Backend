package com.smartdroneinspection.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inspector_profiles")
public class InspectorProfile {

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "pilot_license_number", length = 128)
  private String pilotLicenseNumber;

  @Column(name = "pilot_license_class", length = 64)
  private String pilotLicenseClass;

  @Column(name = "pilot_license_expires_at")
  private Instant pilotLicenseExpiresAt;

  @Column(name = "pilot_license_file_key", length = 1000)
  private String pilotLicenseFileKey;

  @Column(name = "pilot_license_issued_date")
  private LocalDate pilotLicenseIssuedDate;

  @Column(name = "pilot_license_authority", length = 200)
  private String pilotLicenseAuthority;

  @Column(name = "citizen_id", length = 32)
  private String citizenId;

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Column(name = "address", length = 500)
  private String address;

  @Column(name = "prior_flight_hours", precision = 8, scale = 2, nullable = false)
  private BigDecimal priorFlightHours = BigDecimal.ZERO;

  @Column(name = "certified_drone_models", length = 500)
  private String certifiedDroneModels;

  @Column(name = "specializations", length = 500)
  private String specializations;

  @Column(name = "experience_summary", columnDefinition = "text")
  private String experienceSummary;

  @Column(name = "notes", columnDefinition = "text")
  private String notes;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected InspectorProfile() {}

  public InspectorProfile(UUID userId) {
    this.userId = userId;
    this.updatedAt = Instant.now();
  }

  public UUID getUserId() {
    return userId;
  }

  public String getPilotLicenseNumber() {
    return pilotLicenseNumber;
  }

  public String getPilotLicenseClass() {
    return pilotLicenseClass;
  }

  public Instant getPilotLicenseExpiresAt() {
    return pilotLicenseExpiresAt;
  }

  public String getPilotLicenseFileKey() {
    return pilotLicenseFileKey;
  }

  public LocalDate getPilotLicenseIssuedDate() {
    return pilotLicenseIssuedDate;
  }

  public String getPilotLicenseAuthority() {
    return pilotLicenseAuthority;
  }

  public String getCitizenId() {
    return citizenId;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public String getAddress() {
    return address;
  }

  public BigDecimal getPriorFlightHours() {
    return priorFlightHours != null ? priorFlightHours : BigDecimal.ZERO;
  }

  public String getCertifiedDroneModels() {
    return certifiedDroneModels;
  }

  public String getSpecializations() {
    return specializations;
  }

  public String getExperienceSummary() {
    return experienceSummary;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void updatePersonalDetails(
      String citizenId, LocalDate dateOfBirth, String address, String notes) {
    this.citizenId = citizenId;
    this.dateOfBirth = dateOfBirth;
    this.address = address;
    this.notes = notes;
    touch();
  }

  public void updatePilotLicense(
      String licenseNumber,
      String licenseClass,
      LocalDate issuedDate,
      Instant expiresAt,
      String authority,
      String fileKey) {
    this.pilotLicenseNumber = licenseNumber;
    this.pilotLicenseClass = licenseClass;
    this.pilotLicenseIssuedDate = issuedDate;
    this.pilotLicenseExpiresAt = expiresAt;
    this.pilotLicenseAuthority = authority;
    if (fileKey != null && !fileKey.isBlank()) {
      this.pilotLicenseFileKey = fileKey;
    }
    touch();
  }

  public void updatePilotLicense(
      String licenseNumber, String licenseClass, Instant expiresAt, String fileKey) {
    updatePilotLicense(
        licenseNumber,
        licenseClass,
        pilotLicenseIssuedDate,
        expiresAt,
        pilotLicenseAuthority,
        fileKey);
  }

  public void setPilotLicenseFileKey(String pilotLicenseFileKey) {
    this.pilotLicenseFileKey = pilotLicenseFileKey;
    touch();
  }

  public void updateFlightExperience(
      BigDecimal priorFlightHours,
      String certifiedDroneModels,
      String specializations,
      String experienceSummary) {
    if (priorFlightHours != null) {
      this.priorFlightHours = priorFlightHours;
    }
    this.certifiedDroneModels = certifiedDroneModels;
    this.specializations = specializations;
    this.experienceSummary = experienceSummary;
    touch();
  }

  public void addDroneModel(String droneModel) {
    certifiedDroneModels = addDistinctValue(certifiedDroneModels, droneModel);
    touch();
  }

  public void addSpecialization(String specialization) {
    specializations = addDistinctValue(specializations, specialization);
    touch();
  }

  private static String addDistinctValue(String current, String addition) {
    if (addition == null || addition.isBlank()) {
      return current;
    }
    String value = addition.trim();
    if (current == null || current.isBlank()) {
      return value;
    }
    boolean alreadyPresent =
        java.util.Arrays.stream(current.split(","))
            .map(String::trim)
            .anyMatch(existing -> existing.equalsIgnoreCase(value));
    return alreadyPresent ? current : current.trim() + ", " + value;
  }

  private void touch() {
    updatedAt = Instant.now();
  }
}

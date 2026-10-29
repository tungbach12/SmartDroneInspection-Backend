package com.smartdroneinspection.users.domain;

import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
public class User {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false, length = 320)
  private String email;

  @Column(name = "normalized_email", nullable = false, unique = true, length = 320)
  private String normalizedEmail;

  @Column(name = "full_name", nullable = false, length = 200)
  private String fullName;

  @Column(name = "phone_number", length = 32)
  private String phoneNumber;

  @Column(name = "avatar_url", length = 500)
  private String avatarUrl;

  @Column(name = "password_hash", length = 1024)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private UserStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_zone", nullable = false, length = 32)
  private ActorZone actorZone;

  @Column(name = "organization_id")
  private UUID organizationId;

  @Column(name = "auth_version", nullable = false)
  private int authVersion;

  @Column(name = "failed_login_count", nullable = false)
  private int failedLoginCount;

  @Column(name = "lockout_until")
  private Instant lockoutUntil;

  @Column(name = "must_change_password", nullable = false)
  private boolean mustChangePassword;

  @Column(name = "vision_fallback_enabled", nullable = false)
  private boolean visionFallbackEnabled = true;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "last_login_ip", length = 45)
  private String lastLoginIp;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "roles", nullable = false, length = 255)
  private String roles = "";

  @Column(name = "pilot_license_number", length = 100)
  private String pilotLicenseNumber;

  @Column(name = "pilot_license_class", length = 16)
  private String pilotLicenseClass;

  @Column(name = "pilot_license_expires_at")
  private Instant pilotLicenseExpiresAt;

  @Column(name = "pilot_license_file_key", length = 1000)
  private String pilotLicenseFileKey;

  protected User() {}

  public User(
      String email,
      String fullName,
      String passwordHash,
      UserStatus status,
      ActorZone actorZone,
      UUID organizationId) {
    this(email, fullName, null, null, passwordHash, status, actorZone, organizationId);
  }

  public User(
      String email,
      String fullName,
      String phoneNumber,
      String avatarUrl,
      String passwordHash,
      UserStatus status,
      ActorZone actorZone,
      UUID organizationId) {
    this.email = email;
    this.normalizedEmail = normalizeEmail(email);
    this.fullName = fullName;
    this.phoneNumber = phoneNumber;
    this.avatarUrl = avatarUrl;
    this.passwordHash = passwordHash;
    this.status = status;
    this.actorZone = actorZone;
    this.organizationId = organizationId;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public static String normalizeEmail(String value) {
    return value.trim().toLowerCase(java.util.Locale.ROOT);
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getFullName() {
    return fullName;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
    this.updatedAt = Instant.now();
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
    this.updatedAt = Instant.now();
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public UserStatus getStatus() {
    return status;
  }

  public ActorZone getActorZone() {
    return actorZone;
  }

  public UUID getOrganizationId() {
    return organizationId;
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

  public void updatePilotLicense(
      String licenseNumber, String licenseClass, Instant expiresAt, String fileKey) {
    this.pilotLicenseNumber = licenseNumber;
    this.pilotLicenseClass = licenseClass;
    this.pilotLicenseExpiresAt = expiresAt;
    this.pilotLicenseFileKey = fileKey;
    this.updatedAt = Instant.now();
  }

  public void setPilotLicense(
      String number, String licenseClass, java.time.LocalDate expiresAt, String fileKey) {
    Instant instantExpiresAt =
        expiresAt == null ? null : expiresAt.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    updatePilotLicense(number, licenseClass, instantExpiresAt, fileKey);
  }

  public void approveAsCustomer(UUID organizationId) {
    updateAccessProfile(
        ActorZone.CUSTOMER_ORGANIZATION, organizationId, List.of(UserRole.CUSTOMER));
    this.status = UserStatus.ACTIVE;
    this.updatedAt = Instant.now();
  }

  public boolean hasValidPilotLicense(Instant at) {
    Instant checkTime = at != null ? at : Instant.now();
    return pilotLicenseNumber != null
        && !pilotLicenseNumber.isBlank()
        && (pilotLicenseExpiresAt == null || pilotLicenseExpiresAt.isAfter(checkTime));
  }

  public int getAuthVersion() {
    return authVersion;
  }

  public boolean isMustChangePassword() {
    return mustChangePassword;
  }

  public boolean isVisionFallbackEnabled() {
    return visionFallbackEnabled;
  }

  public void setVisionFallbackEnabled(boolean visionFallbackEnabled) {
    this.visionFallbackEnabled = visionFallbackEnabled;
    this.updatedAt = Instant.now();
  }

  public void addRole(UserRole role) {
    if (role == null) {
      return;
    }
    List<UserRole> next = new ArrayList<>(parsedRoles());
    if (!next.contains(role)) {
      next.add(role);
    }
    this.roles = serializeRoles(next);
  }

  public void replaceRoles(List<UserRole> nextRoles) {
    this.roles =
        serializeRoles(nextRoles == null ? List.of() : nextRoles.stream().distinct().toList());
  }

  public List<String> roleValues() {
    return parsedRoles().stream().map(UserRole::value).distinct().toList();
  }

  private List<UserRole> parsedRoles() {
    if (roles == null || roles.isBlank()) {
      return List.of();
    }
    return Arrays.stream(roles.split(","))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .map(UserRole::fromName)
        .distinct()
        .toList();
  }

  private static String serializeRoles(List<UserRole> values) {
    return values.stream().map(UserRole::name).collect(Collectors.joining(","));
  }

  public void recordSuccessfulLogin(String ip) {
    failedLoginCount = 0;
    lockoutUntil = null;
    lastLoginAt = Instant.now();
    lastLoginIp = ip;
    updatedAt = Instant.now();
  }

  public void recordFailedLogin() {
    failedLoginCount++;
    if (failedLoginCount >= 5) {
      lockoutUntil = Instant.now().plusSeconds(15 * 60L);
      failedLoginCount = 0;
    }
    updatedAt = Instant.now();
  }

  public boolean active() {
    return status == UserStatus.ACTIVE
        && (lockoutUntil == null || lockoutUntil.isBefore(Instant.now()));
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
    this.mustChangePassword = false;
    this.updatedAt = Instant.now();
  }

  public void requirePasswordChange() {
    this.mustChangePassword = true;
  }

  public void activate() {
    this.status = UserStatus.ACTIVE;
    this.updatedAt = Instant.now();
  }

  public void disable(UserStatus nextStatus) {
    this.status = nextStatus;
    this.authVersion++;
    this.updatedAt = Instant.now();
  }

  public void updateAccessProfile(ActorZone actorZone, UUID organizationId, List<UserRole> roles) {
    this.actorZone = actorZone;
    this.organizationId = organizationId;
    replaceRoles(roles);
    incrementAuthVersion();
  }

  public void incrementAuthVersion() {
    this.authVersion++;
    this.updatedAt = Instant.now();
  }
}

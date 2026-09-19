package com.smartdroneinspection.users.domain;

import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "last_login_ip", length = 45)
  private String lastLoginIp;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(
      mappedBy = "user",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<UserRoleAssignment> roleAssignments = new ArrayList<>();

  protected User() {}

  public User(
      String email,
      String fullName,
      String passwordHash,
      UserStatus status,
      ActorZone actorZone,
      UUID organizationId) {
    this.email = email;
    this.normalizedEmail = normalizeEmail(email);
    this.fullName = fullName;
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

  public int getAuthVersion() {
    return authVersion;
  }

  public boolean isMustChangePassword() {
    return mustChangePassword;
  }

  public List<UserRoleAssignment> getRoleAssignments() {
    return roleAssignments;
  }

  public void addRole(UserRole role) {
    roleAssignments.add(new UserRoleAssignment(this, role));
  }

  public void replaceRoles(List<UserRole> roles) {
    roleAssignments.clear();
    roles.forEach(this::addRole);
  }

  public List<String> roleValues() {
    return roleAssignments.stream().map(a -> a.getRole().value()).distinct().toList();
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

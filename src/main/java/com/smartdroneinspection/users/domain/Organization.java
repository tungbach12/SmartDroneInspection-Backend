package com.smartdroneinspection.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization {

  @Id @GeneratedValue private UUID id;

  @Column(name = "legal_name", nullable = false, length = 250)
  private String legalName;

  @Column(name = "display_name", nullable = false, length = 200)
  private String displayName;

  @Column(name = "registration_code", nullable = false, unique = true, length = 64)
  private String registrationCode;

  @Column(nullable = false, length = 64)
  private String timezone;

  @Column(nullable = false, length = 24)
  private String status;

  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Organization() {}

  public Organization(String name, String code, String description) {
    this.legalName = name;
    this.displayName = name;
    this.registrationCode = normalizeCode(code);
    this.timezone = "Asia/Ho_Chi_Minh";
    this.status = "ACTIVE";
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return displayName;
  }

  public String getCode() {
    return registrationCode;
  }

  public String getStatus() {
    return status;
  }

  public void deactivate() {
    status = "SUSPENDED";
    updatedAt = Instant.now();
  }

  public void activate() {
    status = "ACTIVE";
    updatedAt = Instant.now();
  }

  public static String normalizeCode(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }
}

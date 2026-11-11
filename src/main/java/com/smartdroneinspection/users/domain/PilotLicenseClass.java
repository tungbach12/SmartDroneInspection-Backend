package com.smartdroneinspection.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "pilot_license_classes")
public class PilotLicenseClass {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false, length = 64)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(nullable = false)
  private boolean active;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PilotLicenseClass() {}

  public PilotLicenseClass(String name, String description) {
    this.name = name.trim();
    this.description = description;
    this.active = true;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void update(String name, String description) {
    this.name = name.trim();
    this.description = description;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}

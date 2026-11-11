package com.smartdroneinspection.assets.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "drone_feature_configs")
public class DroneFeatureConfig {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false, unique = true, length = 64)
  private String code;

  @Column(name = "name_vi", nullable = false, length = 255)
  private String nameVi;

  @Column(name = "name_en", nullable = false, length = 255)
  private String nameEn;

  @Column(name = "description_vi", length = 500)
  private String descriptionVi;

  @Column(name = "description_en", length = 500)
  private String descriptionEn;

  @Column(nullable = false, length = 32)
  private String category;

  @Column(name = "is_active", nullable = false)
  private boolean isActive;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected DroneFeatureConfig() {}

  public DroneFeatureConfig(
      String code,
      String nameVi,
      String nameEn,
      String descriptionVi,
      String descriptionEn,
      String category,
      boolean isActive,
      int displayOrder) {
    this.code = normalizeCode(code);
    this.nameVi = Objects.requireNonNull(nameVi, "nameVi is required").trim();
    this.nameEn = Objects.requireNonNull(nameEn, "nameEn is required").trim();
    this.descriptionVi = descriptionVi != null ? descriptionVi.trim() : null;
    this.descriptionEn = descriptionEn != null ? descriptionEn.trim() : null;
    this.category = normalizeCategory(category);
    this.isActive = isActive;
    this.displayOrder = displayOrder;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void update(
      String nameVi,
      String nameEn,
      String descriptionVi,
      String descriptionEn,
      String category,
      boolean isActive,
      int displayOrder) {
    this.nameVi = Objects.requireNonNull(nameVi, "nameVi is required").trim();
    this.nameEn = Objects.requireNonNull(nameEn, "nameEn is required").trim();
    this.descriptionVi = descriptionVi != null ? descriptionVi.trim() : null;
    this.descriptionEn = descriptionEn != null ? descriptionEn.trim() : null;
    this.category = normalizeCategory(category);
    this.isActive = isActive;
    this.displayOrder = displayOrder;
    this.updatedAt = Instant.now();
  }

  public void toggleActive() {
    this.isActive = !this.isActive;
    this.updatedAt = Instant.now();
  }

  public static String normalizeCode(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Feature config code cannot be blank.");
    }
    return code.trim().toUpperCase(Locale.ROOT);
  }

  public static String normalizeCategory(String category) {
    if (category == null || category.isBlank()) {
      return "SAFETY";
    }
    String norm = category.trim().toUpperCase(Locale.ROOT);
    return ("CAPABILITY".equals(norm) || "SAFETY".equals(norm)) ? norm : "SAFETY";
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getNameVi() {
    return nameVi;
  }

  public String getNameEn() {
    return nameEn;
  }

  public String getDescriptionVi() {
    return descriptionVi;
  }

  public String getDescriptionEn() {
    return descriptionEn;
  }

  public String getCategory() {
    return category;
  }

  public boolean isActive() {
    return isActive;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

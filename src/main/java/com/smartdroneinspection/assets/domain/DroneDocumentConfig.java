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
@Table(name = "drone_document_configs")
public class DroneDocumentConfig {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false, unique = true, length = 64)
  private String code;

  @Column(name = "title_vi", nullable = false, length = 255)
  private String titleVi;

  @Column(name = "title_en", nullable = false, length = 255)
  private String titleEn;

  @Column(name = "description_vi", length = 1000)
  private String descriptionVi;

  @Column(name = "description_en", length = 1000)
  private String descriptionEn;

  @Column(name = "is_required", nullable = false)
  private boolean isRequired;

  @Column(name = "is_active", nullable = false)
  private boolean isActive;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected DroneDocumentConfig() {}

  public DroneDocumentConfig(
      String code,
      String titleVi,
      String titleEn,
      String descriptionVi,
      String descriptionEn,
      boolean isRequired,
      boolean isActive,
      int displayOrder) {
    this.code = normalizeCode(code);
    this.titleVi = Objects.requireNonNull(titleVi, "titleVi is required").trim();
    this.titleEn = Objects.requireNonNull(titleEn, "titleEn is required").trim();
    this.descriptionVi = descriptionVi != null ? descriptionVi.trim() : null;
    this.descriptionEn = descriptionEn != null ? descriptionEn.trim() : null;
    this.isRequired = isRequired;
    this.isActive = isActive;
    this.displayOrder = displayOrder;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void update(
      String titleVi,
      String titleEn,
      String descriptionVi,
      String descriptionEn,
      boolean isRequired,
      boolean isActive,
      int displayOrder) {
    this.titleVi = Objects.requireNonNull(titleVi, "titleVi is required").trim();
    this.titleEn = Objects.requireNonNull(titleEn, "titleEn is required").trim();
    this.descriptionVi = descriptionVi != null ? descriptionVi.trim() : null;
    this.descriptionEn = descriptionEn != null ? descriptionEn.trim() : null;
    this.isRequired = isRequired;
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
      throw new IllegalArgumentException("Document config code cannot be blank.");
    }
    return code.trim().toUpperCase(Locale.ROOT);
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getTitleVi() {
    return titleVi;
  }

  public String getTitleEn() {
    return titleEn;
  }

  public String getDescriptionVi() {
    return descriptionVi;
  }

  public String getDescriptionEn() {
    return descriptionEn;
  }

  public boolean isRequired() {
    return isRequired;
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

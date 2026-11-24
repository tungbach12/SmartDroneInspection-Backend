package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.RaterRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Admin-configurable criterion definition for post-inspection Inspector ratings. */
@Entity
@Table(name = "rating_criterion_configs")
public class RatingCriterionConfig {

  @Id @GeneratedValue private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "rater_role", nullable = false, updatable = false, length = 16)
  private RaterRole raterRole;

  @Column(name = "code", nullable = false, updatable = false, length = 64)
  private String code;

  @Column(name = "label_en", nullable = false, length = 120)
  private String labelEn;

  @Column(name = "label_vi", nullable = false, length = 120)
  private String labelVi;

  @Column(name = "description_en", length = 300)
  private String descriptionEn;

  @Column(name = "description_vi", length = 300)
  private String descriptionVi;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected RatingCriterionConfig() {}

  public RatingCriterionConfig(
      RaterRole raterRole,
      String code,
      String labelEn,
      String labelVi,
      String descriptionEn,
      String descriptionVi,
      int displayOrder,
      boolean isActive) {
    this.raterRole = Objects.requireNonNull(raterRole, "Rater role is required");
    this.code = normalizeCode(code);
    this.labelEn = requireText(labelEn, "English label is required");
    this.labelVi = requireText(labelVi, "Vietnamese label is required");
    this.descriptionEn = trimToNull(descriptionEn);
    this.descriptionVi = trimToNull(descriptionVi);
    this.displayOrder = displayOrder;
    this.isActive = isActive;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void update(
      String labelEn,
      String labelVi,
      String descriptionEn,
      String descriptionVi,
      int displayOrder,
      boolean isActive) {
    this.labelEn = requireText(labelEn, "English label is required");
    this.labelVi = requireText(labelVi, "Vietnamese label is required");
    this.descriptionEn = trimToNull(descriptionEn);
    this.descriptionVi = trimToNull(descriptionVi);
    this.displayOrder = displayOrder;
    this.isActive = isActive;
    this.updatedAt = Instant.now();
  }

  public void setActive(boolean active) {
    this.isActive = active;
    this.updatedAt = Instant.now();
  }

  public static String normalizeCode(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Criterion code is required");
    }
    String trimmed = raw.trim();
    if (!trimmed.matches("^[a-zA-Z][a-zA-Z0-9_]{1,63}$")) {
      throw new IllegalArgumentException(
          "Code must start with a letter and use 2-64 alphanumeric or underscore characters.");
    }
    return trimmed;
  }

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public UUID getId() {
    return id;
  }

  public RaterRole getRaterRole() {
    return raterRole;
  }

  public String getCode() {
    return code;
  }

  public String getLabelEn() {
    return labelEn;
  }

  public String getLabelVi() {
    return labelVi;
  }

  public String getDescriptionEn() {
    return descriptionEn;
  }

  public String getDescriptionVi() {
    return descriptionVi;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public boolean isActive() {
    return isActive;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

package com.smartdroneinspection.assets.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "checklist_templates")
public class ChecklistTemplate {

  @Id @GeneratedValue private UUID id;

  @Column(name = "template_type", nullable = false, length = 32)
  private String templateType;

  @Column(nullable = false, length = 64)
  private String category = "GENERAL";

  @Column(nullable = false, length = 300)
  private String label;

  @Column(name = "label_vi", length = 300)
  private String labelVi;

  @Column(length = 1000)
  private String details;

  @Column(name = "details_vi", length = 1000)
  private String detailsVi;

  @Column(name = "reference_threshold", length = 120)
  private String referenceThreshold;

  @Column(name = "safety_critical", nullable = false)
  private boolean safetyCritical;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean active;

  @Column(nullable = false)
  private boolean required;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected ChecklistTemplate() {}

  public ChecklistTemplate(String templateType, String label, int sortOrder, boolean active) {
    this(templateType, label, sortOrder, active, !"POST_FLIGHT".equalsIgnoreCase(templateType));
  }

  public ChecklistTemplate(
      String templateType, String label, int sortOrder, boolean active, boolean required) {
    this(
        templateType, "GENERAL", label, null, null, null, null, false, sortOrder, active, required);
  }

  public ChecklistTemplate(
      String templateType,
      String category,
      String label,
      String labelVi,
      String details,
      String detailsVi,
      String referenceThreshold,
      boolean safetyCritical,
      int sortOrder,
      boolean active,
      boolean required) {
    this.templateType = normalizeType(templateType);
    this.category = normalizeCategory(category);
    this.label = label.trim();
    this.labelVi = trimToNull(labelVi);
    this.details = trimToNull(details);
    this.detailsVi = trimToNull(detailsVi);
    this.referenceThreshold = trimToNull(referenceThreshold);
    this.safetyCritical = safetyCritical;
    this.sortOrder = sortOrder;
    this.active = active;
    this.required = required;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getTemplateType() {
    return templateType;
  }

  public String getCategory() {
    return category;
  }

  public String getLabel() {
    return label;
  }

  public String getLabelVi() {
    return labelVi;
  }

  public String getDetails() {
    return details;
  }

  public String getDetailsVi() {
    return detailsVi;
  }

  public String getReferenceThreshold() {
    return referenceThreshold;
  }

  public boolean isSafetyCritical() {
    return safetyCritical;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isActive() {
    return active;
  }

  public boolean isRequired() {
    return required;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void update(String newLabel, Integer newSortOrder, Boolean newActive) {
    update(newLabel, newSortOrder, newActive, null);
  }

  public void update(
      String newLabel, Integer newSortOrder, Boolean newActive, Boolean newRequired) {
    update(null, newLabel, null, null, null, null, null, newSortOrder, newActive, newRequired);
  }

  public void update(
      String newCategory,
      String newLabel,
      String newLabelVi,
      String newDetails,
      String newDetailsVi,
      String newReferenceThreshold,
      Boolean newSafetyCritical,
      Integer newSortOrder,
      Boolean newActive,
      Boolean newRequired) {
    if (newCategory != null && !newCategory.isBlank()) {
      this.category = normalizeCategory(newCategory);
    }
    if (newLabel != null && !newLabel.isBlank()) {
      this.label = newLabel.trim();
    }
    if (newLabelVi != null) {
      this.labelVi = trimToNull(newLabelVi);
    }
    if (newDetails != null) {
      this.details = trimToNull(newDetails);
    }
    if (newDetailsVi != null) {
      this.detailsVi = trimToNull(newDetailsVi);
    }
    if (newReferenceThreshold != null) {
      this.referenceThreshold = trimToNull(newReferenceThreshold);
    }
    if (newSafetyCritical != null) {
      this.safetyCritical = newSafetyCritical;
    }
    if (newSortOrder != null) {
      this.sortOrder = newSortOrder;
    }
    if (newActive != null) {
      this.active = newActive;
    }
    if (newRequired != null) {
      this.required = newRequired;
    }
    this.updatedAt = Instant.now();
  }

  private static String normalizeType(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static String normalizeCategory(String value) {
    if (value == null || value.isBlank()) {
      return "GENERAL";
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}

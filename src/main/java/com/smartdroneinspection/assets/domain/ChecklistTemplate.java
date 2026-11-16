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

  @Column(nullable = false, length = 300)
  private String label;

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
    this.templateType = normalizeType(templateType);
    this.label = label.trim();
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

  public String getLabel() {
    return label;
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
    if (newLabel != null && !newLabel.isBlank()) {
      this.label = newLabel.trim();
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
}

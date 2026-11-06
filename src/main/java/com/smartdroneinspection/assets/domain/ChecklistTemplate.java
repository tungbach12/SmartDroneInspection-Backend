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

  @Column(name = "category_id")
  private UUID categoryId;

  @Column(nullable = false, length = 300)
  private String label;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected ChecklistTemplate() {}

  public ChecklistTemplate(
      String templateType, UUID categoryId, String label, int sortOrder, boolean active) {
    this.templateType = normalizeType(templateType);
    this.categoryId = categoryId;
    this.label = label.trim();
    this.sortOrder = sortOrder;
    this.active = active;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getTemplateType() {
    return templateType;
  }

  public UUID getCategoryId() {
    return categoryId;
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void update(String newLabel, Integer newSortOrder, Boolean newActive) {
    if (newLabel != null && !newLabel.isBlank()) {
      this.label = newLabel.trim();
    }
    if (newSortOrder != null) {
      this.sortOrder = newSortOrder;
    }
    if (newActive != null) {
      this.active = newActive;
    }
    this.updatedAt = Instant.now();
  }

  private static String normalizeType(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }
}

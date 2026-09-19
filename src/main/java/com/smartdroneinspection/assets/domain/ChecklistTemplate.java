package com.smartdroneinspection.assets.domain;

import com.smartdroneinspection.assets.domain.enums.ChecklistResponseType;
import com.smartdroneinspection.assets.domain.enums.ChecklistTemplateStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
    name = "checklist_templates",
    uniqueConstraints = @UniqueConstraint(columnNames = {"template_key", "version_number"}))
public class ChecklistTemplate {

  @Id @GeneratedValue private UUID id;

  @Column(name = "template_key", nullable = false, length = 64)
  private String templateKey;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Column(name = "asset_category_id")
  private UUID assetCategoryId;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(length = 2000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ChecklistTemplateStatus status;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  @OneToMany(
      mappedBy = "template",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("displayOrder ASC")
  private List<ChecklistItem> items = new ArrayList<>();

  protected ChecklistTemplate() {}

  public ChecklistTemplate(
      String templateKey,
      int versionNumber,
      UUID assetCategoryId,
      String name,
      String description,
      UUID createdByUserId) {
    if (versionNumber <= 0) {
      throw new IllegalArgumentException("Checklist template version must be positive");
    }
    this.templateKey = normalizeCode(templateKey);
    this.versionNumber = versionNumber;
    this.assetCategoryId = assetCategoryId;
    this.name = name;
    this.description = description;
    this.status = ChecklistTemplateStatus.DRAFT;
    this.createdByUserId = createdByUserId;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public ChecklistItem addItem(
      String itemCode,
      String sectionName,
      String prompt,
      ChecklistResponseType responseType,
      boolean required,
      int displayOrder,
      String guidance,
      String validationConfig) {
    if (status != ChecklistTemplateStatus.DRAFT) {
      throw new IllegalStateException("Published checklist template is immutable");
    }
    ChecklistItem item =
        new ChecklistItem(
            this,
            itemCode,
            sectionName,
            prompt,
            responseType,
            required,
            displayOrder,
            guidance,
            validationConfig);
    items.add(item);
    updatedAt = Instant.now();
    return item;
  }

  public void publish() {
    if (items.isEmpty()) {
      throw new IllegalStateException("Checklist template must contain at least one item");
    }
    if (status != ChecklistTemplateStatus.DRAFT) {
      throw new IllegalStateException("Only draft checklist templates can be published");
    }
    status = ChecklistTemplateStatus.ACTIVE;
    publishedAt = Instant.now();
    updatedAt = publishedAt;
  }

  public void retire() {
    if (status != ChecklistTemplateStatus.ACTIVE) {
      throw new IllegalStateException("Only active checklist templates can be retired");
    }
    status = ChecklistTemplateStatus.RETIRED;
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getTemplateKey() {
    return templateKey;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public ChecklistTemplateStatus getStatus() {
    return status;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public List<ChecklistItem> getItems() {
    return List.copyOf(items);
  }

  private static String normalizeCode(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }
}

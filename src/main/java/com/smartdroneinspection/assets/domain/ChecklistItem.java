package com.smartdroneinspection.assets.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "checklist_items",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"template_id", "item_code"}),
      @UniqueConstraint(columnNames = {"template_id", "display_order"})
    })
public class ChecklistItem {

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "template_id", nullable = false)
  private ChecklistTemplate template;

  @Column(name = "item_code", nullable = false, length = 64)
  private String itemCode;

  @Column(name = "section_name", length = 160)
  private String sectionName;

  @Column(nullable = false, length = 1000)
  private String prompt;

  @Enumerated(EnumType.STRING)
  @Column(name = "response_type", nullable = false, length = 24)
  private ChecklistResponseType responseType;

  @Column(nullable = false)
  private boolean required;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(length = 2000)
  private String guidance;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "validation_config", columnDefinition = "jsonb")
  private String validationConfig;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ChecklistItem() {}

  ChecklistItem(
      ChecklistTemplate template,
      String itemCode,
      String sectionName,
      String prompt,
      ChecklistResponseType responseType,
      boolean required,
      int displayOrder,
      String guidance,
      String validationConfig) {
    if (displayOrder < 0) {
      throw new IllegalArgumentException("Checklist item display order must not be negative");
    }
    this.template = template;
    this.itemCode = normalizeCode(itemCode);
    this.sectionName = sectionName;
    this.prompt = prompt;
    this.responseType = responseType;
    this.required = required;
    this.displayOrder = displayOrder;
    this.guidance = guidance;
    this.validationConfig = validationConfig;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getItemCode() {
    return itemCode;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  private static String normalizeCode(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }
}

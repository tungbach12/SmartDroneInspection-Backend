package com.smartdroneinspection.assets.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(
    name = "category_frequency_suggestions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_category_frequency",
            columnNames = {"asset_category_id", "frequency_unit", "frequency_interval"}))
public class CategoryFrequencySuggestion {

  @Id @GeneratedValue private UUID id;

  @Column(name = "asset_category_id", nullable = false)
  private UUID assetCategoryId;

  @Column(name = "frequency_unit", nullable = false, length = 16)
  private String frequencyUnit;

  @Column(name = "frequency_interval", nullable = false)
  private int frequencyInterval;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected CategoryFrequencySuggestion() {}

  public CategoryFrequencySuggestion(
      UUID assetCategoryId, String frequencyUnit, int frequencyInterval, int sortOrder) {
    if (frequencyInterval <= 0) {
      throw new IllegalArgumentException("frequencyInterval must be positive");
    }
    this.assetCategoryId = assetCategoryId;
    this.frequencyUnit = frequencyUnit;
    this.frequencyInterval = frequencyInterval;
    this.sortOrder = sortOrder;
  }

  public UUID getId() {
    return id;
  }

  public UUID getAssetCategoryId() {
    return assetCategoryId;
  }

  public String getFrequencyUnit() {
    return frequencyUnit;
  }

  public int getFrequencyInterval() {
    return frequencyInterval;
  }

  public int getSortOrder() {
    return sortOrder;
  }
}

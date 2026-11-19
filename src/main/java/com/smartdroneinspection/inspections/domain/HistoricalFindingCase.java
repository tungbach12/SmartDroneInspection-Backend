package com.smartdroneinspection.inspections.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A confirmed finding of a published inspection report, indexed for historical recommendations.
 *
 * <p>Private cases keep their owning organization id and are only retrievable inside that tenant;
 * anonymized benchmark cases carry a null organization id and are shared across tenants. The {@code
 * embedding} column is read and written with parameterized JDBC rather than mapped as a JPA
 * pgvector attribute.
 */
@Entity
@Table(name = "historical_finding_cases")
public class HistoricalFindingCase {

  @Id @GeneratedValue private UUID id;

  @Column(name = "source_finding_id", unique = true)
  private UUID sourceFindingId;

  @Column(name = "source_inspection_id")
  private UUID sourceInspectionId;

  @Column(name = "organization_id")
  private UUID organizationId;

  @Column(name = "asset_id")
  private UUID assetId;

  @Column(name = "asset_category_code", nullable = false, length = 64)
  private String assetCategoryCode;

  @Column(name = "asset_label", nullable = false, length = 255)
  private String assetLabel;

  @Column(name = "defect_label", nullable = false, length = 160)
  private String defectLabel;

  @Column(name = "severity", nullable = false, length = 16)
  private String severity;

  @Column(name = "technical_notes", nullable = false, length = 4000)
  private String technicalNotes;

  @Column(name = "report_outcome", nullable = false, length = 64)
  private String reportOutcome;

  @Column(name = "is_anonymized_benchmark", nullable = false)
  private boolean anonymizedBenchmark;

  @Column(name = "published_at", nullable = false, updatable = false)
  private Instant publishedAt;

  protected HistoricalFindingCase() {}

  public UUID getId() {
    return id;
  }

  public UUID getSourceFindingId() {
    return sourceFindingId;
  }

  public UUID getSourceInspectionId() {
    return sourceInspectionId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getAssetId() {
    return assetId;
  }

  public String getAssetCategoryCode() {
    return assetCategoryCode;
  }

  public String getAssetLabel() {
    return assetLabel;
  }

  public String getDefectLabel() {
    return defectLabel;
  }

  public String getSeverity() {
    return severity;
  }

  public String getTechnicalNotes() {
    return technicalNotes;
  }

  public String getReportOutcome() {
    return reportOutcome;
  }

  public boolean isAnonymizedBenchmark() {
    return anonymizedBenchmark;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }
}

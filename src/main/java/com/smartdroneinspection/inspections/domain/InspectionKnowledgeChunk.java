package com.smartdroneinspection.inspections.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A published inspection guideline chunk used to ground AI summaries in engineering references.
 *
 * <p>The {@code embedding} column is intentionally absent from this mapping: pgvector is read and
 * written with parameterized JDBC so the module does not depend on a Hibernate pgvector type. Rows
 * are seeded by Flyway, so the entity only exists to keep the runtime persistence inventory honest.
 */
@Entity
@Table(name = "inspection_knowledge_chunks")
public class InspectionKnowledgeChunk {

  @Id @GeneratedValue private UUID id;

  @Column(name = "standard_code", nullable = false, length = 64)
  private String standardCode;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "asset_category_code", nullable = false, length = 64)
  private String assetCategoryCode;

  @Column(name = "defect_patterns", nullable = false, length = 255)
  private String defectPatterns;

  @Column(name = "severity", nullable = false, length = 16)
  private String severity;

  @Column(name = "guidance", nullable = false, length = 4000)
  private String guidance;

  @Column(name = "reference_url", length = 500)
  private String referenceUrl;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected InspectionKnowledgeChunk() {}

  public UUID getId() {
    return id;
  }

  public String getStandardCode() {
    return standardCode;
  }

  public String getTitle() {
    return title;
  }

  public String getAssetCategoryCode() {
    return assetCategoryCode;
  }

  public String getDefectPatterns() {
    return defectPatterns;
  }

  public String getSeverity() {
    return severity;
  }

  public String getGuidance() {
    return guidance;
  }

  public String getReferenceUrl() {
    return referenceUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

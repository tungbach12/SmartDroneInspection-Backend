package com.smartdroneinspection.maintenance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Reference engineering standard and repair procedure for RAG-assisted maintenance planning. */
@Entity
@Table(name = "repair_knowledge_chunks")
public class RepairKnowledgeChunk {

  @Id @GeneratedValue private UUID id;

  @Column(name = "standard_code", nullable = false, length = 64)
  private String standardCode;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, length = 64)
  private String category;

  @Column(name = "defect_patterns", nullable = false, length = 255)
  private String defectPatterns;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Column(name = "reference_url", length = 500)
  private String referenceUrl;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected RepairKnowledgeChunk() {}

  public RepairKnowledgeChunk(
      String standardCode,
      String title,
      String category,
      String defectPatterns,
      String content,
      String referenceUrl) {
    this.standardCode = standardCode;
    this.title = title;
    this.category = category;
    this.defectPatterns = defectPatterns;
    this.content = content;
    this.referenceUrl = referenceUrl;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getStandardCode() {
    return standardCode;
  }

  public String getTitle() {
    return title;
  }

  public String getCategory() {
    return category;
  }

  public String getDefectPatterns() {
    return defectPatterns;
  }

  public String getContent() {
    return content;
  }

  public String getReferenceUrl() {
    return referenceUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.PeerReviewDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "peer_reviews")
public class PeerReview {

  @Id @GeneratedValue private UUID id;

  @Column(name = "report_version_id", nullable = false, unique = true)
  private UUID reportVersionId;

  @Column(name = "reviewer_user_id", nullable = false)
  private UUID reviewerUserId;

  @Column(name = "assigned_by_user_id", nullable = false)
  private UUID assignedByUserId;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private PeerReviewDecision decision;

  @Column(length = 4000)
  private String comments;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PeerReview() {}

  public PeerReview(
      UUID reportVersionId, UUID reviewerUserId, UUID assignedByUserId, Instant assignedAt) {
    this.reportVersionId = reportVersionId;
    this.reviewerUserId = reviewerUserId;
    this.assignedByUserId = assignedByUserId;
    this.assignedAt = assignedAt;
    this.decision = PeerReviewDecision.PENDING;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getReportVersionId() {
    return reportVersionId;
  }

  public PeerReviewDecision getDecision() {
    return decision;
  }
}

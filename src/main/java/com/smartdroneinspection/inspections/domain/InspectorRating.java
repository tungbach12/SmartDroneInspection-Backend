package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.RaterRole;
import com.smartdroneinspection.shared.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.http.HttpStatus;

/** Subjective post-inspection rating of the assigned Inspector by Customer or Manager. */
@Entity
@Table(name = "inspector_ratings")
public class InspectorRating {

  public static final Duration EDIT_WINDOW = Duration.ofDays(7);
  public static final int MAX_COMMENT_LENGTH = 2000;

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspection_id", nullable = false, updatable = false)
  private UUID inspectionId;

  @Column(name = "inspector_id", nullable = false, updatable = false)
  private UUID inspectorId;

  @Column(name = "rater_user_id", nullable = false)
  private UUID raterUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "rater_role", nullable = false, updatable = false, length = 16)
  private RaterRole raterRole;

  @Column(name = "overall_score", nullable = false)
  private int overallScore;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "criterion_scores", columnDefinition = "jsonb", nullable = false)
  private Map<String, Integer> criterionScores = new LinkedHashMap<>();

  @Column(name = "comment", columnDefinition = "text")
  private String comment;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected InspectorRating() {}

  public InspectorRating(
      UUID inspectionId,
      UUID inspectorId,
      UUID raterUserId,
      RaterRole raterRole,
      int overallScore,
      Map<String, Integer> criterionScores,
      String comment,
      Instant now) {
    this.inspectionId = Objects.requireNonNull(inspectionId, "Inspection ID is required");
    this.inspectorId = Objects.requireNonNull(inspectorId, "Inspector ID is required");
    this.raterUserId = Objects.requireNonNull(raterUserId, "Rater user ID is required");
    this.raterRole = Objects.requireNonNull(raterRole, "Rater role is required");
    this.overallScore = validateScore("overallScore", overallScore);
    this.criterionScores = validateAndNormalizeCriteria(raterRole, criterionScores);
    this.comment = normalizeComment(comment);
    Instant timestamp = now != null ? now : Instant.now();
    this.createdAt = timestamp;
    this.updatedAt = timestamp;
  }

  public Instant editableUntil() {
    return createdAt.plus(EDIT_WINDOW);
  }

  public boolean isEditableAt(Instant now) {
    Instant reference = now != null ? now : Instant.now();
    return !reference.isAfter(editableUntil());
  }

  public void updateRating(
      UUID editorUserId,
      int overallScore,
      Map<String, Integer> criterionScores,
      String comment,
      Instant now) {
    Instant timestamp = now != null ? now : Instant.now();
    if (!isEditableAt(timestamp)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "RATING_EDIT_WINDOW_EXPIRED",
          "Ratings can only be edited within 7 days of initial submission.");
    }
    this.raterUserId = Objects.requireNonNull(editorUserId, "Rater user ID is required");
    this.overallScore = validateScore("overallScore", overallScore);
    this.criterionScores = validateAndNormalizeCriteria(this.raterRole, criterionScores);
    this.comment = normalizeComment(comment);
    this.updatedAt = timestamp;
  }

  private static int validateScore(String fieldName, Integer value) {
    if (value == null || value < 1 || value > 5) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_RATING_PAYLOAD",
          fieldName + " must be an integer between 1 and 5.");
    }
    return value;
  }

  private static Map<String, Integer> validateAndNormalizeCriteria(
      RaterRole role, Map<String, Integer> raw) {
    if (raw == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_RATING_PAYLOAD",
          "criterionScores is required for role " + role.name() + ".");
    }
    List<String> expectedKeys = role.expectedCriteriaKeys();
    if (raw.size() != expectedKeys.size() || !raw.keySet().containsAll(expectedKeys)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_RATING_PAYLOAD",
          "criterionScores must contain exactly: " + String.join(", ", expectedKeys) + ".");
    }
    Map<String, Integer> normalized = new LinkedHashMap<>();
    for (String key : expectedKeys) {
      normalized.put(key, validateScore("criterionScores." + key, raw.get(key)));
    }
    return normalized;
  }

  private static String normalizeComment(String comment) {
    if (comment == null) {
      return null;
    }
    String trimmed = comment.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.length() > MAX_COMMENT_LENGTH) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_RATING_PAYLOAD",
          "Comment must not exceed " + MAX_COMMENT_LENGTH + " characters.");
    }
    return trimmed;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getInspectorId() {
    return inspectorId;
  }

  public UUID getRaterUserId() {
    return raterUserId;
  }

  public RaterRole getRaterRole() {
    return raterRole;
  }

  public int getOverallScore() {
    return overallScore;
  }

  public Map<String, Integer> getCriterionScores() {
    return criterionScores == null ? Map.of() : Map.copyOf(criterionScores);
  }

  public String getComment() {
    return comment;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

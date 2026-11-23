package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InspectorRatingResponse(
    UUID id,
    UUID inspectionId,
    UUID inspectorId,
    UUID raterUserId,
    String raterName,
    String raterAvatarUrl,
    String raterRole,
    String assetName,
    int overallScore,
    Map<String, Integer> criterionScores,
    String comment,
    Instant createdAt,
    Instant updatedAt,
    Instant editableUntil,
    boolean editable) {
  public InspectorRatingResponse {
    criterionScores = criterionScores == null ? Map.of() : Map.copyOf(criterionScores);
  }
}

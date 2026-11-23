package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionRatingBundleResponse(
    UUID inspectionId,
    UUID inspectorId,
    String inspectorName,
    boolean canRate,
    String ineligibleReason,
    String actorRole,
    List<String> expectedCriteriaKeys,
    Instant editableUntil,
    InspectorRatingResponse myRating,
    InspectorRatingResponse customerRating,
    InspectorRatingResponse managerRating,
    Double inspectorAverageRating,
    long inspectorRatingCount) {
  public InspectionRatingBundleResponse {
    expectedCriteriaKeys =
        expectedCriteriaKeys == null ? List.of() : List.copyOf(expectedCriteriaKeys);
  }
}

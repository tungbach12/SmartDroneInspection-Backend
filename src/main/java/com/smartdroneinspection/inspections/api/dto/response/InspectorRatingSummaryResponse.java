package com.smartdroneinspection.inspections.api.dto.response;

import java.util.Map;
import java.util.UUID;

public record InspectorRatingSummaryResponse(
    UUID inspectorId,
    Double averageRating,
    long ratingCount,
    Double customerAverageRating,
    long customerRatingCount,
    Double managerAverageRating,
    long managerRatingCount,
    Map<Integer, Long> starDistribution,
    Map<String, Double> customerCriteriaAverages,
    Map<String, Double> managerCriteriaAverages) {
  public InspectorRatingSummaryResponse {
    starDistribution = starDistribution == null ? Map.of() : Map.copyOf(starDistribution);
    customerCriteriaAverages =
        customerCriteriaAverages == null ? Map.of() : Map.copyOf(customerCriteriaAverages);
    managerCriteriaAverages =
        managerCriteriaAverages == null ? Map.of() : Map.copyOf(managerCriteriaAverages);
  }
}

package com.smartdroneinspection.inspections.api.dto.response;

import java.util.List;

public record InspectorRatingProfileResponse(
    InspectorRatingSummaryResponse summary,
    List<InspectorRatingResponse> ratings,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean historyVisible) {
  public InspectorRatingProfileResponse {
    ratings = ratings == null ? List.of() : List.copyOf(ratings);
  }
}

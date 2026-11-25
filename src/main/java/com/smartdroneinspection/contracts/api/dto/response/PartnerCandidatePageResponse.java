package com.smartdroneinspection.contracts.api.dto.response;

import java.util.List;

/** Paginated partner Inspector + Drone evaluation results for dispatch and reassignment. */
public record PartnerCandidatePageResponse(
    List<PartnerCandidateResponse> items,
    int page,
    int pageSize,
    long totalCount,
    int totalPages,
    long eligibleCount,
    long unavailableCount,
    long allEvaluatedCount) {

  public PartnerCandidatePageResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }
}

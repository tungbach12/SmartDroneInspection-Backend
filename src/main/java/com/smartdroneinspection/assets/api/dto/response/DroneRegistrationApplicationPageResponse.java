package com.smartdroneinspection.assets.api.dto.response;

import java.util.List;

/** Paginated view of partner drone registration applications for Governance reviewers. */
public record DroneRegistrationApplicationPageResponse(
    List<DroneRegistrationApplicationResponse> items,
    int page,
    int pageSize,
    long totalCount,
    int totalPages,
    long totalAllCount,
    long pendingCount,
    long approvedCount,
    long rejectedCount) {

  public DroneRegistrationApplicationPageResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }
}

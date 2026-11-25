package com.smartdroneinspection.contracts.api.dto.response;

import java.util.List;

/** Paginated partner drone listing for Managers and Admins. */
public record PartnerDronePageResponse(
    List<PartnerDroneResponse> items, int page, int pageSize, long totalCount, int totalPages) {

  public PartnerDronePageResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }
}

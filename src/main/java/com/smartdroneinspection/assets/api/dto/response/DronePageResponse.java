package com.smartdroneinspection.assets.api.dto.response;

import java.util.List;

/** Paginated list of drones matching the shared page envelope contract. */
public record DronePageResponse(
    List<DroneResponse> items, int page, int pageSize, long totalCount, int totalPages) {

  public DronePageResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }
}

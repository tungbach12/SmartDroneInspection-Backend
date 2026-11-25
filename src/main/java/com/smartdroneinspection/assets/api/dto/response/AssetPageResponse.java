package com.smartdroneinspection.assets.api.dto.response;

import java.util.List;

public record AssetPageResponse(
    List<AssetResponse> items,
    int page,
    int pageSize,
    long totalCount,
    int totalPages,
    long allCount,
    long activeCount,
    long checkpointsConfiguredCount) {

  public AssetPageResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }

  /** Backward-compatible constructor for callers/tests omitting organization summary counts. */
  public AssetPageResponse(
      List<AssetResponse> items, int page, int pageSize, long totalCount, int totalPages) {
    this(items, page, pageSize, totalCount, totalPages, totalCount, 0L, 0L);
  }
}

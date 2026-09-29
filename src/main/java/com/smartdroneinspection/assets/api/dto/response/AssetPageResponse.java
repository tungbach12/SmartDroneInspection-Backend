package com.smartdroneinspection.assets.api.dto.response;

import java.util.List;

public record AssetPageResponse(
    List<AssetResponse> items, int page, int pageSize, long totalCount, int totalPages) {}

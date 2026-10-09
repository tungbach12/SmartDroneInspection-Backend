package com.smartdroneinspection.inspections.api.dto.response;

import java.util.List;

/** Page numbers start at 1, matching the asset list contract. */
public record InspectionPageResponse(
    List<InspectionListItemResponse> items,
    int page,
    int pageSize,
    long totalCount,
    int totalPages) {}

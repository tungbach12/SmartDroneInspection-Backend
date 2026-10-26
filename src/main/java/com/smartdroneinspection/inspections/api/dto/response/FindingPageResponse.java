package com.smartdroneinspection.inspections.api.dto.response;

import java.util.List;

public record FindingPageResponse(
    List<FindingResponse> items,
    int page,
    int pageSize,
    long totalCount,
    int totalPages,
    long activeCount,
    long removedCount,
    long pendingCount,
    long confirmedCount) {}

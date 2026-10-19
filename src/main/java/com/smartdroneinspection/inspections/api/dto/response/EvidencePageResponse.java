package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.List;

/** Page numbers start at 1, matching the inspection list contract. */
public record EvidencePageResponse(
    List<EvidenceResponse> items,
    int page,
    int pageSize,
    long totalCount,
    int totalPages,
    long activeCount,
    long retiredCount,
    Instant latestCreatedAt,
    Instant latestRetiredAt) {}

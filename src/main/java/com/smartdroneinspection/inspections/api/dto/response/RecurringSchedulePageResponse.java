package com.smartdroneinspection.inspections.api.dto.response;

import java.util.List;

public record RecurringSchedulePageResponse(
    List<RecurringScheduleResponse> items,
    int page,
    int pageSize,
    long totalCount,
    int totalPages) {}

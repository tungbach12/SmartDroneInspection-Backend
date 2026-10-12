package com.smartdroneinspection.maintenance.api.dto.response;

import java.util.List;

/** A server-paged, organization-scoped page of work orders. */
public record WorkOrderPageResponse(
    List<WorkOrderResponse> items, int page, int size, long total) {}

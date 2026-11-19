package com.smartdroneinspection.users.api.dto.response;

import java.util.List;

public record AuditLogPageResponse(
    List<AuditLogEntryResponse> items,
    int page,
    int size,
    long total,
    int totalPages,
    List<String> availableActions) {}

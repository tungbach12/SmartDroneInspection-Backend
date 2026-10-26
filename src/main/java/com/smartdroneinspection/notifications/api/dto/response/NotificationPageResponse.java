package com.smartdroneinspection.notifications.api.dto.response;

import java.util.List;

public record NotificationPageResponse(
    List<NotificationResponse> items, int page, int pageSize, long totalCount, int totalPages) {}

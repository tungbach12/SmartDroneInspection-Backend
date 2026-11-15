package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;

public record MediaTicketResponse(String ticket, String streamUrl, Instant expiresAt) {}

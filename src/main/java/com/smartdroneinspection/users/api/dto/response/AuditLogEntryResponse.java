package com.smartdroneinspection.users.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AuditLogEntryResponse(
    UUID id,
    UUID actorUserId,
    String actorEmail,
    String actorFullName,
    String action,
    String aggregateType,
    UUID aggregateId,
    String outcome,
    String traceId,
    Instant createdAt) {}

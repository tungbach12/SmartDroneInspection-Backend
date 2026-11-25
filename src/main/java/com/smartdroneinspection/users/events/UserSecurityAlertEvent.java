package com.smartdroneinspection.users.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on security-sensitive account events (password change/reset, session revocation,
 * administrator role/status updates).
 */
public record UserSecurityAlertEvent(
    UUID userId,
    String email,
    String fullName,
    String alertKind,
    String detail,
    Instant occurredAt) {}

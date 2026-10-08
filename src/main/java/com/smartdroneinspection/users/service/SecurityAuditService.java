package com.smartdroneinspection.users.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAuditService {

  private final JdbcTemplate jdbcTemplate;

  public SecurityAuditService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public void record(
      UUID actorUserId,
      UUID subjectUserId,
      String eventType,
      String outcome,
      String ipAddress,
      String userAgent,
      String correlationId) {
    UUID aggregateId = subjectUserId != null ? subjectUserId : actorUserId;
    String aggregateType = subjectUserId != null ? "USER" : "AUTHENTICATION_ATTEMPT";
    if (aggregateId == null) {
      aggregateId = UUID.randomUUID();
    }

    jdbcTemplate.update(
        """
        INSERT INTO audit_events
          (organization_id, actor_user_id, action, aggregate_type, aggregate_id,
           after_status, trace_id, safe_metadata, created_at)
        VALUES
          ((SELECT organization_id FROM users WHERE id = ?), ?, ?, ?, ?, ?, ?,
           jsonb_build_object('ip_address', ?::text, 'user_agent', ?::text), ?)
        """,
        subjectUserId,
        actorUserId,
        eventType,
        aggregateType,
        aggregateId,
        outcome,
        truncate(correlationId, 128),
        normalizeIp(ipAddress),
        truncate(userAgent, 1000),
        Timestamp.from(Instant.now()));
  }

  private String normalizeIp(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}

package com.smartdroneinspection.users.service;

import java.sql.Timestamp;
import java.sql.Types;
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
    jdbcTemplate.update(
        connection -> {
          var statement =
              connection.prepareStatement(
                  """
                  INSERT INTO security_audit_events
                    (actor_user_id, subject_user_id, event_type, outcome, ip_address,
                     user_agent, correlation_id, occurred_at)
                  VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                  """);
          if (actorUserId == null) {
            statement.setNull(1, Types.OTHER);
          } else {
            statement.setObject(1, actorUserId);
          }
          if (subjectUserId == null) {
            statement.setNull(2, Types.OTHER);
          } else {
            statement.setObject(2, subjectUserId);
          }
          statement.setString(3, eventType);
          statement.setString(4, outcome);
          statement.setString(5, normalizeIp(ipAddress));
          statement.setString(6, truncate(userAgent, 1000));
          statement.setString(7, truncate(correlationId, 128));
          statement.setTimestamp(8, Timestamp.from(Instant.now()));
          return statement;
        });
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

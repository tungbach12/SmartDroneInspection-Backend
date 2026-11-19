package com.smartdroneinspection.users.service;

import com.smartdroneinspection.users.api.dto.response.AuditLogEntryResponse;
import com.smartdroneinspection.users.api.dto.response.AuditLogPageResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only platform activity history for the administrator workspace. */
@Service
public class AdminAuditLogService {

  private static final int MAX_DAY_WINDOW = 3650;

  private final JdbcTemplate jdbcTemplate;

  public AdminAuditLogService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(readOnly = true)
  public AuditLogPageResponse list(
      int page, int size, String action, String query, String outcome, Integer days) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 200);

    String actionFilter = normalize(action);
    String outcomeFilter = normalize(outcome);
    String queryFilter = normalize(query);
    Integer dayWindow = days != null && days > 0 ? Math.min(days, MAX_DAY_WINDOW) : null;

    StringBuilder where = new StringBuilder(" where 1 = 1");
    List<Object> filterParams = new ArrayList<>();

    if (!actionFilter.isEmpty()) {
      where.append(" and e.action = ?");
      filterParams.add(actionFilter);
    }
    if (!outcomeFilter.isEmpty()) {
      where.append(" and e.after_status = ?");
      filterParams.add(outcomeFilter);
    }
    if (!queryFilter.isEmpty()) {
      String pattern = "%" + queryFilter.toLowerCase(Locale.ROOT) + "%";
      where.append(
          """
           and (
             lower(coalesce(u.email, '')) like ?
             or lower(coalesce(u.full_name, '')) like ?
             or lower(cast(e.aggregate_id as text)) like ?
             or lower(coalesce(e.trace_id, '')) like ?
             or lower(e.action) like ?
           )
          """);
      filterParams.add(pattern);
      filterParams.add(pattern);
      filterParams.add(pattern);
      filterParams.add(pattern);
      filterParams.add(pattern);
    }
    if (dayWindow != null) {
      where.append(" and e.created_at >= ?");
      filterParams.add(Timestamp.from(Instant.now().minus(Duration.ofDays(dayWindow))));
    }

    String fromClause = " from audit_events e left join users u on u.id = e.actor_user_id";

    long total =
        Optional.ofNullable(
                jdbcTemplate.queryForObject(
                    "select count(*)" + fromClause + where, Long.class, filterParams.toArray()))
            .orElse(0L);

    List<Object> pageParams = new ArrayList<>(filterParams);
    pageParams.add(safeSize);
    pageParams.add((long) safePage * safeSize);

    List<AuditLogEntryResponse> items =
        jdbcTemplate.query(
            """
            select e.id, e.actor_user_id, u.email as actor_email, u.full_name as actor_full_name,
                   e.action, e.aggregate_type, e.aggregate_id, e.after_status, e.trace_id,
                   e.created_at
            """
                + fromClause
                + where
                + " order by e.created_at desc, e.id desc limit ? offset ?",
            (rs, rowNum) -> {
              Timestamp created = rs.getTimestamp("created_at");
              return new AuditLogEntryResponse(
                  rs.getObject("id", UUID.class),
                  rs.getObject("actor_user_id", UUID.class),
                  rs.getString("actor_email"),
                  rs.getString("actor_full_name"),
                  rs.getString("action"),
                  rs.getString("aggregate_type"),
                  rs.getObject("aggregate_id", UUID.class),
                  rs.getString("after_status"),
                  rs.getString("trace_id"),
                  created != null ? created.toInstant() : Instant.now());
            },
            pageParams.toArray());

    int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
    return new AuditLogPageResponse(
        items, safePage, safeSize, total, totalPages, availableActions());
  }

  private List<String> availableActions() {
    return jdbcTemplate.queryForList(
        "select distinct action from audit_events order by action", String.class);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}

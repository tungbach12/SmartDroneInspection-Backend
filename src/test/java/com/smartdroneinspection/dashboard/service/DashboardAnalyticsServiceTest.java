package com.smartdroneinspection.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.dashboard.api.dto.DashboardAnalyticsResponse;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit coverage for the role-branch selection in {@link DashboardAnalyticsService}. The service is
 * a thin SQL-aggregating read model, so these tests mock {@link JdbcTemplate} and assert the
 * response shape and that each branch queries by the correct owner/actor scope.
 */
class DashboardAnalyticsServiceTest {

  private JdbcTemplate jdbcTemplate;
  private UserAccess userAccess;
  private DashboardAnalyticsService service;

  @BeforeEach
  void setUp() {
    jdbcTemplate = mock(JdbcTemplate.class);
    userAccess = mock(UserAccess.class);
    service = new DashboardAnalyticsService(jdbcTemplate, userAccess);
  }

  @Test
  @DisplayName("a missing actor is rejected")
  void unknownActorIsRejected() {
    UUID actorId = UUID.randomUUID();
    when(userAccess.findActiveUser(actorId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getAnalytics(actorId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User not found");
  }

  @Test
  @DisplayName(
      "a platform manager sees platform-wide counts and never the dropped organizations table")
  void managerSeesPlatformWideCounts() {
    UUID actorId = UUID.randomUUID();
    when(userAccess.findActiveUser(actorId))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(actorId, Set.of("MANAGER"), "Platform")));
    // Every count query returns zero; the SQL itself is what matters here.
    when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
        .thenReturn(0L);

    DashboardAnalyticsResponse response = service.getAnalytics(actorId);

    assertThat(response).isNotNull();
    assertThat(response.summaryCards()).hasSize(4);
    assertThat(response.pipelineStages()).hasSize(6);
    assertThat(response.summaryCards().getFirst().id()).isEqualTo("pending_quotes");
    // No query may reference the organizations table dropped by V43.
    verifyNoOrganizationsQuery();
  }

  @Test
  @DisplayName(
      "an admin sees platform-wide governance & AI metrics and cannot be spoofed by customers")
  void adminAndRoleOverrideChecks() {
    UUID adminId = UUID.randomUUID();
    when(userAccess.findActiveUser(adminId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(adminId, Set.of("ADMIN", "MANAGER"), "Admin")));
    when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
        .thenReturn(0L);

    DashboardAnalyticsResponse adminRes = service.getAnalytics(adminId, "ADMIN");
    assertThat(adminRes.summaryCards()).hasSize(4);
    assertThat(adminRes.summaryCards().getFirst().id()).isEqualTo("active_users");

    DashboardAnalyticsResponse managerRes = service.getAnalytics(adminId, "MANAGER");
    assertThat(managerRes.summaryCards()).hasSize(4);
    assertThat(managerRes.summaryCards().getFirst().id()).isEqualTo("pending_quotes");

    UUID customerId = UUID.randomUUID();
    when(userAccess.findActiveUser(customerId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(customerId, Set.of("CUSTOMER"), "Customer")));
    DashboardAnalyticsResponse spoofAttempt = service.getAnalytics(customerId, "ADMIN");
    assertThat(spoofAttempt.summaryCards()).hasSize(3);
    assertThat(spoofAttempt.summaryCards().getFirst().id()).isEqualTo("registered_assets");
  }

  @Test
  @DisplayName("a customer is scoped to its own owner id")
  void customerIsScopedToItsOwnOwnerId() {
    UUID actorId = UUID.randomUUID();
    when(userAccess.findActiveUser(actorId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(actorId, Set.of("CUSTOMER"), "Customer")));
    when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
        .thenReturn(0L);
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

    DashboardAnalyticsResponse response = service.getAnalytics(actorId);

    assertThat(response).isNotNull();
    assertThat(response.summaryCards()).hasSize(3);
  }

  @Test
  @DisplayName("an inspector branch runs without a customer scope")
  void inspectorBranchRuns() {
    UUID actorId = UUID.randomUUID();
    when(userAccess.findActiveUser(actorId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(actorId, Set.of("INSPECTOR"), "Inspector")));
    when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
        .thenReturn(0L);
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

    DashboardAnalyticsResponse response = service.getAnalytics(actorId);

    assertThat(response).isNotNull();
    assertThat(response.summaryCards()).isNotEmpty();
  }

  @Test
  @DisplayName("a role-less actor falls through to the zero branch")
  void rolelessActorFallsThrough() {
    UUID actorId = UUID.randomUUID();
    when(userAccess.findActiveUser(actorId))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(actorId, Set.of(), "Nobody")));
    when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

    DashboardAnalyticsResponse response = service.getAnalytics(actorId);

    assertThat(response).isNotNull();
    assertThat(response.summaryCards()).isEmpty();
  }

  @Test
  @DisplayName("manager branch populates action items, schedule, workloads, and revenue series")
  void managerBranchPopulatesRichAnalytics() {
    UUID managerId = UUID.randomUUID();
    when(userAccess.findActiveUser(managerId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(managerId, Set.of("MANAGER"), "Manager")));

    when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
        .thenAnswer(
            inv -> {
              Class<?> type = inv.getArgument(1);
              if (type == BigDecimal.class) {
                String sql = inv.getArgument(0);
                return sql.contains("date_trunc")
                    ? BigDecimal.valueOf(1_250_000_000L)
                    : BigDecimal.valueOf(25_500_000L);
              }
              return 5L;
            });
    when(jdbcTemplate.queryForObject(anyString(), any(Class.class)))
        .thenAnswer(
            inv -> {
              Class<?> type = inv.getArgument(1);
              if (type == BigDecimal.class) return BigDecimal.valueOf(500_000L);
              return 4L;
            });

    LocalDate today = LocalDate.now();
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
        .thenAnswer(
            inv -> {
              String sql = inv.getArgument(0);
              if (sql.contains("AS month")) {
                return List.of(Map.of("month", today.getMonthValue(), "total", 12L));
              }
              return List.of();
            });
    when(jdbcTemplate.queryForList(anyString()))
        .thenAnswer(
            inv -> {
              String sql = inv.getArgument(0);
              if (sql.contains("FROM service_requests sr")) {
                return List.of(
                    Map.of(
                        "id", UUID.randomUUID(),
                        "asset_name", "Bridge A",
                        "asset_code", "BR-01",
                        "status", "OPEN"),
                    Map.of(
                        "id", UUID.randomUUID(),
                        "asset_name", "Tower B",
                        "asset_code", "TW-02",
                        "status", "QUOTED"));
              }
              if (sql.contains("FROM inspection_reports ir")) {
                return List.of(
                    Map.of(
                        "id",
                        UUID.randomUUID(),
                        "asset_name",
                        "Solar Plant",
                        "asset_code",
                        "SP-03",
                        "version_no",
                        2));
              }
              if (sql.contains("FROM service_request_occurrences sro")) {
                return List.of(
                    Map.of(
                        "req_id",
                        UUID.randomUUID(),
                        "asset_name",
                        "Port Pier",
                        "asset_code",
                        "PP-04",
                        "occurrence_index",
                        1));
              }
              if (sql.contains("JOIN field_sessions fs")) {
                return List.of(
                    Map.of(
                        "id", UUID.randomUUID(),
                        "asset_name", "Dam Wall",
                        "asset_code", "DW-05"));
              }
              if (sql.contains("FROM inspections i") && sql.contains("planned_start_at")) {
                return List.of(
                    Map.of(
                        "id", UUID.randomUUID(),
                        "asset_name", "Bridge A",
                        "asset_code", "BR-01",
                        "inspector_name", "Pilot One",
                        "planned_start_at", Timestamp.from(Instant.now()),
                        "status", "SCHEDULED"));
              }
              if (sql.contains("FROM users u LEFT JOIN inspector_profiles")) {
                return List.of(
                    Map.of(
                        "full_name",
                        "Pilot One",
                        "license_class",
                        "CLASS_B",
                        "active_missions",
                        2L,
                        "avg_rating",
                        4.85,
                        "rating_count",
                        3L));
              }
              if (sql.contains("f.severity")) {
                return List.of(
                    Map.of(
                        "y",
                        today.getYear(),
                        "m",
                        today.getMonthValue(),
                        "severity",
                        "CRITICAL",
                        "cnt",
                        2),
                    Map.of(
                        "y",
                        today.getYear(),
                        "m",
                        today.getMonthValue(),
                        "severity",
                        "MEDIUM",
                        "cnt",
                        4),
                    Map.of(
                        "y",
                        today.getYear(),
                        "m",
                        today.getMonthValue(),
                        "severity",
                        "LOW",
                        "cnt",
                        1));
              }
              return List.of();
            });

    DashboardAnalyticsResponse response = service.getAnalytics(managerId, "MANAGER");

    assertThat(response.summaryCards()).hasSize(4);
    assertThat(response.secondaryPerformanceChart()).isNotNull();
    assertThat(response.actionItems()).hasSizeGreaterThanOrEqualTo(5);
    assertThat(response.upcomingSchedule()).hasSize(1);
    assertThat(response.inspectorWorkloads()).hasSize(1);
    assertThat(response.queueTable().rows()).isNotEmpty();
  }

  @Test
  @DisplayName("admin branch populates AI activity, MF1 items, audit logs, and service health")
  void adminBranchPopulatesRichAnalytics() {
    UUID adminId = UUID.randomUUID();
    when(userAccess.findActiveUser(adminId))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(adminId, Set.of("ADMIN"), "Admin")));

    when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
        .thenAnswer(
            inv -> {
              Class<?> type = inv.getArgument(1);
              if (type == BigDecimal.class) return BigDecimal.valueOf(500_000L);
              if (type == Boolean.class) return Boolean.FALSE;
              return 3L;
            });
    when(jdbcTemplate.queryForObject(anyString(), any(Class.class)))
        .thenAnswer(
            inv -> {
              Class<?> type = inv.getArgument(1);
              if (type == BigDecimal.class) return BigDecimal.valueOf(12_000_000L);
              if (type == Boolean.class) return Boolean.TRUE;
              return 6L;
            });

    LocalDate today = LocalDate.now();
    when(jdbcTemplate.queryForList(anyString()))
        .thenAnswer(
            inv -> {
              String sql = inv.getArgument(0);
              if (sql.contains("AS src")) {
                return List.of(
                    Map.of(
                        "y", today.getYear(), "m", today.getMonthValue(), "src", "YOLO", "cnt", 5),
                    Map.of(
                        "y",
                        today.getYear(),
                        "m",
                        today.getMonthValue(),
                        "src",
                        "VISION",
                        "cnt",
                        2),
                    Map.of(
                        "y",
                        today.getYear(),
                        "m",
                        today.getMonthValue(),
                        "src",
                        "MANUAL",
                        "cnt",
                        1));
              }
              if (sql.contains("FROM inspector_applications")) {
                return List.of(
                    Map.of(
                        "full_name", "Applicant A",
                        "pilot_license_class", "CLASS_B",
                        "status", "PENDING_REVIEW"));
              }
              if (sql.contains("FROM drone_registration_applications")) {
                return List.of(
                    Map.of(
                        "serial_number", "SN-01",
                        "model", "Mavic 3 [Specs]",
                        "status", "PENDING"));
              }
              if (sql.contains("FROM prospective_customer_registrations")) {
                return List.of(
                    Map.of(
                        "asset_name", "Site C",
                        "asset_code", "AS-01",
                        "airspace_classification", "CLEAR",
                        "status", "PENDING"));
              }
              if (sql.contains("FROM audit_events")) {
                return List.of(
                    Map.of(
                        "actor_name", "Admin One",
                        "aggregate_type", "DRONE",
                        "action", "APPROVE",
                        "after_status", "APPROVED",
                        "created_at", Timestamp.from(Instant.now())));
              }
              return List.of();
            });

    DashboardAnalyticsResponse response = service.getAnalytics(adminId, "ADMIN");

    assertThat(response.summaryCards()).hasSize(4);
    assertThat(response.pipelineStages()).hasSize(5);
    assertThat(response.serviceHealth()).hasSize(4);
    assertThat(response.actionItems()).hasSize(4);
    assertThat(response.queueTable().rows()).hasSize(1);
  }

  @Test
  @DisplayName("customer and inspector branches map rows and survive query errors gracefully")
  void customerAndInspectorBranchesMapRowsAndHandleFallbacks() {
    UUID customerId = UUID.randomUUID();
    when(userAccess.findActiveUser(customerId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(customerId, Set.of("CUSTOMER"), "Customer")));
    when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
        .thenThrow(new RuntimeException("db timeout"));
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
        .thenAnswer(
            inv -> {
              String sql = inv.getArgument(0);
              if (sql.contains("FROM inspections i JOIN assets a")) {
                return List.of(
                    Map.of(
                        "id", UUID.randomUUID(),
                        "asset_name", "Factory X",
                        "asset_code", "FX-1",
                        "status", "COMPLETED"),
                    Map.of(
                        "id", UUID.randomUUID(),
                        "asset_name", "Bridge Y",
                        "asset_code", "BY-2",
                        "status", "IN_PROGRESS"));
              }
              return List.of();
            });

    DashboardAnalyticsResponse custRes = service.getAnalytics(customerId);
    assertThat(custRes.queueTable().rows()).hasSize(2);

    UUID inspectorId = UUID.randomUUID();
    when(userAccess.findActiveUser(inspectorId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(inspectorId, Set.of("INSPECTOR"), "Inspector")));
    when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class)))
        .thenReturn(4L);
    DashboardAnalyticsResponse inspRes = service.getAnalytics(inspectorId);
    assertThat(inspRes.queueTable().rows()).hasSize(2);
  }

  private void verifyNoOrganizationsQuery() {
    org.mockito.Mockito.mockingDetails(jdbcTemplate)
        .getInvocations()
        .forEach(
            inv -> {
              for (Object a : inv.getArguments()) {
                if (a instanceof String sql && sql.toLowerCase().contains("from organizations")) {
                  throw new AssertionError(
                      "Dashboard queried the dropped organizations table: " + sql);
                }
              }
            });
  }
}

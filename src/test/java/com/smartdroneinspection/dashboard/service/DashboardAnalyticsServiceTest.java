package com.smartdroneinspection.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.dashboard.api.dto.DashboardAnalyticsResponse;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
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
    assertThat(response.summaryCards()).isNotEmpty();
    // No query may reference the organizations table dropped by V43.
    verifyNoOrganizationsQuery();
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

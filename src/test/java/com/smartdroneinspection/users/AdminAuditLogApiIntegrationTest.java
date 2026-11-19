package com.smartdroneinspection.users;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminAuditLogApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;
  private User admin;
  private User manager;
  private UUID recentAggregateId;
  private UUID staleAggregateId;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    jdbcTemplate.update("delete from audit_events");
    admin = saveUser(UserRole.ADMIN, "audit-admin");
    manager = saveUser(UserRole.MANAGER, "audit-manager");
    recentAggregateId = UUID.randomUUID();
    staleAggregateId = UUID.randomUUID();
    insertEvent(
        admin.getId(),
        "INSPECTOR_PROFILE_UPDATED",
        "INSPECTOR_PROFILE",
        recentAggregateId,
        "SUCCESS",
        "trace-recent-001",
        "CURRENT_TIMESTAMP");
    insertEvent(
        manager.getId(),
        "PLATFORM_SETTINGS_UPDATED",
        "PLATFORM_SETTINGS",
        staleAggregateId,
        "DENIED",
        "trace-stale-002",
        "CURRENT_TIMESTAMP - INTERVAL '30 days'");
  }

  @Test
  void adminReadsThePlatformActivityLog() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/audit-logs").with(principal(admin.getId(), "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[*].action", hasItem("INSPECTOR_PROFILE_UPDATED")))
        .andExpect(jsonPath("$.data.items[0].actorEmail").value(admin.getEmail()))
        .andExpect(jsonPath("$.data.availableActions", hasItem("INSPECTOR_PROFILE_UPDATED")))
        .andExpect(jsonPath("$.data.availableActions", hasItem("PLATFORM_SETTINGS_UPDATED")));
  }

  @Test
  void managerCannotReadThePlatformActivityLog() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/audit-logs").with(principal(manager.getId(), "MANAGER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void filtersByExactAction() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/audit-logs")
                .param("action", "PLATFORM_SETTINGS_UPDATED")
                .with(principal(admin.getId(), "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].action").value("PLATFORM_SETTINGS_UPDATED"));
  }

  @Test
  void filtersByCaseInsensitiveQueryAndOutcome() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/audit-logs")
                .param("query", "AUDIT-ADMIN")
                .with(principal(admin.getId(), "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].action").value("INSPECTOR_PROFILE_UPDATED"));

    mockMvc
        .perform(
            get("/api/v1/admin/audit-logs")
                .param("query", staleAggregateId.toString())
                .with(principal(admin.getId(), "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].outcome").value("DENIED"));

    mockMvc
        .perform(
            get("/api/v1/admin/audit-logs")
                .param("outcome", "DENIED")
                .with(principal(admin.getId(), "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].aggregateId").value(staleAggregateId.toString()));
  }

  @Test
  void filtersByTraceIdAndDayWindow() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/audit-logs")
                .param("query", "trace-stale-002")
                .with(principal(admin.getId(), "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].traceId").value("trace-stale-002"));

    mockMvc
        .perform(
            get("/api/v1/admin/audit-logs")
                .param("days", "7")
                .with(principal(admin.getId(), "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].aggregateId").value(recentAggregateId.toString()));

    mockMvc
        .perform(
            get("/api/v1/admin/audit-logs")
                .param("days", "90")
                .with(principal(admin.getId(), "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(2));
  }

  private void insertEvent(
      UUID actorUserId,
      String action,
      String aggregateType,
      UUID aggregateId,
      String outcome,
      String traceId,
      String createdAtExpression) {
    jdbcTemplate.update(
        """
        insert into audit_events
          (actor_user_id, action, aggregate_type, aggregate_id, after_status, trace_id, created_at)
        values (?, ?, ?, ?, ?, ?, %s)
        """
            .formatted(createdAtExpression),
        actorUserId,
        action,
        aggregateType,
        aggregateId,
        outcome,
        traceId);
  }

  private User saveUser(UserRole role, String label) {
    User user =
        new User(
            label + "-" + UUID.randomUUID() + "@example.test",
            label,
            "{argon2}encoded-password",
            UserStatus.ACTIVE);
    user.addRole(role);
    return users.saveAndFlush(user);
  }

  private RequestPostProcessor principal(UUID id, String role) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}

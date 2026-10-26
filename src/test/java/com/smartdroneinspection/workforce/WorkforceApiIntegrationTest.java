package com.smartdroneinspection.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.domain.AuthSession;
import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.ClientType;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.AuthSessionRepository;
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
class WorkforceApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired OrganizationRepository organizations;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired EntityManager entityManager;
  @Autowired AuthSessionRepository sessions;
  @Autowired UserAccess userAccess;

  private MockMvc mockMvc;
  private Organization organizationA;
  private User orgAdmin;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    organizationA = saveOrganization("Alpha");
    grantEntitlement(organizationA.getId());
    orgAdmin =
        saveUser("org-admin", organizationA.getId(), UserRole.ORG_MANAGER, UserStatus.ACTIVE);
  }

  private void grantEntitlement(UUID organizationId) {
    jdbcTemplate.update(
        """
        INSERT INTO subscriptions (organization_id, plan_code, billing_period_months, status, starts_at)
        VALUES (?, 'ENTERPRISE', 1, 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '1 hour')
        """,
        organizationId);
  }

  @Test
  void provisionsInspectorAndMaintenanceEngineerForActorOrganization() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "");
    postProvision("inspector-" + unique + "@example.test", "INSPECTOR", null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.user.roles", hasItem("INSPECTOR")))
        .andExpect(jsonPath("$.data.user.organizationId").value(organizationA.getId().toString()))
        .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty());
    assertThat(users.findByNormalizedEmail("inspector-" + unique + "@example.test")).isPresent();

    postProvision("engineer-" + unique + "@example.test", "MAINTENANCE_ENGINEER", null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.user.roles", hasItem("MAINTENANCE_ENGINEER")))
        .andExpect(jsonPath("$.data.user.organizationId").value(organizationA.getId().toString()));
    assertThat(users.findByNormalizedEmail("engineer-" + unique + "@example.test")).isPresent();
  }

  @Test
  void ignoresRequestOrganizationAndAlwaysUsesActorOrganization() throws Exception {
    Organization organizationB = saveOrganization("Beta");
    String email = "scoped-" + UUID.randomUUID() + "@example.test";

    postProvision(email, "INSPECTOR", organizationB.getId().toString())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.user.organizationId").value(organizationA.getId().toString()));

    User created = users.findByNormalizedEmail(User.normalizeEmail(email)).orElseThrow();
    assertThat(created.getOrganizationId()).isEqualTo(organizationA.getId());
  }

  @Test
  void rejectsPlatformAndOrganizationAdministratorRolesWithoutCreatingRows() throws Exception {
    assertRejectedWithoutRows("ADMIN");
    assertRejectedWithoutRows("ORG_MANAGER");
  }

  @Test
  void doesNotRevealThatAnEmailBelongsToAnotherOrganization() throws Exception {
    Organization organizationB = saveOrganization("Beta");
    User foreignUser =
        saveUser(
            "foreign-email-" + UUID.randomUUID(),
            organizationB.getId(),
            UserRole.INSPECTOR,
            UserStatus.ACTIVE);

    // A 409 here would tell the actor the email is already registered somewhere in the
    // platform. A 404 with the same body as a genuinely missing user reveals nothing.
    postProvision(foreignUser.getEmail(), "INSPECTOR", null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value("User was not found."));
  }

  @Test
  void returnsConflictWhenEmailIsAlreadyUsedWithinTheActorOrganization() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "");
    String email = "dup-" + unique + "@example.test";

    postProvision(email, "INSPECTOR", null).andExpect(status().isCreated());

    // Same-organization duplication is not an oracle: the actor already knows their own
    // users. A clear 409 is more useful than a silent 404 here.
    postProvision(email, "INSPECTOR", null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
  }

  @Test
  void returnsNonDisclosingNotFoundForForeignOrganizationUser() throws Exception {
    Organization organizationB = saveOrganization("Foreign");
    User foreignUser =
        saveUser("foreign-inspector", organizationB.getId(), UserRole.INSPECTOR, UserStatus.ACTIVE);
    long[] counts = userAndRoleCounts();

    mockMvc
        .perform(
            patch("/api/v1/workforce/users/{userId}/status", foreignUser.getId())
                .with(orgAdmin(orgAdmin.getId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}"))
        .andExpect(status().isNotFound());

    assertCountsUnchanged(counts);
    assertThat(users.findDetailedById(foreignUser.getId()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  void rejectsSuspendedOrganizationAdministratorWithoutCreatingRows() throws Exception {
    orgAdmin.disable(UserStatus.SUSPENDED);
    users.saveAndFlush(orgAdmin);
    long[] counts = userAndRoleCounts();

    mockMvc
        .perform(
            post("/api/v1/workforce/users")
                .with(orgAdmin(orgAdmin.getId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"blocked-%s@example.test","fullName":"Blocked","roles":["INSPECTOR"]}
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isForbidden());

    assertCountsUnchanged(counts);
  }

  @Test
  void listReturnsOnlyActorOrganizationWorkforceUsers() throws Exception {
    Organization organizationB = saveOrganization("Other");
    saveUser("local-inspector", organizationA.getId(), UserRole.INSPECTOR, UserStatus.ACTIVE);
    saveUser("foreign-inspector", organizationB.getId(), UserRole.INSPECTOR, UserStatus.ACTIVE);

    String body =
        mockMvc
            .perform(get("/api/v1/workforce/users").with(orgAdmin(orgAdmin.getId())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // The same-tenant inspector is listed; the other tenant's inspector never appears in the
    // payload.
    assertThat(body).contains("local-inspector@example.test");
    assertThat(body).doesNotContain("foreign-inspector@example.test");
  }

  @Test
  void usersFacadeRejectsInspectorProvisionWithoutAnyLifecycleRows() {
    User inspector =
        saveUser("facade-inspector", organizationA.getId(), UserRole.INSPECTOR, UserStatus.ACTIVE);
    long[] counts = lifecycleCounts();

    assertThatThrownBy(
            () ->
                userAccess.provisionWorkforceUser(
                    inspector.getId(),
                    organizationA.getId(),
                    "forbidden-" + UUID.randomUUID() + "@example.test",
                    "Forbidden User",
                    java.util.Set.of("INSPECTOR"),
                    "127.0.0.1",
                    "JUnit",
                    "facade-test"))
        .isInstanceOf(AuthException.class)
        .satisfies(
            failure ->
                assertThat(((AuthException) failure).status())
                    .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));

    assertLifecycleCountsUnchanged(counts);
  }

  @Test
  void usersFacadeRejectsProvisioningIntoAnotherOrganization() {
    Organization organizationB = saveOrganization("Facade Other");
    long[] counts = lifecycleCounts();

    assertThatThrownBy(
            () ->
                userAccess.provisionWorkforceUser(
                    orgAdmin.getId(),
                    organizationB.getId(),
                    "foreign-scope-" + UUID.randomUUID() + "@example.test",
                    "Foreign Scope",
                    java.util.Set.of("INSPECTOR"),
                    "127.0.0.1",
                    "JUnit",
                    "facade-test"))
        .isInstanceOf(AuthException.class)
        .satisfies(
            failure ->
                assertThat(((AuthException) failure).status())
                    .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));

    assertLifecycleCountsUnchanged(counts);
  }

  @Test
  void usersFacadeCannotProvisionOrganizationAdministratorDirectly() {
    long[] counts = lifecycleCounts();

    assertThatThrownBy(
            () ->
                userAccess.provisionWorkforceUser(
                    orgAdmin.getId(),
                    organizationA.getId(),
                    "facade-org-admin-" + UUID.randomUUID() + "@example.test",
                    "Escalated Admin",
                    java.util.Set.of("ORG_MANAGER"),
                    "127.0.0.1",
                    "JUnit",
                    "facade-test"))
        .isInstanceOf(AuthException.class)
        .satisfies(
            failure ->
                assertThat(((AuthException) failure).status())
                    .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));

    assertLifecycleCountsUnchanged(counts);
  }

  @Test
  void usersFacadeCannotProvisionPlatformAdministratorDirectly() {
    long[] counts = lifecycleCounts();

    assertThatThrownBy(
            () ->
                userAccess.provisionWorkforceUser(
                    orgAdmin.getId(),
                    organizationA.getId(),
                    "facade-platform-admin-" + UUID.randomUUID() + "@example.test",
                    "Escalated Platform Admin",
                    java.util.Set.of("ADMIN"),
                    "127.0.0.1",
                    "JUnit",
                    "facade-test"))
        .isInstanceOf(AuthException.class)
        .satisfies(
            failure ->
                assertThat(((AuthException) failure).status())
                    .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));

    assertLifecycleCountsUnchanged(counts);
  }

  @Test
  void usersFacadeCannotPromoteWorkforceMemberToOrganizationAdministratorDirectly() {
    User inspector =
        saveUser("facade-promote", organizationA.getId(), UserRole.INSPECTOR, UserStatus.ACTIVE);
    long[] counts = lifecycleCounts();
    var beforeRoles = users.findDetailedById(inspector.getId()).orElseThrow().roleValues();

    assertThatThrownBy(
            () ->
                userAccess.updateWorkforceRoles(
                    orgAdmin.getId(),
                    inspector.getId(),
                    java.util.Set.of("ORG_MANAGER"),
                    "127.0.0.1",
                    "JUnit",
                    "facade-test"))
        .isInstanceOf(AuthException.class)
        .satisfies(
            failure ->
                assertThat(((AuthException) failure).status())
                    .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));

    assertLifecycleCountsUnchanged(counts);
    assertThat(users.findDetailedById(inspector.getId()).orElseThrow().roleValues())
        .containsExactlyElementsOf(beforeRoles);
  }

  @Test
  void usersFacadeCannotChangePeerOrganizationAdministratorStatus() {
    User peerAdmin =
        saveUser(
            "facade-peer-admin-status",
            organizationA.getId(),
            UserRole.ORG_MANAGER,
            UserStatus.ACTIVE);
    UUID sessionId = insertSession(peerAdmin.getId());
    long[] counts = lifecycleCounts();

    assertThatThrownBy(
            () ->
                userAccess.updateWorkforceStatus(
                    orgAdmin.getId(),
                    peerAdmin.getId(),
                    "SUSPENDED",
                    "127.0.0.1",
                    "JUnit",
                    "facade-test"))
        .isInstanceOf(AuthException.class)
        .satisfies(
            failure ->
                assertThat(((AuthException) failure).status())
                    .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));

    assertLifecycleCountsUnchanged(counts);
    assertThat(users.findDetailedById(peerAdmin.getId()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.ACTIVE);
    assertThat(revokedAt(sessionId)).isNull();
  }

  @Test
  void usersFacadeCannotChangePeerOrganizationAdministratorRoles() {
    User peerAdmin =
        saveUser(
            "facade-peer-admin-roles",
            organizationA.getId(),
            UserRole.ORG_MANAGER,
            UserStatus.ACTIVE);
    var beforeRoles = users.findDetailedById(peerAdmin.getId()).orElseThrow().roleValues();
    UUID sessionId = insertSession(peerAdmin.getId());
    long[] counts = lifecycleCounts();

    assertThatThrownBy(
            () ->
                userAccess.updateWorkforceRoles(
                    orgAdmin.getId(),
                    peerAdmin.getId(),
                    java.util.Set.of("INSPECTOR"),
                    "127.0.0.1",
                    "JUnit",
                    "facade-test"))
        .isInstanceOf(AuthException.class)
        .satisfies(
            failure ->
                assertThat(((AuthException) failure).status())
                    .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));

    assertLifecycleCountsUnchanged(counts);
    assertThat(users.findDetailedById(peerAdmin.getId()).orElseThrow().roleValues())
        .containsExactlyElementsOf(beforeRoles);
    assertThat(revokedAt(sessionId)).isNull();
  }

  @Test
  void usersFacadeRejectsInspectorStatusChangeWithoutLifecycleChanges() {
    User inspector =
        saveUser("facade-inspector", organizationA.getId(), UserRole.INSPECTOR, UserStatus.ACTIVE);
    User target =
        saveUser(
            "facade-target",
            organizationA.getId(),
            UserRole.MAINTENANCE_ENGINEER,
            UserStatus.ACTIVE);
    UUID sessionId = insertSession(target.getId());
    long[] counts = lifecycleCounts();

    assertThatThrownBy(
            () ->
                userAccess.updateWorkforceStatus(
                    inspector.getId(),
                    target.getId(),
                    "SUSPENDED",
                    "127.0.0.1",
                    "JUnit",
                    "facade-test"))
        .isInstanceOf(AuthException.class)
        .satisfies(
            failure ->
                assertThat(((AuthException) failure).status())
                    .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));

    assertLifecycleCountsUnchanged(counts);
    assertThat(users.findDetailedById(target.getId()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.ACTIVE);
    assertThat(revokedAt(sessionId)).isNull();
    assertThat(revokedReason(sessionId)).isNull();
  }

  @Test
  void usersFacadeRejectsInspectorRoleChangeWithoutLifecycleChanges() {
    User inspector =
        saveUser("facade-inspector", organizationA.getId(), UserRole.INSPECTOR, UserStatus.ACTIVE);
    User target =
        saveUser(
            "facade-target",
            organizationA.getId(),
            UserRole.MAINTENANCE_ENGINEER,
            UserStatus.ACTIVE);
    UUID sessionId = insertSession(target.getId());
    long[] counts = lifecycleCounts();

    assertThatThrownBy(
            () ->
                userAccess.updateWorkforceRoles(
                    inspector.getId(),
                    target.getId(),
                    java.util.Set.of("INSPECTOR"),
                    "127.0.0.1",
                    "JUnit",
                    "facade-test"))
        .isInstanceOf(AuthException.class)
        .satisfies(
            failure ->
                assertThat(((AuthException) failure).status())
                    .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));

    assertLifecycleCountsUnchanged(counts);
    assertThat(users.findDetailedById(target.getId()).orElseThrow().roleValues())
        .containsExactly("MAINTENANCE_ENGINEER");
    assertThat(revokedAt(sessionId)).isNull();
    assertThat(revokedReason(sessionId)).isNull();
  }

  @Test
  void statusChangeRecordsAuditAttributionAndRevokesSessions() throws Exception {
    User inspector =
        saveUser("audited-inspector", organizationA.getId(), UserRole.INSPECTOR, UserStatus.ACTIVE);
    UUID sessionId = insertSession(inspector.getId());
    assertThat(revokedReason(sessionId)).isNull();

    mockMvc
        .perform(
            patch("/api/v1/workforce/users/{userId}/status", inspector.getId())
                .with(orgAdmin(orgAdmin.getId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

    entityManager.flush();
    assertThat(revokedAt(sessionId)).isNotNull();
    assertThat(revokedReason(sessionId)).isEqualTo("account-status-changed");
    assertThat(refreshRevokedAt(sessionId)).isNotNull();
    assertThat(refreshRevokeReason(sessionId)).isEqualTo("account-status-changed");

    var audit =
        jdbcTemplate.queryForList(
            """
            SELECT actor_user_id, aggregate_id, action, after_status, organization_id
            FROM audit_events
            WHERE action = 'USER_STATUS_CHANGED' AND aggregate_id = ?
            """,
            inspector.getId());
    assertThat(audit).hasSize(1);
    assertThat(audit.getFirst().get("actor_user_id")).isEqualTo(orgAdmin.getId());
    assertThat(audit.getFirst().get("after_status")).isEqualTo("SUCCESS");
    assertThat(audit.getFirst().get("organization_id")).isEqualTo(organizationA.getId());
  }

  @Test
  void rejectsAdministratorRoleEscalationWithoutChangingRolesOrRows() throws Exception {
    User inspector =
        saveUser("role-escalation", organizationA.getId(), UserRole.INSPECTOR, UserStatus.ACTIVE);
    long[] counts = lifecycleCounts();
    var beforeRoles = users.findDetailedById(inspector.getId()).orElseThrow().roleValues();

    mockMvc
        .perform(
            put("/api/v1/workforce/users/{userId}/roles", inspector.getId())
                .with(orgAdmin(orgAdmin.getId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"ORG_MANAGER\"]}"))
        .andExpect(status().isBadRequest());

    assertLifecycleCountsUnchanged(counts);
    assertThat(users.findDetailedById(inspector.getId()).orElseThrow().roleValues())
        .containsExactlyElementsOf(beforeRoles);
  }

  @Test
  void rejectsPlatformAdministratorRoleEscalationWithoutChangingRolesOrRows() throws Exception {
    User inspector =
        saveUser(
            "platform-role-escalation",
            organizationA.getId(),
            UserRole.INSPECTOR,
            UserStatus.ACTIVE);
    long[] counts = lifecycleCounts();
    var beforeRoles = users.findDetailedById(inspector.getId()).orElseThrow().roleValues();

    mockMvc
        .perform(
            put("/api/v1/workforce/users/{userId}/roles", inspector.getId())
                .with(orgAdmin(orgAdmin.getId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"ADMIN\"]}"))
        .andExpect(status().isBadRequest());

    assertLifecycleCountsUnchanged(counts);
    assertThat(users.findDetailedById(inspector.getId()).orElseThrow().roleValues())
        .containsExactlyElementsOf(beforeRoles);
  }

  @Test
  void roleChangeRevokesSessionsAndKeepsSubjectOrganization() throws Exception {
    User inspector =
        saveUser(
            "reassigned-inspector", organizationA.getId(), UserRole.INSPECTOR, UserStatus.ACTIVE);
    UUID sessionId = insertSession(inspector.getId());

    mockMvc
        .perform(
            put("/api/v1/workforce/users/{userId}/roles", inspector.getId())
                .with(orgAdmin(orgAdmin.getId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"MAINTENANCE_ENGINEER\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.roles", hasItem("MAINTENANCE_ENGINEER")))
        .andExpect(jsonPath("$.data.organizationId").value(organizationA.getId().toString()));

    entityManager.flush();
    assertThat(revokedAt(sessionId)).isNotNull();
    assertThat(revokedReason(sessionId)).isEqualTo("roles-changed");
    assertThat(refreshRevokedAt(sessionId)).isNotNull();
    assertThat(refreshRevokeReason(sessionId)).isEqualTo("roles-changed");

    User reloaded = users.findDetailedById(inspector.getId()).orElseThrow();
    assertThat(reloaded.getOrganizationId()).isEqualTo(organizationA.getId());
    assertThat(reloaded.getActorZone()).isEqualTo(ActorZone.CUSTOMER_ORGANIZATION);
    assertThat(reloaded.roleValues()).containsExactly("MAINTENANCE_ENGINEER");

    Integer auditRows =
        jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM audit_events
            WHERE action = 'USER_ROLES_CHANGED' AND aggregate_id = ? AND actor_user_id = ?
            """,
            Integer.class,
            inspector.getId(),
            orgAdmin.getId());
    assertThat(auditRows).isEqualTo(1);
  }

  @Test
  void cannotSuspendSameOrganizationAdministrator() throws Exception {
    User peerAdmin =
        saveUser("peer-admin", organizationA.getId(), UserRole.ORG_MANAGER, UserStatus.ACTIVE);

    mockMvc
        .perform(
            patch("/api/v1/workforce/users/{userId}/status", peerAdmin.getId())
                .with(orgAdmin(orgAdmin.getId()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}"))
        .andExpect(status().isNotFound());

    assertThat(users.findDetailedById(peerAdmin.getId()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.ACTIVE);
  }

  private UUID insertSession(UUID userId) {
    User user = users.findDetailedById(userId).orElseThrow();
    AuthSession session =
        sessions.saveAndFlush(
            new AuthSession(
                user,
                ClientType.WEB,
                UUID.randomUUID().toString().replace("-", "").repeat(2),
                java.time.Instant.now(),
                java.time.Instant.now().plusSeconds(3600)));
    return session.getId();
  }

  private java.sql.Timestamp revokedAt(UUID sessionId) {
    return jdbcTemplate.queryForObject(
        "SELECT revoked_at FROM auth_sessions WHERE id = ?", java.sql.Timestamp.class, sessionId);
  }

  private String revokedReason(UUID sessionId) {
    return jdbcTemplate.queryForObject(
        "SELECT revoked_reason FROM auth_sessions WHERE id = ?", String.class, sessionId);
  }

  private java.sql.Timestamp refreshRevokedAt(UUID sessionId) {
    return revokedAt(sessionId);
  }

  private String refreshRevokeReason(UUID sessionId) {
    return revokedReason(sessionId);
  }

  private org.springframework.test.web.servlet.ResultActions postProvision(
      String email, String role, String requestOrganizationId) throws Exception {
    String organizationField =
        requestOrganizationId == null
            ? ""
            : ",\"organizationId\":\"" + requestOrganizationId + "\"";
    return mockMvc.perform(
        post("/api/v1/workforce/users")
            .with(orgAdmin(orgAdmin.getId()))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"email\":\"%s\",\"fullName\":\"Provisioned User\",\"roles\":[\"%s\"]%s}"
                    .formatted(email, role, organizationField)));
  }

  private void assertRejectedWithoutRows(String role) throws Exception {
    long[] counts = userAndRoleCounts();
    postProvision(
            "rejected-" + role.toLowerCase() + "-" + UUID.randomUUID() + "@example.test",
            role,
            null)
        .andExpect(status().isBadRequest());
    assertCountsUnchanged(counts);
  }

  private long[] userAndRoleCounts() {
    return new long[] {jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class)};
  }

  private long[] lifecycleCounts() {
    return new long[] {
      jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class),
      jdbcTemplate.queryForObject("SELECT count(*) FROM audit_events", Long.class),
      jdbcTemplate.queryForObject("SELECT count(*) FROM auth_sessions", Long.class)
    };
  }

  private void assertLifecycleCountsUnchanged(long[] counts) {
    assertThat(lifecycleCounts()).containsExactly(counts);
  }

  private void assertCountsUnchanged(long[] counts) {
    assertThat(userAndRoleCounts()).containsExactly(counts);
  }

  private Organization saveOrganization(String name) {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    return organizations.saveAndFlush(new Organization(name, name + "-" + suffix, "Test fixture"));
  }

  private User saveUser(String prefix, UUID organizationId, UserRole role, UserStatus userStatus) {
    User user =
        new User(
            prefix + "@example.test",
            prefix,
            "{argon2}fixture-hash",
            userStatus,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    user.addRole(role);
    return users.saveAndFlush(user);
  }

  private RequestPostProcessor orgAdmin(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_ORG_MANAGER"));
  }
}

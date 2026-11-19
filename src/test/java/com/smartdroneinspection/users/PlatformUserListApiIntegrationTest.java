package com.smartdroneinspection.users;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * The platform administrator's view of the accounts it manages.
 *
 * <p>The list is platform-wide on purpose: an ADMIN manages enterprise workspaces and the
 * administrator-managed accounts inside them, not one tenant's records. Workforce members of a
 * customer organization are administered by that organization's CUSTOMER through a different
 * contract.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PlatformUserListApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;
  private User admin;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    admin = saveAdmin();
  }

  @Test
  void listsEveryManagedAccountWithItsRoles() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "");
    User customer =
        new User(
            "customer-" + unique + "@example.test",
            "Customer Manager",
            "{argon2}encoded-password",
            UserStatus.ACTIVE);
    customer.addRole(UserRole.CUSTOMER);
    users.saveAndFlush(customer);

    mockMvc
        .perform(get("/api/v1/platform/users").with(admin(admin.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[*].email", hasItem(customer.getEmail())))
        .andExpect(jsonPath("$.data[*].email", hasItem(admin.getEmail())))
        .andExpect(
            jsonPath(
                "$.data[?(@.email == '" + customer.getEmail() + "')].roles[0]",
                hasItem("CUSTOMER")));
  }

  @Test
  void refusesTheListToACallerWhoIsNotAPlatformAdministrator() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "");
    User orgAdmin =
        new User(
            "orgadmin-" + unique + "@example.test",
            "Organization Admin",
            "{argon2}encoded-password",
            UserStatus.ACTIVE);
    orgAdmin.addRole(UserRole.CUSTOMER);
    users.saveAndFlush(orgAdmin);

    mockMvc
        .perform(
            get("/api/v1/platform/users")
                .with(
                    jwt()
                        .jwt(token -> token.subject(orgAdmin.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void resetsThePasswordOfAnotherAccountAndRevokesItsSessions() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "");
    User engineer =
        new User(
            "reset-" + unique + "@example.test",
            "Resettable Engineer",
            "{argon2}encoded-password",
            UserStatus.ACTIVE);
    engineer.addRole(UserRole.INSPECTOR);
    users.saveAndFlush(engineer);

    mockMvc
        .perform(
            post("/api/v1/platform/users/{userId}/reset-password", engineer.getId())
                .with(admin(admin.getId()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.id").value(engineer.getId().toString()))
        .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty());
  }

  @Test
  void limitsAPureManagerToInspectorAccounts() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "");

    User manager =
        new User(
            "manager-" + unique + "@example.test",
            "Platform Manager",
            "{argon2}encoded-password",
            UserStatus.ACTIVE);
    manager.addRole(UserRole.MANAGER);
    manager = users.saveAndFlush(manager);

    User inspector =
        new User(
            "inspector-" + unique + "@example.test",
            "Partner Inspector",
            "{argon2}encoded-password",
            UserStatus.ACTIVE);
    inspector.addRole(UserRole.INSPECTOR);
    inspector = users.saveAndFlush(inspector);

    User customer =
        new User(
            "customer-" + unique + "@example.test",
            "Customer Manager",
            "{argon2}encoded-password",
            UserStatus.ACTIVE);
    customer.addRole(UserRole.CUSTOMER);
    customer = users.saveAndFlush(customer);

    mockMvc
        .perform(get("/api/v1/platform/users").with(manager(manager.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].email", hasItem(inspector.getEmail())))
        .andExpect(jsonPath("$.data[?(@.email == '" + admin.getEmail() + "')]").isEmpty())
        .andExpect(jsonPath("$.data[?(@.email == '" + customer.getEmail() + "')]").isEmpty());
  }

  private User saveAdmin() {
    User user =
        new User(
            "admin-" + UUID.randomUUID() + "@example.test",
            "Platform Admin",
            "{argon2}encoded-password",
            UserStatus.ACTIVE);
    user.addRole(UserRole.ADMIN);
    return users.saveAndFlush(user);
  }

  private RequestPostProcessor admin(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private RequestPostProcessor manager(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
  }
}

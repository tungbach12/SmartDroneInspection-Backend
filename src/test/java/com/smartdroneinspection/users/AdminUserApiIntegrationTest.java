package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
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
class AdminUserApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired OrganizationRepository organizations;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @Test
  void createPersistsUserAndAuditEventInOneTransaction() throws Exception {
    User admin = saveAdmin();
    String unique = UUID.randomUUID().toString().replace("-", "");
    Organization organization =
        organizations.saveAndFlush(
            new Organization("Engineer organization", "ENG-" + unique, "Test fixture"));
    String body =
        """
        {
          "email": "engineer-%s@example.test",
          "fullName": "Maintenance Engineer",
          "actorZone": "CUSTOMER_ORGANIZATION",
          "organizationId": "%s",
          "roles": ["MAINTENANCE_ENGINEER"]
        }
        """
            .formatted(unique, organization.getId());

    var result =
        mockMvc
            .perform(
                post("/api/v1/platform/users")
                    .with(admin(admin.getId()))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.user.roles", hasItem("MAINTENANCE_ENGINEER")))
            .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty())
            .andReturn();

    String email = "engineer-" + unique + "@example.test";
    User created = users.findByNormalizedEmail(User.normalizeEmail(email)).orElseThrow();
    assertThat(created.roleValues()).containsExactly("MAINTENANCE_ENGINEER");

    Integer auditRows =
        jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM audit_events
            WHERE actor_user_id = ? AND aggregate_id = ?
              AND action = 'USER_CREATED' AND after_status = 'SUCCESS'
            """,
            Integer.class,
            admin.getId(),
            created.getId());
    assertThat(auditRows).isEqualTo(1);
    assertThat(result.getResponse().getContentAsString()).isNotBlank();
  }

  private User saveAdmin() {
    User admin =
        new User(
            "admin-" + UUID.randomUUID() + "@example.test",
            "Platform Admin",
            "{argon2}encoded-password",
            UserStatus.ACTIVE,
            ActorZone.PLATFORM,
            null);
    admin.addRole(UserRole.ADMIN);
    return users.saveAndFlush(admin);
  }

  private RequestPostProcessor admin(UUID id) {
    return jwt()
        .jwt(token -> token.subject(id.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }
}

package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.User;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class OrganizationRegistrationApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired OrganizationRepository organizations;
  @Autowired JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
  }

  @Test
  void registrationCreatesActiveOrganizationAndItsFirstOrgAdminAtomically() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "");
    String email = "org-admin-" + unique + "@example.test";
    String code = "ORG-" + unique.substring(0, 16);
    String body =
        """
        {
          "email": "%s",
          "fullName": "Organization Admin",
          "organizationName": "Infrastructure Company",
          "organizationCode": "%s",
          "password": "a sufficiently long passphrase"
        }
        """
            .formatted(email, code);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.organizationCode").value(code.toUpperCase()))
        .andExpect(jsonPath("$.data.user.roles", hasItem("ORG_ADMIN")))
        .andExpect(jsonPath("$.data.user.actorZone").value("CUSTOMER_ORGANIZATION"));

    User user = users.findByNormalizedEmail(User.normalizeEmail(email)).orElseThrow();
    assertThat(user.roleValues()).containsExactly("ORG_ADMIN");
    assertThat(user.getOrganizationId()).isNotNull();

    Organization organization = organizations.findById(user.getOrganizationId()).orElseThrow();
    assertThat(organization.getStatus()).isEqualTo("ACTIVE");
    // database-design.md §6.1: the organization records the ORG_ADMIN who created it.
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT created_by_user_id FROM organizations WHERE id = ?",
                UUID.class,
                organization.getId()))
        .isEqualTo(user.getId());

    Integer auditRows =
        jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM audit_events
            WHERE aggregate_id = ? AND action = 'ORGANIZATION_REGISTRATION' AND after_status = 'SUCCESS'
            """,
            Integer.class,
            user.getId());
    assertThat(auditRows).isEqualTo(1);
  }
}

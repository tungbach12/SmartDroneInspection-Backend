package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
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
class ClientRegistrationApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;
  @Autowired OrganizationRepository organizations;
  @Autowired JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @Test
  void registerPersistsOrganizationClientAndAuditEventInOneTransaction() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "");
    String email = "rep-" + unique + "@acme.example";
    String organizationCode = "ACME-" + unique.substring(0, 12);
    String body =
        """
        {
          "email": "%s",
          "fullName": "Company Representative",
          "organizationName": "Acme Infrastructure",
          "organizationCode": "%s",
          "password": "a sufficiently long passphrase"
        }
        """
            .formatted(email, organizationCode);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.organizationCode").value(organizationCode.toUpperCase()))
        .andExpect(jsonPath("$.data.user.roles", hasItem("CLIENT")));

    User user = users.findByNormalizedEmail(User.normalizeEmail(email)).orElseThrow();
    assertThat(organizations.findByCodeIgnoreCase(organizationCode)).isPresent();
    assertThat(user.roleValues()).containsExactly("CLIENT");

    Integer auditRows =
        jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM security_audit_events
            WHERE subject_user_id = ? AND event_type = 'CLIENT_REGISTRATION' AND outcome = 'SUCCESS'
            """,
            Integer.class,
            user.getId());
    assertThat(auditRows).isEqualTo(1);
  }
}

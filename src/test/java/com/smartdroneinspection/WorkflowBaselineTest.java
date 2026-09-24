package com.smartdroneinspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WorkflowBaselineTest {

  private static final String PASSWORD_PREFIX = "W3-generated-fixture-";

  @Autowired WebApplicationContext webApplicationContext;
  private MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired OrganizationRepository organizations;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwords;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired Flyway flyway;

  private final Map<UserRole, FixtureCredentials> credentials = new EnumMap<>(UserRole.class);

  @BeforeEach
  void seedRoleFixtures() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    Organization organization =
        organizations.saveAndFlush(
            new Organization("W3 baseline customer", "W3-" + UUID.randomUUID(), "Test fixture"));

    for (UserRole role : UserRole.values()) {
      String email = "w3-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.test";
      String password = PASSWORD_PREFIX + UUID.randomUUID();
      ActorZone actorZone =
          role == UserRole.CLIENT ? ActorZone.CUSTOMER_ORGANIZATION : zoneFor(role);
      UUID organizationId = role == UserRole.CLIENT ? organization.getId() : null;
      User user =
          new User(
              email,
              "W3 " + role.name(),
              passwords.encode(password),
              UserStatus.ACTIVE,
              actorZone,
              organizationId);
      user.addRole(role);
      users.saveAndFlush(user);
      credentials.put(role, new FixtureCredentials(email, password));
    }
  }

  @Test
  void cleanBaselineAppliesMigrations() {
    List<String> versions =
        jdbcTemplate.query(
            "SELECT version FROM flyway_schema_history ORDER BY installed_rank",
            (resultSet, rowNumber) -> resultSet.getString(1));

    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("10");
    assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
  }

  @Test
  void allRoleFixturesAuthenticateWithGeneratedCredentials() throws Exception {
    for (UserRole role : UserRole.values()) {
      FixtureCredentials fixture = credentials.get(role);
      String body =
          objectMapper.writeValueAsString(
              Map.of("email", fixture.email(), "password", fixture.password()));

      String responseBody =
          mockMvc
              .perform(
                  post("/api/v1/mobile/auth/login")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(body))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      JsonNode response = objectMapper.readTree(responseBody);
      JsonNode data = response.path("data");
      assertThat(response.path("success").asBoolean()).isTrue();
      assertThat(response.path("message").asText()).isEqualTo("Success");
      assertThat(data.path("step").asText()).isEqualTo("AUTHENTICATED");
      assertThat(data.path("accessToken").asText()).isNotBlank();
      assertThat(data.path("refreshToken").asText()).isNotBlank();
      assertThat(data.path("user").path("roles").valueStream().map(JsonNode::asText))
          .contains(role.value());
    }
  }

  @Test
  void mobileLogoutRemainsBodylessAfterAuthSuccessEnvelope() throws Exception {
    FixtureCredentials fixture = credentials.get(UserRole.INSPECTOR);
    String loginBody =
        objectMapper.writeValueAsString(
            Map.of("email", fixture.email(), "password", fixture.password()));
    String loginResponse =
        mockMvc
            .perform(
                post("/api/v1/mobile/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String refreshToken =
        objectMapper.readTree(loginResponse).path("data").path("refreshToken").asText();

    mockMvc
        .perform(
            post("/api/v1/mobile/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
  }

  private ActorZone zoneFor(UserRole role) {
    return role == UserRole.ADMIN ? ActorZone.PLATFORM : ActorZone.SERVICE_WORKFORCE;
  }

  private record FixtureCredentials(String email, String password) {}
}

package com.smartdroneinspection.users;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserProfileApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired UserRepository users;

  private MockMvc mockMvc;
  private User customer;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    customer =
        new User(
            "profile-" + UUID.randomUUID() + "@example.test",
            "Customer Before",
            "+84 900 000 000",
            null,
            "{argon2}encoded-password",
            UserStatus.ACTIVE);
    customer.addRole(UserRole.CUSTOMER);
    customer = users.saveAndFlush(customer);
  }

  @Test
  void updatesOnlyTheAuthenticatedProfileAndPersistsTheValues() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .with(jwt().jwt(token -> token.subject(customer.getId().toString())))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"fullName\":\"  Customer After  \",\"phoneNumber\":\"  +84 901 234 567  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fullName").value("Customer After"))
        .andExpect(jsonPath("$.data.phoneNumber").value("+84 901 234 567"))
        .andExpect(jsonPath("$.data.email").value(customer.getEmail()));

    mockMvc
        .perform(
            get("/api/v1/users/me")
                .with(jwt().jwt(token -> token.subject(customer.getId().toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fullName").value("Customer After"))
        .andExpect(jsonPath("$.data.phoneNumber").value("+84 901 234 567"));

    User persisted = users.findDetailedById(customer.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(persisted.getFullName()).isEqualTo("Customer After");
    org.assertj.core.api.Assertions.assertThat(persisted.getPhoneNumber())
        .isEqualTo("+84 901 234 567");
  }

  @Test
  void rejectsInvalidProfileFieldsWithoutChangingTheAccount() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .with(jwt().jwt(token -> token.subject(customer.getId().toString())))
                .with(csrf())
                .contentType("application/json")
                .content("{\"fullName\":\"   \",\"phoneNumber\":\"0900\"}"))
        .andExpect(status().isBadRequest());

    User persisted = users.findDetailedById(customer.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(persisted.getFullName())
        .isEqualTo("Customer Before");
    org.assertj.core.api.Assertions.assertThat(persisted.getPhoneNumber())
        .isEqualTo("+84 900 000 000");
  }

  @Test
  void rejectsOverlongProfileFieldsWithoutChangingTheAccount() throws Exception {
    String tooLongName = "N".repeat(201);
    String tooLongPhone = "1".repeat(33);

    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .with(jwt().jwt(token -> token.subject(customer.getId().toString())))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"fullName\":\""
                        + tooLongName
                        + "\",\"phoneNumber\":\""
                        + tooLongPhone
                        + "\"}"))
        .andExpect(status().isBadRequest());

    User persisted = users.findDetailedById(customer.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(persisted.getFullName())
        .isEqualTo("Customer Before");
    org.assertj.core.api.Assertions.assertThat(persisted.getPhoneNumber())
        .isEqualTo("+84 900 000 000");
  }

  @Test
  void rejectsProfileUpdatesWithoutAuthentication() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .with(csrf())
                .contentType("application/json")
                .content("{\"fullName\":\"Should Not Persist\",\"phoneNumber\":\"0900\"}"))
        .andExpect(status().isUnauthorized());

    User persisted = users.findDetailedById(customer.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(persisted.getFullName())
        .isEqualTo("Customer Before");
  }
}

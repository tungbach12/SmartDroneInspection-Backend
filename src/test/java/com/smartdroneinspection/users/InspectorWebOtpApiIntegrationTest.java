package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "smartdroneinspection.auth.otp.enabled=true",
      "smartdroneinspection.auth.otp.challenge-pepper=integration-test-only-pepper",
      "smartdroneinspection.auth.otp.smtp-host=localhost",
      "smartdroneinspection.auth.otp.email-from=otp@example.test",
      "smartdroneinspection.auth.otp.sms-account-sid=AC-test",
      "smartdroneinspection.auth.otp.sms-auth-token=test-token",
      "smartdroneinspection.auth.otp.sms-from-number=+14155550000"
    })
@Import(TestcontainersConfiguration.class)
class InspectorWebOtpApiIntegrationTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwords;
  @MockitoBean com.smartdroneinspection.users.service.LoginOtpDelivery otpDelivery;
  private MockMvc mockMvc;
  private String email;
  private String password;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    email = "otp-inspector-" + UUID.randomUUID() + "@example.test";
    password = "OTP-test-password-" + UUID.randomUUID();
    User inspector =
        new User(
            email,
            "OTP Inspector",
            "+14155550123",
            null,
            passwords.encode(password),
            UserStatus.ACTIVE,
            ActorZone.INSPECTOR);
    inspector.addRole(UserRole.INSPECTOR);
    users.saveAndFlush(inspector);
  }

  @Test
  void inspectorWebLoginRequiresOtpBeforeIssuingAnySessionCredential() throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode data = objectMapper.readTree(response).path("data");
    assertThat(data.path("step").asText()).isEqualTo("OTP_REQUIRED");
    assertThat(data.path("accessToken").isMissingNode() || data.path("accessToken").isNull())
        .isTrue();
    assertThat(data.path("refreshToken").isMissingNode() || data.path("refreshToken").isNull())
        .isTrue();
    assertThat(data.path("challengeToken").asText()).isNotBlank();
    assertThat(data.path("availableOtpChannels").toString()).contains("EMAIL", "SMS");
  }

  @Test
  void selectingAndVerifyingTheEmailOtpIssuesTheBrowserSession() throws Exception {
    String loginResponse =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .with(
                        request -> {
                          request.setRemoteAddr("127.0.0.1");
                          return request;
                        })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String challenge =
        objectMapper.readTree(loginResponse).path("data").path("challengeToken").asText();

    mockMvc
        .perform(
            post("/api/v1/auth/otp/send")
                .with(
                    request -> {
                      request.setRemoteAddr("127.0.0.1");
                      return request;
                    })
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("challengeToken", challenge, "channel", "EMAIL"))))
        .andExpect(status().isOk());
    ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
    verify(otpDelivery)
        .deliver(
            eq(com.smartdroneinspection.users.domain.enums.LoginOtpChannel.EMAIL),
            eq(email),
            codeCaptor.capture());

    String verificationResponse =
        mockMvc
            .perform(
                post("/api/v1/auth/otp/verify")
                    .with(
                        request -> {
                          request.setRemoteAddr("127.0.0.1");
                          return request;
                        })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("challengeToken", challenge, "code", codeCaptor.getValue()))))
            .andExpect(status().isOk())
            .andExpect(header().exists("Set-Cookie"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode data = objectMapper.readTree(verificationResponse).path("data");
    assertThat(data.path("step").asText()).isEqualTo("AUTHENTICATED");
    assertThat(data.path("accessToken").asText()).isNotBlank();
    assertThat(
            mockMvc
                .perform(
                    post("/api/v1/auth/otp/verify")
                        .with(
                            request -> {
                              request.setRemoteAddr("127.0.0.1");
                              return request;
                            })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                Map.of(
                                    "challengeToken", challenge, "code", codeCaptor.getValue()))))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(401);
  }

  @Test
  void inspectorWebInitialPasswordSetupRequiresOtpAndIssuesNoSession() throws Exception {
    String temporaryPassword = "Temporary-" + UUID.randomUUID();
    User inspector =
        new User(
            "first-login-" + UUID.randomUUID() + "@example.test",
            "First Login Inspector",
            "+14155550123",
            null,
            passwords.encode(temporaryPassword),
            UserStatus.ACTIVE,
            ActorZone.INSPECTOR);
    inspector.addRole(UserRole.INSPECTOR);
    inspector.requirePasswordChange();
    users.saveAndFlush(inspector);

    String response =
        mockMvc
            .perform(
                post("/api/v1/auth/password/setup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "email",
                                inspector.getEmail(),
                                "currentPassword",
                                temporaryPassword,
                                "password",
                                "New-secure-password-" + UUID.randomUUID()))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode data = objectMapper.readTree(response).path("data");
    assertThat(data.path("step").asText()).isEqualTo("OTP_REQUIRED");
    assertThat(data.path("accessToken").isMissingNode() || data.path("accessToken").isNull())
        .isTrue();
    assertThat(data.path("refreshToken").isMissingNode() || data.path("refreshToken").isNull())
        .isTrue();
    assertThat(data.path("challengeToken").asText()).isNotBlank();
  }

  @Test
  void inspectorMobileLoginStillAuthenticatesWithoutWebOtp() throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/mobile/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode data = objectMapper.readTree(response).path("data");
    assertThat(data.path("step").asText()).isEqualTo("AUTHENTICATED");
    assertThat(data.path("accessToken").asText()).isNotBlank();
    assertThat(data.path("refreshToken").asText()).isNotBlank();
  }
}

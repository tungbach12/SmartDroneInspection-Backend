package com.smartdroneinspection.users.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.users.api.dto.request.UpdateAiFallbackPreferenceRequest;
import com.smartdroneinspection.users.api.dto.request.UpdatePilotLicenseRequest;
import com.smartdroneinspection.users.api.dto.response.UserResponse;
import com.smartdroneinspection.users.service.AuthService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class UserProfileControllerTest {

  private AuthService authService;
  private UserProfileController controller;

  @BeforeEach
  void setUp() {
    authService = mock(AuthService.class);
    controller = new UserProfileController(authService);
  }

  @Test
  void getProfileReturnsCurrentUser() {
    UUID userId = UUID.randomUUID();
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn(userId.toString());

    UserResponse response =
        new UserResponse(
            userId,
            "inspector@example.test",
            "Inspector Test",
            List.of("INSPECTOR"),
            "CUSTOMER_ORGANIZATION",
            UUID.randomUUID(),
            true);

    when(authService.me(userId)).thenReturn(response);

    var result = controller.getProfile(jwt);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().data().id()).isEqualTo(userId);
  }

  @Test
  void updatePilotLicenseInvokesAuthServiceAndReturnsUserResponse() {
    UUID userId = UUID.randomUUID();
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn(userId.toString());

    UpdatePilotLicenseRequest request =
        new UpdatePilotLicenseRequest(
            "RPL-VN-2026-0042",
            "CLASS_3",
            Instant.parse("2028-10-31T00:00:00Z"),
            "licenses/key.pdf");

    UserResponse response =
        new UserResponse(
            userId,
            "inspector@example.test",
            "Inspector Test",
            null,
            null,
            List.of("INSPECTOR"),
            "CUSTOMER_ORGANIZATION",
            UUID.randomUUID(),
            true,
            "RPL-VN-2026-0042",
            "CLASS_3",
            Instant.parse("2028-10-31T00:00:00Z"),
            "licenses/key.pdf");

    when(authService.updatePilotLicense(eq(userId), any(UpdatePilotLicenseRequest.class)))
        .thenReturn(response);

    var result = controller.updatePilotLicense(jwt, request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().data().pilotLicenseNumber()).isEqualTo("RPL-VN-2026-0042");
  }

  @Test
  void updateAiFallbackPreferenceInvokesAuthService() {
    UUID userId = UUID.randomUUID();
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn(userId.toString());

    UpdateAiFallbackPreferenceRequest request = new UpdateAiFallbackPreferenceRequest(true);

    UserResponse response =
        new UserResponse(
            userId,
            "inspector@example.test",
            "Inspector Test",
            List.of("INSPECTOR"),
            "CUSTOMER_ORGANIZATION",
            UUID.randomUUID(),
            true);

    when(authService.updateVisionFallbackPreference(userId, true)).thenReturn(response);

    var result = controller.updateAiFallbackPreference(jwt, request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().data().visionFallbackEnabled()).isTrue();
  }
}

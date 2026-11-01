package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.config.AuthProperties;
import com.smartdroneinspection.users.api.OAuth2Controller;
import com.smartdroneinspection.users.api.RefreshCookieService;
import com.smartdroneinspection.users.api.dto.response.AuthFlowResponse;
import com.smartdroneinspection.users.api.dto.response.AuthStep;
import com.smartdroneinspection.users.api.dto.response.UserResponse;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.ClientType;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.service.AuthService;
import com.smartdroneinspection.users.service.GoogleOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OAuth2ControllerTest {

  private GoogleOAuthService googleOAuthService;
  private AuthService authService;
  private AuthProperties properties;
  private RefreshCookieService cookies;
  private OAuth2Controller controller;

  @BeforeEach
  void setUp() {
    googleOAuthService = mock(GoogleOAuthService.class);
    authService = mock(AuthService.class);
    properties = new AuthProperties();
    properties.getGoogle().setClientId("test-client-id");
    properties.getGoogle().setFrontendSuccessUrl("http://localhost:3000/auth/callback");
    properties.getGoogle().setFrontendFailureUrl("http://localhost:3000/login");
    properties.getGoogle().setMobileSuccessUrl("smartdrone://auth/callback");
    cookies = new RefreshCookieService(properties);
    controller = new OAuth2Controller(googleOAuthService, authService, properties, cookies);
  }

  @Test
  void authorizeRedirectsToGoogleWhenConfigured() throws IOException {
    when(googleOAuthService.isConfigured()).thenReturn(true);
    when(googleOAuthService.buildAuthorizationUrl(ClientType.WEB))
        .thenReturn("https://accounts.google.com/o/oauth2/v2/auth?test=1");

    HttpServletResponse response = mock(HttpServletResponse.class);
    controller.authorizeGoogle("web", response);

    verify(response).sendRedirect("https://accounts.google.com/o/oauth2/v2/auth?test=1");
  }

  @Test
  void authorizeRedirectsToFailureWhenNotConfigured() throws IOException {
    when(googleOAuthService.isConfigured()).thenReturn(false);

    HttpServletResponse response = mock(HttpServletResponse.class);
    controller.authorizeGoogle("web", response);

    verify(response).sendRedirect("http://localhost:3000/login?error=google_oauth_not_configured");
  }

  @Test
  void callbackWebRedirectsToFrontendWithToken() throws IOException {
    when(googleOAuthService.validateStateAndGetClientType("state-web")).thenReturn(ClientType.WEB);
    when(googleOAuthService.exchangeCodeForUserInfo("valid-code"))
        .thenReturn(
            new GoogleOAuthService.GoogleUserInfo(
                "sub-1", "user@example.com", "User Name", "http://avatar.jpg"));

    UserResponse userResponse =
        new UserResponse(
            UUID.randomUUID(),
            "user@example.com",
            "User Name",
            null,
            "http://avatar.jpg",
            List.of(UserRole.CUSTOMER.value()),
            ActorZone.CUSTOMER_ORGANIZATION.name(),
            null,
            false);
    AuthFlowResponse flowResponse =
        new AuthFlowResponse(
            AuthStep.AUTHENTICATED, "jwt-access-token", "raw-refresh-token", 900, userResponse);
    AuthService.AuthResult authResult =
        new AuthService.AuthResult(flowResponse, "raw-refresh-token");

    when(authService.processOAuthLogin(
            eq("user@example.com"),
            eq("User Name"),
            eq("http://avatar.jpg"),
            eq(ClientType.WEB),
            any(),
            any(),
            any()))
        .thenReturn(authResult);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    controller.callbackGoogle("valid-code", "state-web", null, request, response);

    ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
    verify(response).sendRedirect(redirectCaptor.capture());
    assertThat(redirectCaptor.getValue())
        .startsWith("http://localhost:3000/auth/callback?accessToken=jwt-access-token");
  }

  @Test
  void callbackMobileRedirectsToDeepLink() throws IOException {
    when(googleOAuthService.validateStateAndGetClientType("state-mobile"))
        .thenReturn(ClientType.MOBILE);
    when(googleOAuthService.exchangeCodeForUserInfo("valid-code"))
        .thenReturn(
            new GoogleOAuthService.GoogleUserInfo(
                "sub-1", "user@example.com", "User Name", "http://avatar.jpg"));

    UserResponse userResponse =
        new UserResponse(
            UUID.randomUUID(),
            "user@example.com",
            "User Name",
            null,
            "http://avatar.jpg",
            List.of(UserRole.CUSTOMER.value()),
            ActorZone.CUSTOMER_ORGANIZATION.name(),
            null,
            false);
    AuthFlowResponse flowResponse =
        new AuthFlowResponse(
            AuthStep.AUTHENTICATED, "jwt-access-token", "raw-refresh-token", 900, userResponse);
    AuthService.AuthResult authResult =
        new AuthService.AuthResult(flowResponse, "raw-refresh-token");

    when(authService.processOAuthLogin(
            eq("user@example.com"),
            eq("User Name"),
            eq("http://avatar.jpg"),
            eq(ClientType.MOBILE),
            any(),
            any(),
            any()))
        .thenReturn(authResult);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    controller.callbackGoogle("valid-code", "state-mobile", null, request, response);

    ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
    verify(response).sendRedirect(redirectCaptor.capture());
    assertThat(redirectCaptor.getValue())
        .startsWith(
            "smartdrone://auth/callback?accessToken=jwt-access-token&refreshToken=raw-refresh-token");
  }

  @Test
  void callbackWithOAuthErrorRedirectsToFailureUrl() throws IOException {
    when(googleOAuthService.validateStateAndGetClientType(anyString())).thenReturn(ClientType.WEB);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    controller.callbackGoogle(null, "some-state", "access_denied", request, response);

    verify(response).sendRedirect("http://localhost:3000/login?error=access_denied");
  }
}

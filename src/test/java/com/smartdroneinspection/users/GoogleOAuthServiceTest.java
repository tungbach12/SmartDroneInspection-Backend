package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.shared.config.AuthProperties;
import com.smartdroneinspection.users.domain.enums.ClientType;
import com.smartdroneinspection.users.service.GoogleOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GoogleOAuthServiceTest {

  private AuthProperties properties;
  private GoogleOAuthService service;

  @BeforeEach
  void setUp() {
    properties = new AuthProperties();
    properties.getGoogle().setClientId("test-client-id");
    properties.getGoogle().setClientSecret("test-client-secret");
    properties
        .getGoogle()
        .setRedirectUri("http://localhost:8080/api/v1/auth/oauth2/callback/google");
    service = new GoogleOAuthService(properties, new ObjectMapper());
  }

  @Test
  void reportsConfiguredWhenKeysPresent() {
    assertThat(service.isConfigured()).isTrue();

    properties.getGoogle().setClientId("");
    assertThat(service.isConfigured()).isFalse();
  }

  @Test
  void buildsValidAuthorizationUrlForWeb() {
    String url = service.buildAuthorizationUrl(ClientType.WEB);
    assertThat(url).contains("https://accounts.google.com/o/oauth2/v2/auth");
    assertThat(url).contains("client_id=test-client-id");
    assertThat(url)
        .contains(
            "redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fv1%2Fauth%2Foauth2%2Fcallback%2Fgoogle");
    assertThat(url).contains("state=");
  }

  @Test
  void buildsValidAuthorizationUrlForMobile() {
    String url = service.buildAuthorizationUrl(ClientType.MOBILE);
    assertThat(url).contains("client_id=test-client-id");
  }

  @Test
  void validatesStateRoundtrip() {
    String urlWeb = service.buildAuthorizationUrl(ClientType.WEB);
    String stateWeb = extractParam(urlWeb, "state");
    assertThat(service.validateStateAndGetClientType(stateWeb)).isEqualTo(ClientType.WEB);

    String urlMobile = service.buildAuthorizationUrl(ClientType.MOBILE);
    String stateMobile = extractParam(urlMobile, "state");
    assertThat(service.validateStateAndGetClientType(stateMobile)).isEqualTo(ClientType.MOBILE);
  }

  @Test
  void rejectsTamperedState() {
    assertThat(service.validateStateAndGetClientType("invalid-state")).isEqualTo(ClientType.WEB);
  }

  private String extractParam(String url, String param) {
    int start = url.indexOf(param + "=");
    if (start == -1) return null;
    int end = url.indexOf("&", start);
    if (end == -1) end = url.length();
    return url.substring(start + param.length() + 1, end);
  }
}

package com.smartdroneinspection.users.api;

import static com.smartdroneinspection.users.api.AuthHttpSupport.clientIp;
import static com.smartdroneinspection.users.api.AuthHttpSupport.correlationId;
import static com.smartdroneinspection.users.api.AuthHttpSupport.userAgent;

import com.smartdroneinspection.shared.config.AuthProperties;
import com.smartdroneinspection.users.domain.enums.ClientType;
import com.smartdroneinspection.users.service.AuthService;
import com.smartdroneinspection.users.service.GoogleOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/auth/oauth2")
public class OAuth2Controller {

  private static final Logger log = LoggerFactory.getLogger(OAuth2Controller.class);

  private final GoogleOAuthService googleOAuthService;
  private final AuthService authService;
  private final AuthProperties properties;
  private final RefreshCookieService cookies;

  public OAuth2Controller(
      GoogleOAuthService googleOAuthService,
      AuthService authService,
      AuthProperties properties,
      RefreshCookieService cookies) {
    this.googleOAuthService = googleOAuthService;
    this.authService = authService;
    this.properties = properties;
    this.cookies = cookies;
  }

  @GetMapping("/authorize/google")
  public void authorizeGoogle(
      @RequestParam(name = "client_type", defaultValue = "web") String clientTypeParam,
      HttpServletResponse response)
      throws IOException {
    if (!googleOAuthService.isConfigured()) {
      String failureUrl =
          properties.getGoogle() != null
                  && !properties.getGoogle().getFrontendFailureUrl().isBlank()
              ? properties.getGoogle().getFrontendFailureUrl()
              : "http://localhost:3000/login";
      response.sendRedirect(failureUrl + "?error=google_oauth_not_configured");
      return;
    }
    ClientType clientType =
        "mobile".equalsIgnoreCase(clientTypeParam) ? ClientType.MOBILE : ClientType.WEB;
    String authUrl = googleOAuthService.buildAuthorizationUrl(clientType);
    response.sendRedirect(authUrl);
  }

  @GetMapping("/callback/google")
  public void callbackGoogle(
      @RequestParam(name = "code", required = false) String code,
      @RequestParam(name = "state", required = false) String state,
      @RequestParam(name = "error", required = false) String error,
      HttpServletRequest request,
      HttpServletResponse response)
      throws IOException {
    ClientType clientType = googleOAuthService.validateStateAndGetClientType(state);
    String failureBaseUrl =
        clientType == ClientType.MOBILE
            ? properties.getGoogle().getMobileSuccessUrl()
            : properties.getGoogle().getFrontendFailureUrl();

    if (error != null && !error.isBlank()) {
      log.warn("Google OAuth callback returned error: {}", error);
      redirectFailure(response, failureBaseUrl, error);
      return;
    }

    if (code == null || code.isBlank()) {
      log.warn("Google OAuth callback missing authorization code");
      redirectFailure(response, failureBaseUrl, "missing_code");
      return;
    }

    try {
      var userInfo = googleOAuthService.exchangeCodeForUserInfo(code);
      var result =
          authService.processOAuthLogin(
              userInfo.email(),
              userInfo.name(),
              userInfo.picture(),
              clientType,
              clientIp(request),
              userAgent(request),
              correlationId(request));

      if (clientType == ClientType.MOBILE) {
        String target =
            UriComponentsBuilder.fromUriString(properties.getGoogle().getMobileSuccessUrl())
                .queryParam("accessToken", result.response().accessToken())
                .queryParam("refreshToken", result.refreshToken())
                .build()
                .toUriString();
        response.sendRedirect(target);
      } else {
        var cookie = cookies.create(result.refreshToken());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        String target =
            UriComponentsBuilder.fromUriString(properties.getGoogle().getFrontendSuccessUrl())
                .queryParam("accessToken", result.response().accessToken())
                .build()
                .toUriString();
        response.sendRedirect(target);
      }
    } catch (Exception ex) {
      log.error("Google OAuth login failure", ex);
      redirectFailure(response, failureBaseUrl, "authentication_failed");
    }
  }

  private void redirectFailure(HttpServletResponse response, String baseUrl, String errorCode)
      throws IOException {
    String target =
        UriComponentsBuilder.fromUriString(baseUrl)
            .queryParam("error", errorCode)
            .build()
            .toUriString();
    response.sendRedirect(target);
  }
}

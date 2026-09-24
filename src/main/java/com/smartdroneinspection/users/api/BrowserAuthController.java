package com.smartdroneinspection.users.api;

import static com.smartdroneinspection.users.api.AuthHttpSupport.clientIp;
import static com.smartdroneinspection.users.api.AuthHttpSupport.correlationId;
import static com.smartdroneinspection.users.api.AuthHttpSupport.userAgent;

import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.users.api.dto.request.ClientRegistrationRequest;
import com.smartdroneinspection.users.api.dto.request.InitialPasswordChangeRequest;
import com.smartdroneinspection.users.api.dto.request.LoginRequest;
import com.smartdroneinspection.users.api.dto.request.PasswordChangeRequest;
import com.smartdroneinspection.users.api.dto.response.AuthFlowResponse;
import com.smartdroneinspection.users.api.dto.response.ClientRegistrationResponse;
import com.smartdroneinspection.users.api.dto.response.UserResponse;
import com.smartdroneinspection.users.domain.enums.ClientType;
import com.smartdroneinspection.users.service.AuthService;
import com.smartdroneinspection.users.service.ClientRegistrationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class BrowserAuthController {

  private final AuthService auth;
  private final ClientRegistrationService registration;
  private final RefreshCookieService cookies;

  public BrowserAuthController(
      AuthService auth, ClientRegistrationService registration, RefreshCookieService cookies) {
    this.auth = auth;
    this.registration = registration;
    this.cookies = cookies;
  }

  @GetMapping("/csrf")
  public ApiResponse<CsrfToken> csrf(CsrfToken token) {
    return ApiResponse.success(token);
  }

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<ClientRegistrationResponse>> register(
      @Valid @RequestBody ClientRegistrationRequest body, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                registration.register(
                    body, clientIp(request), userAgent(request), correlationId(request))));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthFlowResponse>> login(
      @Valid @RequestBody LoginRequest body, HttpServletRequest request) {
    var result =
        auth.login(
            body.email(),
            body.password(),
            ClientType.WEB,
            clientIp(request),
            userAgent(request),
            correlationId(request));
    return webResponse(result);
  }

  @PostMapping("/password/setup")
  public ResponseEntity<ApiResponse<AuthFlowResponse>> initialPassword(
      @Valid @RequestBody InitialPasswordChangeRequest body, HttpServletRequest request) {
    var result =
        auth.changeInitialPassword(
            body.email(),
            body.currentPassword(),
            body.password(),
            ClientType.WEB,
            clientIp(request),
            userAgent(request),
            correlationId(request));
    return webResponse(result);
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<AuthFlowResponse>> refresh(
      @CookieValue(name = RefreshCookieService.COOKIE_NAME) String refreshToken,
      HttpServletRequest request) {
    var result =
        auth.refresh(
            refreshToken,
            ClientType.WEB,
            clientIp(request),
            userAgent(request),
            correlationId(request));
    return webResponse(result);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    String refreshToken = cookieValue(request, RefreshCookieService.COOKIE_NAME);
    auth.logout(refreshToken, clientIp(request), userAgent(request), correlationId(request));
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
        .cacheControl(CacheControl.noStore())
        .build();
  }

  @PostMapping("/logout-all")
  public ResponseEntity<Void> logoutAll(
      @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
    auth.logoutAll(
        UUID.fromString(jwt.getSubject()),
        clientIp(request),
        userAgent(request),
        correlationId(request));
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
        .cacheControl(CacheControl.noStore())
        .build();
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(auth.me(UUID.fromString(jwt.getSubject()))));
  }

  @PostMapping("/password/change")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody PasswordChangeRequest body,
      HttpServletRequest request) {
    auth.changePassword(
        UUID.fromString(jwt.getSubject()),
        body.currentPassword(),
        body.newPassword(),
        clientIp(request),
        userAgent(request),
        correlationId(request));
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
        .cacheControl(CacheControl.noStore())
        .build();
  }

  private ResponseEntity<ApiResponse<AuthFlowResponse>> webResponse(AuthService.AuthResult result) {
    var response = result.response();
    var browserSafe =
        new AuthFlowResponse(
            response.step(),
            response.accessToken(),
            null,
            response.accessTokenExpiresInSeconds(),
            response.user());
    var builder = ResponseEntity.ok().cacheControl(CacheControl.noStore());
    if (result.refreshToken() != null) {
      builder.header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()).toString());
    }
    return builder.body(ApiResponse.success(browserSafe));
  }

  private String cookieValue(HttpServletRequest request, String name) {
    if (request.getCookies() == null) {
      return null;
    }
    return Arrays.stream(request.getCookies())
        .filter(cookie -> name.equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }
}

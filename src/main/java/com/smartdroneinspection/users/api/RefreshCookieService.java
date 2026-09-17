package com.smartdroneinspection.users.api;

import com.smartdroneinspection.shared.config.AuthProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookieService {

  public static final String COOKIE_NAME = "sdi_refresh";
  private final AuthProperties properties;

  public RefreshCookieService(AuthProperties properties) {
    this.properties = properties;
  }

  public ResponseCookie create(String token) {
    return ResponseCookie.from(COOKIE_NAME, token)
        .httpOnly(true)
        .secure(properties.isSecureCookies())
        .sameSite("Strict")
        .path("/api/v1/auth")
        .maxAge(properties.getRefreshTokenTtl())
        .build();
  }

  public ResponseCookie clear() {
    return ResponseCookie.from(COOKIE_NAME, "")
        .httpOnly(true)
        .secure(properties.isSecureCookies())
        .sameSite("Strict")
        .path("/api/v1/auth")
        .maxAge(Duration.ZERO)
        .build();
  }
}

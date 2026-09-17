package com.smartdroneinspection.shared.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

  private String issuer = "smart-drone-inspection";
  private String audience = "smart-drone-inspection-api";
  private Duration accessTokenTtl = Duration.ofMinutes(15);
  private Duration refreshTokenTtl = Duration.ofDays(7);
  private String jwtSecret = "";
  private String refreshTokenPepper = "";
  private boolean secureCookies = true;
  private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));
  private Bootstrap bootstrap = new Bootstrap();

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(String audience) {
    this.audience = audience;
  }

  public Duration getAccessTokenTtl() {
    return accessTokenTtl;
  }

  public void setAccessTokenTtl(Duration accessTokenTtl) {
    this.accessTokenTtl = accessTokenTtl;
  }

  public Duration getRefreshTokenTtl() {
    return refreshTokenTtl;
  }

  public void setRefreshTokenTtl(Duration refreshTokenTtl) {
    this.refreshTokenTtl = refreshTokenTtl;
  }

  public String getJwtSecret() {
    return jwtSecret;
  }

  public void setJwtSecret(String jwtSecret) {
    this.jwtSecret = jwtSecret;
  }

  public String getRefreshTokenPepper() {
    return refreshTokenPepper;
  }

  public void setRefreshTokenPepper(String refreshTokenPepper) {
    this.refreshTokenPepper = refreshTokenPepper;
  }

  public boolean isSecureCookies() {
    return secureCookies;
  }

  public void setSecureCookies(boolean secureCookies) {
    this.secureCookies = secureCookies;
  }

  public List<String> getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  public Bootstrap getBootstrap() {
    return bootstrap;
  }

  public void setBootstrap(Bootstrap bootstrap) {
    this.bootstrap = bootstrap;
  }

  public static class Bootstrap {

    private boolean enabled;
    private String email = "";
    private String password = "";
    private String fullName = "Platform Administrator";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public String getFullName() {
      return fullName;
    }

    public void setFullName(String fullName) {
      this.fullName = fullName;
    }
  }
}

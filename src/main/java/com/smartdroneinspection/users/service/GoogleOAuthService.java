package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.config.AuthProperties;
import com.smartdroneinspection.users.domain.enums.ClientType;
import com.smartdroneinspection.users.security.AuthCrypto;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GoogleOAuthService {

  private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);
  private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
  private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
  private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

  private final AuthProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public GoogleOAuthService(AuthProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
  }

  public boolean isConfigured() {
    return properties.getGoogle() != null
        && properties.getGoogle().getClientId() != null
        && !properties.getGoogle().getClientId().isBlank()
        && properties.getGoogle().getClientSecret() != null
        && !properties.getGoogle().getClientSecret().isBlank();
  }

  public String buildAuthorizationUrl(ClientType clientType) {
    String state = generateState(clientType);
    String redirectUri = getRedirectUri();
    return GOOGLE_AUTH_URL
        + "?response_type=code"
        + "&client_id="
        + urlEncode(properties.getGoogle().getClientId())
        + "&redirect_uri="
        + urlEncode(redirectUri)
        + "&scope="
        + urlEncode("openid email profile")
        + "&state="
        + urlEncode(state)
        + "&access_type=offline"
        + "&prompt=select_account";
  }

  public ClientType validateStateAndGetClientType(String state) {
    if (state == null || state.isBlank()) {
      return ClientType.WEB;
    }
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
      String[] parts = decoded.split(":");
      if (parts.length != 4) {
        return ClientType.WEB;
      }
      String type = parts[0];
      String nonce = parts[1];
      long timestamp = Long.parseLong(parts[2]);
      String signature = parts[3];

      long now = Instant.now().getEpochSecond();
      if (now - timestamp > 900 || timestamp > now + 300) {
        log.warn("Google OAuth state expired or invalid timestamp: {}", timestamp);
        return ClientType.WEB;
      }

      String expectedSig = hmac(type + ":" + nonce + ":" + timestamp);
      if (!expectedSig.equals(signature)) {
        log.warn("Google OAuth state signature mismatch");
        return ClientType.WEB;
      }

      return "MOBILE".equalsIgnoreCase(type) ? ClientType.MOBILE : ClientType.WEB;
    } catch (Exception ex) {
      log.warn("Failed to parse Google OAuth state: {}", ex.getMessage());
      return ClientType.WEB;
    }
  }

  public GoogleUserInfo exchangeCodeForUserInfo(String code) {
    try {
      String form =
          "code="
              + urlEncode(code)
              + "&client_id="
              + urlEncode(properties.getGoogle().getClientId())
              + "&client_secret="
              + urlEncode(properties.getGoogle().getClientSecret())
              + "&redirect_uri="
              + urlEncode(getRedirectUri())
              + "&grant_type=authorization_code";

      HttpRequest tokenRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(GOOGLE_TOKEN_URL))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(15))
              .POST(HttpRequest.BodyPublishers.ofString(form))
              .build();

      HttpResponse<String> tokenResponse =
          httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());

      if (tokenResponse.statusCode() != 200) {
        log.error(
            "Google token exchange failed with status {}: {}",
            tokenResponse.statusCode(),
            tokenResponse.body());
        throw new IllegalStateException("Google token exchange failed");
      }

      JsonNode tokenJson = objectMapper.readTree(tokenResponse.body());
      String accessToken =
          tokenJson.has("access_token") ? tokenJson.get("access_token").asText() : null;
      if (accessToken == null || accessToken.isBlank()) {
        throw new IllegalStateException("Google token response did not include access_token");
      }

      HttpRequest userInfoRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(GOOGLE_USERINFO_URL))
              .header("Authorization", "Bearer " + accessToken)
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(15))
              .GET()
              .build();

      HttpResponse<String> userInfoResponse =
          httpClient.send(userInfoRequest, HttpResponse.BodyHandlers.ofString());

      if (userInfoResponse.statusCode() != 200) {
        log.error(
            "Google userinfo failed with status {}: {}",
            userInfoResponse.statusCode(),
            userInfoResponse.body());
        throw new IllegalStateException("Google userinfo fetch failed");
      }

      JsonNode userJson = objectMapper.readTree(userInfoResponse.body());
      String sub = userJson.has("sub") ? userJson.get("sub").asText() : "";
      String email = userJson.has("email") ? userJson.get("email").asText() : "";
      String name = userJson.has("name") ? userJson.get("name").asText() : "";
      String picture = userJson.has("picture") ? userJson.get("picture").asText() : null;

      if (email.isBlank()) {
        throw new IllegalStateException("Google userinfo missing email");
      }

      return new GoogleUserInfo(sub, email, name, picture);
    } catch (Exception ex) {
      if (ex instanceof RuntimeException re) {
        throw re;
      }
      throw new RuntimeException("Error during Google OAuth exchange", ex);
    }
  }

  private String getRedirectUri() {
    String uri = properties.getGoogle().getRedirectUri();
    if (uri != null && !uri.isBlank()) {
      return uri;
    }
    return "http://localhost:8080/api/v1/auth/oauth2/callback/google";
  }

  private String generateState(ClientType clientType) {
    String type = clientType.name();
    String nonce = AuthCrypto.randomToken().substring(0, 16);
    long timestamp = Instant.now().getEpochSecond();
    String payload = type + ":" + nonce + ":" + timestamp;
    String sig = hmac(payload);
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((payload + ":" + sig).getBytes(StandardCharsets.UTF_8));
  }

  private String hmac(String value) {
    String secret = properties.getRefreshTokenPepper();
    if (secret == null || secret.isBlank()) {
      secret = properties.getJwtSecret();
    }
    if (secret == null || secret.isBlank()) {
      secret = "smartdrone-oauth-default-secret";
    }
    return AuthCrypto.hmacSha256Hex(value, secret);
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  public record GoogleUserInfo(String sub, String email, String name, String picture) {}
}

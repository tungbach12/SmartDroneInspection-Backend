package com.smartdroneinspection.users.security;

import com.smartdroneinspection.shared.config.AuthProperties;
import com.smartdroneinspection.shared.exception.AuthException;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GoogleTokenVerifier {

  private final AuthProperties properties;
  private final RestClient restClient;

  public GoogleTokenVerifier(AuthProperties properties) {
    this.properties = properties;
    this.restClient = RestClient.builder().build();
  }

  public GoogleUser verify(String idToken) {
    if (idToken == null || idToken.isBlank()) {
      throw new AuthException(
          HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Google token is missing.");
    }
    String configuredClientId = properties.getGoogle().getClientId();
    try {
      Map<String, Object> body =
          restClient
              .get()
              .uri("https://oauth2.googleapis.com/tokeninfo?id_token={idToken}", idToken.trim())
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, Object>>() {});

      if (body == null) {
        throw new AuthException(
            HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Invalid Google credential.");
      }

      String aud = String.valueOf(body.get("aud"));
      if (configuredClientId != null
          && !configuredClientId.isBlank()
          && !configuredClientId.equals(aud)) {
        throw new AuthException(
            HttpStatus.UNAUTHORIZED,
            "GOOGLE_AUDIENCE_MISMATCH",
            "Token was not issued for this application.");
      }

      Object emailVerified = body.get("email_verified");
      boolean verified =
          Boolean.TRUE.equals(emailVerified)
              || "true".equalsIgnoreCase(String.valueOf(emailVerified));
      if (!verified) {
        throw new AuthException(
            HttpStatus.UNAUTHORIZED,
            "GOOGLE_EMAIL_NOT_VERIFIED",
            "Google email has not been verified.");
      }

      String email = (String) body.get("email");
      if (email == null || email.isBlank()) {
        throw new AuthException(
            HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Email is missing from Google token.");
      }

      String name = (String) body.getOrDefault("name", email);
      String picture = (String) body.get("picture");
      return new GoogleUser(email, name, picture);
    } catch (AuthException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new AuthException(
          HttpStatus.UNAUTHORIZED,
          "INVALID_GOOGLE_TOKEN",
          "Google token validation failed: " + exception.getMessage());
    }
  }

  public record GoogleUser(String email, String name, String picture) {}
}

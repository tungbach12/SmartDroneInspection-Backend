package com.smartdroneinspection.users.security;

import com.smartdroneinspection.shared.config.AuthProperties;
import com.smartdroneinspection.users.domain.AuthSession;
import com.smartdroneinspection.users.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class AccessTokenService {

  private final JwtEncoder encoder;
  private final AuthProperties properties;

  public AccessTokenService(JwtEncoder encoder, AuthProperties properties) {
    this.encoder = encoder;
    this.properties = properties;
  }

  public IssuedAccessToken issue(User user, AuthSession session) {
    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(properties.getAccessTokenTtl());
    var claims =
        JwtClaimsSet.builder()
            .issuer(properties.getIssuer())
            .audience(List.of(properties.getAudience()))
            .subject(user.getId().toString())
            .issuedAt(issuedAt)
            .notBefore(issuedAt)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim("sid", session.getId().toString())
            .claim("auth_version", user.getAuthVersion())
            .claim("roles", user.roleValues())
            .claim("actor_zone", user.getActorZone().name())
            .claim("auth_time", issuedAt.getEpochSecond());
    if (user.getOrganizationId() != null) {
      claims.claim("org_id", user.getOrganizationId().toString());
    }
    var header = JwsHeader.with(MacAlgorithm.HS256).type("at+jwt").build();
    String token =
        encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    return new IssuedAccessToken(token, expiresAt);
  }

  public record IssuedAccessToken(String value, Instant expiresAt) {}
}

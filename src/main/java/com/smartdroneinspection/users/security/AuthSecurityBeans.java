package com.smartdroneinspection.users.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.smartdroneinspection.shared.config.AuthProperties;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthSecurityBeans {

  private static final Logger LOG = LoggerFactory.getLogger(AuthSecurityBeans.class);

  @Bean
  AuthSecrets authSecrets(AuthProperties properties, Environment environment) {
    boolean production = environment.matchesProfiles("prod", "production");
    byte[] jwtKey = decodeOrGenerate(properties.getJwtSecret(), production, "AUTH_JWT_SECRET");
    if (jwtKey.length < 32) {
      throw new IllegalStateException("AUTH_JWT_SECRET must contain at least 256 bits.");
    }
    byte[] pepper =
        decodeOrGenerate(
            properties.getRefreshTokenPepper(), production, "AUTH_REFRESH_TOKEN_PEPPER");
    return new AuthSecrets(
        new SecretKeySpec(jwtKey, "HmacSHA256"), Base64.getEncoder().encodeToString(pepper));
  }

  @Bean
  JwtEncoder jwtEncoder(AuthSecrets secrets) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(secrets.jwtKey()));
  }

  @Bean
  JwtDecoder jwtDecoder(AuthSecrets secrets, AuthProperties properties) {
    var decoder =
        NimbusJwtDecoder.withSecretKey(secrets.jwtKey())
            .macAlgorithm(MacAlgorithm.HS256)
            .validateType(false)
            .build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTypeValidator("at+jwt", "application/at+jwt"),
            new JwtTimestampValidator(),
            new JwtIssuerValidator(properties.getIssuer()),
            new JwtAudienceValidator(properties.getAudience())));
    return decoder;
  }

  private byte[] decodeOrGenerate(String encoded, boolean production, String variable) {
    if (encoded != null && !encoded.isBlank()) {
      try {
        return Base64.getDecoder().decode(encoded);
      } catch (IllegalArgumentException exception) {
        throw new IllegalStateException(variable + " must be Base64 encoded.", exception);
      }
    }
    if (production) {
      throw new IllegalStateException(variable + " is required in production.");
    }
    LOG.warn("{} is not configured; using an ephemeral development value", variable);
    return AuthCrypto.randomBytes(32);
  }

  public record AuthSecrets(SecretKey jwtKey, String refreshTokenPepper) {}
}

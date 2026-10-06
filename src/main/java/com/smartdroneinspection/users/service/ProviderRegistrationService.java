package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.ProviderRegistrationRequest;
import com.smartdroneinspection.users.api.dto.response.ProviderRegistrationResponse;
import com.smartdroneinspection.users.domain.ProviderOrganization;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.ProviderOrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.security.AuthCrypto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderRegistrationService {

  private static final Logger log = LoggerFactory.getLogger(ProviderRegistrationService.class);
  private static final Duration ACTIVATION_TTL = Duration.ofHours(24);

  private final ProviderOrganizationRepository providers;
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final PasswordPolicy passwordPolicy;
  private final SecurityAuditService audit;

  public ProviderRegistrationService(
      ProviderOrganizationRepository providers,
      UserRepository users,
      PasswordEncoder passwords,
      PasswordPolicy passwordPolicy,
      SecurityAuditService audit) {
    this.providers = providers;
    this.users = users;
    this.passwords = passwords;
    this.passwordPolicy = passwordPolicy;
    this.audit = audit;
  }

  @Transactional
  public ProviderRegistrationResponse register(
      ProviderRegistrationRequest request,
      String ipAddress,
      String userAgent,
      String correlationId) {
    String email = User.normalizeEmail(request.email());
    String fullName = request.fullName().trim();

    if (users.findByNormalizedEmail(email).isPresent()) {
      throw new AuthException(
          HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already in use.");
    }
    if (providers.existsByTaxCode(request.taxCode().trim())) {
      throw new AuthException(
          HttpStatus.CONFLICT, "TAX_CODE_ALREADY_EXISTS", "Tax code is already in use.");
    }

    passwordPolicy.validate(request.password(), email, fullName);

    ProviderOrganization provider =
        providers.save(
            new ProviderOrganization(
                request.providerName().trim(),
                request.legalName().trim(),
                request.taxCode().trim(),
                request.businessLicenseNo().trim()));

    User manager =
        new User(
            email,
            fullName,
            passwords.encode(request.password()),
            UserStatus.DISABLED,
            ActorZone.SERVICE_WORKFORCE,
            null,
            provider.getId());
    manager.addRole(UserRole.PROVIDER_MANAGER);

    String rawToken =
        Base64.getUrlEncoder().withoutPadding().encodeToString(AuthCrypto.randomBytes(48));
    manager.setActivationToken(sha256Hex(rawToken), Instant.now().plus(ACTIVATION_TTL));

    // Flush before the audit insert: SecurityAuditService writes raw SQL with an FK to users.
    manager = users.saveAndFlush(manager);

    audit.record(
        null,
        manager.getId(),
        "PROVIDER_REGISTRATION",
        "SUCCESS",
        ipAddress,
        userAgent,
        correlationId);

    String link = frontendBaseUrl() + "/activate-provider?token=" + rawToken;
    log.info("Provider activation email for {}: {}", email, link);

    return new ProviderRegistrationResponse(provider.getId(), manager.getId(), link);
  }

  @Transactional
  public void activate(String rawToken, String ipAddress, String userAgent, String correlationId) {
    User user =
        users
            .findByActivationTokenHash(sha256Hex(rawToken))
            .filter(u -> u.isActivationTokenValid(Instant.now()))
            .orElseThrow(
                () ->
                    new AuthException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_OR_EXPIRED_TOKEN",
                        "Activation token is invalid or expired."));
    user.activate();
    users.saveAndFlush(user);
    audit.record(
        null, user.getId(), "PROVIDER_ACTIVATION", "SUCCESS", ipAddress, userAgent, correlationId);
  }

  private static String frontendBaseUrl() {
    String value = System.getenv("FRONTEND_BASE_URL");
    return value == null || value.isBlank() ? "http://localhost:5173" : value;
  }

  private static String sha256Hex(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to hash activation token", ex);
    }
  }
}

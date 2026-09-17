package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.config.AuthProperties;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.response.AuthFlowResponse;
import com.smartdroneinspection.users.api.dto.response.AuthStep;
import com.smartdroneinspection.users.api.dto.response.UserResponse;
import com.smartdroneinspection.users.domain.AuthSession;
import com.smartdroneinspection.users.domain.ClientType;
import com.smartdroneinspection.users.domain.RefreshToken;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.repository.AuthSessionRepository;
import com.smartdroneinspection.users.repository.RefreshTokenRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.security.AccessTokenService;
import com.smartdroneinspection.users.security.AuthCrypto;
import com.smartdroneinspection.users.security.AuthSecurityBeans.AuthSecrets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository users;
  private final AuthSessionRepository sessions;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwords;
  private final PasswordPolicy passwordPolicy;
  private final AccessTokenService accessTokens;
  private final AuthProperties properties;
  private final String tokenPepper;
  private final LoginFailureService loginFailures;
  private final SecurityAuditService audit;
  private final String dummyPasswordHash;

  public AuthService(
      UserRepository users,
      AuthSessionRepository sessions,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwords,
      PasswordPolicy passwordPolicy,
      AccessTokenService accessTokens,
      AuthProperties properties,
      AuthSecrets secrets,
      LoginFailureService loginFailures,
      SecurityAuditService audit) {
    this.users = users;
    this.sessions = sessions;
    this.refreshTokens = refreshTokens;
    this.passwords = passwords;
    this.passwordPolicy = passwordPolicy;
    this.accessTokens = accessTokens;
    this.properties = properties;
    this.tokenPepper = secrets.refreshTokenPepper();
    this.loginFailures = loginFailures;
    this.audit = audit;
    this.dummyPasswordHash = passwords.encode(AuthCrypto.randomToken());
  }

  @Transactional(noRollbackFor = AuthException.class)
  public AuthResult login(
      String email,
      String password,
      ClientType clientType,
      String ipAddress,
      String userAgent,
      String correlationId) {
    User user = users.findByNormalizedEmail(User.normalizeEmail(email)).orElse(null);
    if (user == null) {
      passwords.matches(password, dummyPasswordHash);
      audit.record(null, null, "AUTH_LOGIN", "FAILURE", ipAddress, userAgent, correlationId);
      throw AuthException.invalidCredentials();
    }

    if (!user.active() || user.getPasswordHash() == null) {
      audit.record(null, user.getId(), "AUTH_LOGIN", "DENIED", ipAddress, userAgent, correlationId);
      throw AuthException.invalidCredentials();
    }

    if (!passwords.matches(password, user.getPasswordHash())) {
      loginFailures.record(user.getId());
      audit.record(
          null, user.getId(), "AUTH_LOGIN", "FAILURE", ipAddress, userAgent, correlationId);
      throw AuthException.invalidCredentials();
    }

    if (user.isMustChangePassword()) {
      audit.record(
          user.getId(),
          user.getId(),
          "AUTH_LOGIN_PASSWORD_CHANGE_REQUIRED",
          "SUCCESS",
          ipAddress,
          userAgent,
          correlationId);
      return new AuthResult(
          new AuthFlowResponse(AuthStep.PASSWORD_CHANGE_REQUIRED, null, null, 0, toResponse(user)),
          null);
    }

    if (passwords.upgradeEncoding(user.getPasswordHash())) {
      user.setPasswordHash(passwords.encode(password));
    }
    user.recordSuccessfulLogin(ipAddress);
    AuthResult result = createSession(user, clientType);
    audit.record(
        user.getId(), user.getId(), "AUTH_LOGIN", "SUCCESS", ipAddress, userAgent, correlationId);
    return result;
  }

  @Transactional
  public AuthResult changeInitialPassword(
      String email,
      String currentPassword,
      String newPassword,
      ClientType clientType,
      String ipAddress,
      String userAgent,
      String correlationId) {
    User user =
        users
            .findByNormalizedEmail(User.normalizeEmail(email))
            .orElseThrow(AuthException::invalidCredentials);
    if (!user.active()
        || !user.isMustChangePassword()
        || user.getPasswordHash() == null
        || !passwords.matches(currentPassword, user.getPasswordHash())) {
      throw AuthException.invalidCredentials();
    }
    passwordPolicy.validate(newPassword, user.getEmail(), user.getFullName());
    user.setPasswordHash(passwords.encode(newPassword));
    user.incrementAuthVersion();
    revokeAllSessions(user.getId(), "initial-password-change");
    AuthResult result = createSession(user, clientType);
    audit.record(
        user.getId(),
        user.getId(),
        "AUTH_INITIAL_PASSWORD_CHANGED",
        "SUCCESS",
        ipAddress,
        userAgent,
        correlationId);
    return result;
  }

  @Transactional(noRollbackFor = AuthException.class)
  public AuthResult refresh(
      String rawToken,
      ClientType expectedClient,
      String ipAddress,
      String userAgent,
      String correlationId) {
    String hash = tokenHash(rawToken);
    RefreshToken stored =
        refreshTokens.findForUpdate(hash).orElseThrow(AuthException::invalidCredentials);
    AuthSession session = stored.getSession();
    Instant now = Instant.now();

    if (stored.consumed()) {
      revokeSession(session, "refresh-token-reuse");
      audit.record(
          null,
          session.getUser().getId(),
          "AUTH_REFRESH_REUSE",
          "DENIED",
          ipAddress,
          userAgent,
          correlationId);
      throw AuthException.invalidCredentials();
    }
    if (!stored.getExpiresAt().isAfter(now)
        || !session.active(now)
        || session.getClientType() != expectedClient
        || !session.getUser().active()) {
      revokeSession(session, "refresh-rejected");
      throw AuthException.invalidCredentials();
    }

    String nextRawToken = AuthCrypto.randomToken();
    Instant expiresAt = session.getExpiresAt();
    RefreshToken replacement =
        refreshTokens.saveAndFlush(
            new RefreshToken(session, tokenHash(nextRawToken), now, expiresAt));
    stored.rotate();
    var access = accessTokens.issue(session.getUser(), session);
    audit.record(
        session.getUser().getId(),
        session.getUser().getId(),
        "AUTH_REFRESH",
        "SUCCESS",
        ipAddress,
        userAgent,
        correlationId);
    return authenticated(session.getUser(), access.value(), access.expiresAt(), nextRawToken);
  }

  @Transactional
  public void logout(String rawToken, String ipAddress, String userAgent, String correlationId) {
    if (rawToken == null || rawToken.isBlank()) {
      return;
    }
    refreshTokens
        .findForUpdate(tokenHash(rawToken))
        .ifPresent(
            token -> {
              revokeSession(token.getSession(), "logout");
              audit.record(
                  token.getSession().getUser().getId(),
                  token.getSession().getUser().getId(),
                  "AUTH_LOGOUT",
                  "SUCCESS",
                  ipAddress,
                  userAgent,
                  correlationId);
            });
  }

  @Transactional
  public void logoutAll(UUID userId, String ipAddress, String userAgent, String correlationId) {
    User user = users.findDetailedById(userId).orElseThrow(AuthException::invalidCredentials);
    revokeAllSessions(userId, "logout-all");
    user.incrementAuthVersion();
    audit.record(userId, userId, "AUTH_LOGOUT_ALL", "SUCCESS", ipAddress, userAgent, correlationId);
  }

  @Transactional(readOnly = true)
  public UserResponse me(UUID userId) {
    User user =
        users
            .findDetailedById(userId)
            .filter(User::active)
            .orElseThrow(
                () ->
                    new AuthException(
                        HttpStatus.UNAUTHORIZED,
                        "ACCOUNT_INACTIVE",
                        "Authentication is required."));
    return toResponse(user);
  }

  @Transactional
  public void changePassword(
      UUID userId,
      String currentPassword,
      String newPassword,
      String ipAddress,
      String userAgent,
      String correlationId) {
    User user = users.findDetailedById(userId).orElseThrow(AuthException::invalidCredentials);
    if (user.getPasswordHash() == null
        || !passwords.matches(currentPassword, user.getPasswordHash())) {
      throw AuthException.invalidCredentials();
    }
    passwordPolicy.validate(newPassword, user.getEmail(), user.getFullName());
    user.setPasswordHash(passwords.encode(newPassword));
    user.incrementAuthVersion();
    revokeAllSessions(userId, "password-change");
    audit.record(
        userId, userId, "AUTH_PASSWORD_CHANGED", "SUCCESS", ipAddress, userAgent, correlationId);
  }

  private AuthResult createSession(User user, ClientType clientType) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(properties.getRefreshTokenTtl());
    AuthSession session = sessions.save(new AuthSession(user, clientType, expiresAt));
    String rawRefreshToken = AuthCrypto.randomToken();
    refreshTokens.save(new RefreshToken(session, tokenHash(rawRefreshToken), now, expiresAt));
    var access = accessTokens.issue(user, session);
    return authenticated(user, access.value(), access.expiresAt(), rawRefreshToken);
  }

  private AuthResult authenticated(
      User user, String accessToken, Instant expiresAt, String refreshToken) {
    long expiresIn = Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
    return new AuthResult(
        new AuthFlowResponse(
            AuthStep.AUTHENTICATED, accessToken, refreshToken, expiresIn, toResponse(user)),
        refreshToken);
  }

  private UserResponse toResponse(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        user.roleValues(),
        user.getActorZone().name(),
        user.getOrganizationId());
  }

  public void revokeAllSessions(UUID userId, String reason) {
    sessions
        .findByUser_IdAndRevokedAtIsNull(userId)
        .forEach(session -> revokeSession(session, reason));
  }

  private void revokeSession(AuthSession session, String reason) {
    session.revoke(reason);
    refreshTokens.findBySession_Id(session.getId()).forEach(token -> token.revoke(reason));
  }

  private String tokenHash(String rawToken) {
    return AuthCrypto.hmacSha256Hex(rawToken, tokenPepper);
  }

  public record AuthResult(AuthFlowResponse response, String refreshToken) {}
}

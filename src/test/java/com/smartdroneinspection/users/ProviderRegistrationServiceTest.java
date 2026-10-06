package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.ProviderRegistrationRequest;
import com.smartdroneinspection.users.domain.ProviderOrganization;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.ProviderOrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.service.PasswordPolicy;
import com.smartdroneinspection.users.service.ProviderRegistrationService;
import com.smartdroneinspection.users.service.SecurityAuditService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ProviderRegistrationServiceTest {

  @Mock ProviderOrganizationRepository providers;
  @Mock UserRepository users;
  @Mock PasswordEncoder passwords;
  @Mock PasswordPolicy passwordPolicy;
  @Mock SecurityAuditService audit;
  @Mock ProviderOrganization savedProvider;

  @Captor ArgumentCaptor<User> userCaptor;

  @InjectMocks ProviderRegistrationService registration;

  private static final String PASSWORD = "a sufficiently long passphrase";

  private static ProviderRegistrationRequest request(String email, String taxCode) {
    return new ProviderRegistrationRequest(
        email,
        "Provider Manager",
        "Drone Provider Ltd",
        "Drone Provider Limited Liability Company",
        taxCode,
        "BL-123456",
        PASSWORD);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Test
  void createsPendingProviderAndDisabledManagerWithActivationToken() {
    UUID providerId = UUID.randomUUID();
    when(users.findByNormalizedEmail("manager@example.com")).thenReturn(Optional.empty());
    when(providers.existsByTaxCode("TAX-001")).thenReturn(false);
    when(providers.save(any(ProviderOrganization.class))).thenReturn(savedProvider);
    when(savedProvider.getId()).thenReturn(providerId);
    when(passwords.encode(PASSWORD)).thenReturn("{argon2}encoded-password");
    when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        registration.register(
            request(" Manager@Example.com ", "TAX-001"), "127.0.0.1", "JUnit", "test-correlation");

    verify(users).saveAndFlush(userCaptor.capture());
    User created = userCaptor.getValue();
    assertThat(created.getStatus()).isEqualTo(UserStatus.DISABLED);
    assertThat(created.getActorZone()).isEqualTo(ActorZone.SERVICE_WORKFORCE);
    assertThat(created.getOrganizationId()).isNull();
    assertThat(created.getProviderId()).isEqualTo(providerId);
    assertThat(created.roleValues()).containsExactly(UserRole.PROVIDER_MANAGER.value());
    assertThat(created.isMustChangePassword()).isFalse();
    assertThat(created.getActivationTokenHash()).isNotBlank();
    assertThat(created.getActivationExpiresAt())
        .isAfter(Instant.now().plus(23, ChronoUnit.HOURS))
        .isBefore(Instant.now().plus(25, ChronoUnit.HOURS));

    assertThat(response.providerId()).isEqualTo(providerId);
    assertThat(response.userId()).isEqualTo(created.getId());
    assertThat(response.activationLink()).contains("/activate-provider?token=");

    verify(audit)
        .record(
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq(created.getId()),
            org.mockito.ArgumentMatchers.eq("PROVIDER_REGISTRATION"),
            org.mockito.ArgumentMatchers.eq("SUCCESS"),
            org.mockito.ArgumentMatchers.eq("127.0.0.1"),
            org.mockito.ArgumentMatchers.eq("JUnit"),
            org.mockito.ArgumentMatchers.eq("test-correlation"));
  }

  @Test
  void rejectsDuplicateEmailBeforeCreatingProvider() {
    when(users.findByNormalizedEmail("manager@example.com"))
        .thenReturn(Optional.of(mock(User.class)));

    assertThatThrownBy(
            () ->
                registration.register(request("manager@example.com", "TAX-001"), null, null, null))
        .isInstanceOf(AuthException.class)
        .hasMessage("Email is already in use.")
        .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo("EMAIL_ALREADY_EXISTS"));
  }

  @Test
  void rejectsDuplicateTaxCode() {
    when(users.findByNormalizedEmail("manager@example.com")).thenReturn(Optional.empty());
    when(providers.existsByTaxCode("TAX-001")).thenReturn(true);

    assertThatThrownBy(
            () ->
                registration.register(request("manager@example.com", "TAX-001"), null, null, null))
        .isInstanceOf(AuthException.class)
        .hasMessage("Tax code is already in use.")
        .satisfies(
            ex -> assertThat(((AuthException) ex).code()).isEqualTo("TAX_CODE_ALREADY_EXISTS"));
  }

  @Test
  void activatesUserWithValidToken() {
    User user = mock(User.class);
    UUID userId = UUID.randomUUID();
    when(users.findByActivationTokenHash(sha256("raw-token"))).thenReturn(Optional.of(user));
    when(user.isActivationTokenValid(any(Instant.class))).thenReturn(true);
    when(user.getId()).thenReturn(userId);
    when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    registration.activate("raw-token", "127.0.0.1", "JUnit", "test-correlation");

    verify(user).activate();
    verify(users).saveAndFlush(user);
    verify(audit)
        .record(
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq("PROVIDER_ACTIVATION"),
            org.mockito.ArgumentMatchers.eq("SUCCESS"),
            org.mockito.ArgumentMatchers.eq("127.0.0.1"),
            org.mockito.ArgumentMatchers.eq("JUnit"),
            org.mockito.ArgumentMatchers.eq("test-correlation"));
  }

  @Test
  void rejectsExpiredToken() {
    User user = mock(User.class);
    when(users.findByActivationTokenHash(sha256("raw-token"))).thenReturn(Optional.of(user));
    when(user.isActivationTokenValid(any(Instant.class))).thenReturn(false);

    assertThatThrownBy(
            () -> registration.activate("raw-token", "127.0.0.1", "JUnit", "test-correlation"))
        .isInstanceOf(AuthException.class)
        .hasMessage("Activation token is invalid or expired.")
        .satisfies(
            ex -> assertThat(((AuthException) ex).code()).isEqualTo("INVALID_OR_EXPIRED_TOKEN"));
  }

  @Test
  void rejectsUnknownToken() {
    when(users.findByActivationTokenHash(sha256("unknown-token"))).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> registration.activate("unknown-token", "127.0.0.1", "JUnit", "test-correlation"))
        .isInstanceOf(AuthException.class)
        .hasMessage("Activation token is invalid or expired.");
  }
}

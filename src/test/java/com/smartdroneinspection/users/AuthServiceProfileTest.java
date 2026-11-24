package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.config.AuthProperties;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.UpdatePayoutBankDetailsRequest;
import com.smartdroneinspection.users.api.dto.request.UpdateProfileRequest;
import com.smartdroneinspection.users.domain.InspectorProfile;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.AuthSessionRepository;
import com.smartdroneinspection.users.repository.InspectorProfileRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.security.AccessTokenService;
import com.smartdroneinspection.users.security.AuthSecurityBeans.AuthSecrets;
import com.smartdroneinspection.users.security.GoogleTokenVerifier;
import com.smartdroneinspection.users.service.AuthService;
import com.smartdroneinspection.users.service.LoginFailureService;
import com.smartdroneinspection.users.service.LoginOtpService;
import com.smartdroneinspection.users.service.PasswordPolicy;
import com.smartdroneinspection.users.service.PilotLicenseClassService;
import com.smartdroneinspection.users.service.SecurityAuditService;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceProfileTest {

  private UserRepository users;
  private InspectorProfileRepository inspectorProfiles;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    inspectorProfiles = mock(InspectorProfileRepository.class);
    PasswordEncoder passwords = mock(PasswordEncoder.class);
    when(passwords.encode(any())).thenReturn("encoded");
    authService =
        new AuthService(
            users,
            mock(AuthSessionRepository.class),
            passwords,
            mock(PasswordPolicy.class),
            mock(AccessTokenService.class),
            new AuthProperties(),
            new AuthSecrets(
                new SecretKeySpec(new byte[32], "HmacSHA256"),
                "test-pepper-at-least-32-chars-long"),
            mock(LoginFailureService.class),
            mock(SecurityAuditService.class),
            mock(LoginOtpService.class),
            inspectorProfiles,
            mock(PilotLicenseClassService.class),
            mock(GoogleTokenVerifier.class));
  }

  @Test
  void updateProfilePersistsTrimmedNameAndPhoneForActiveUser() {
    UUID userId = UUID.randomUUID();
    User user =
        new User("client@smartdrone.test", "Old Name", null, null, "hash", UserStatus.ACTIVE);
    user.addRole(UserRole.CUSTOMER);
    ReflectionTestUtils.setField(user, "id", userId);

    when(users.findDetailedById(userId)).thenReturn(Optional.of(user));
    when(users.saveAndFlush(user)).thenReturn(user);
    when(inspectorProfiles.findById(userId)).thenReturn(Optional.empty());

    var response =
        authService.updateProfile(
            userId, new UpdateProfileRequest("  New Client Name  ", "  +84 901 234 567  "));

    assertThat(user.getFullName()).isEqualTo("New Client Name");
    assertThat(user.getPhoneNumber()).isEqualTo("+84 901 234 567");
    assertThat(response.fullName()).isEqualTo("New Client Name");
    assertThat(response.phoneNumber()).isEqualTo("+84 901 234 567");
    assertThat(response.email()).isEqualTo("client@smartdrone.test");
  }

  @Test
  void updateProfileClearsBlankPhoneNumberToNull() {
    UUID userId = UUID.randomUUID();
    User user =
        new User(
            "manager@smartdrone.test",
            "Manager One",
            "+84 900 000 000",
            null,
            "hash",
            UserStatus.ACTIVE);
    user.addRole(UserRole.MANAGER);
    ReflectionTestUtils.setField(user, "id", userId);

    when(users.findDetailedById(userId)).thenReturn(Optional.of(user));
    when(users.saveAndFlush(user)).thenReturn(user);
    when(inspectorProfiles.findById(userId)).thenReturn(Optional.empty());

    var response =
        authService.updateProfile(userId, new UpdateProfileRequest("Manager Updated", "   "));

    assertThat(user.getPhoneNumber()).isNull();
    assertThat(response.phoneNumber()).isNull();
    assertThat(response.fullName()).isEqualTo("Manager Updated");
  }

  @Test
  void updateProfileRejectsInactiveUser() {
    UUID userId = UUID.randomUUID();
    User disabled =
        new User(
            "disabled@smartdrone.test", "Disabled User", null, null, "hash", UserStatus.DISABLED);
    ReflectionTestUtils.setField(disabled, "id", userId);

    when(users.findDetailedById(userId)).thenReturn(Optional.of(disabled));

    assertThatThrownBy(
            () ->
                authService.updateProfile(
                    userId, new UpdateProfileRequest("Someone", "0901234567")))
        .isInstanceOf(AuthException.class)
        .satisfies(
            ex -> assertThat(((AuthException) ex).status()).isEqualTo(HttpStatus.UNAUTHORIZED));
  }

  @Test
  void updatePayoutBankDetailsPersistsTrimmedBankAccountForActiveInspector() {
    UUID userId = UUID.randomUUID();
    User user =
        new User(
            "inspector@smartdrone.test", "Pham Van Bay", null, null, "hash", UserStatus.ACTIVE);
    user.addRole(UserRole.INSPECTOR);
    ReflectionTestUtils.setField(user, "id", userId);
    InspectorProfile profile = new InspectorProfile(userId);

    when(users.findDetailedById(userId)).thenReturn(Optional.of(user));
    when(inspectorProfiles.findById(userId)).thenReturn(Optional.of(profile));
    when(inspectorProfiles.saveAndFlush(profile)).thenReturn(profile);

    var response =
        authService.updatePayoutBankDetails(
            userId,
            new UpdatePayoutBankDetailsRequest(
                "  Vietcombank  ", "  0071001234567 ", "  PHAM VAN BAY  ", "  CN Sai Gon "));

    assertThat(profile.getPayoutBankName()).isEqualTo("Vietcombank");
    assertThat(profile.getPayoutBankAccountNumber()).isEqualTo("0071001234567");
    assertThat(profile.getPayoutBankAccountHolder()).isEqualTo("PHAM VAN BAY");
    assertThat(profile.getPayoutBankBranch()).isEqualTo("CN Sai Gon");
    assertThat(profile.hasCompletePayoutBankDetails()).isTrue();
    assertThat(response.payoutBankName()).isEqualTo("Vietcombank");
    assertThat(response.payoutBankAccountNumber()).isEqualTo("0071001234567");
    assertThat(response.payoutBankAccountHolder()).isEqualTo("PHAM VAN BAY");
    assertThat(response.payoutBankBranch()).isEqualTo("CN Sai Gon");
  }
}

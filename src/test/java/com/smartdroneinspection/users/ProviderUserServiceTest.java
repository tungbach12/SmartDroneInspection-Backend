package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.ProviderStaffRequest;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.service.ProviderUserService;
import com.smartdroneinspection.users.service.SecurityAuditService;
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
class ProviderUserServiceTest {

  @Mock UserRepository users;
  @Mock PasswordEncoder passwords;
  @Mock SecurityAuditService audit;

  @Captor ArgumentCaptor<User> userCaptor;

  @InjectMocks ProviderUserService staffService;

  private User manager(UserRole role) {
    UUID providerId = UUID.randomUUID();
    User manager =
        new User(
            "manager@example.com",
            "Provider Manager",
            "{argon2}hash",
            UserStatus.ACTIVE,
            ActorZone.SERVICE_WORKFORCE,
            null,
            providerId);
    manager.addRole(role);
    return manager;
  }

  private User managerWithOtherDetails(UUID providerId) {
    User manager =
        new User(
            "manager@example.com",
            "Provider Manager",
            "{argon2}hash",
            UserStatus.ACTIVE,
            ActorZone.SERVICE_WORKFORCE,
            null,
            providerId);
    manager.addRole(UserRole.PROVIDER_MANAGER);
    return manager;
  }

  @Test
  void createsInspectorScopedToProviderWithRequirePasswordChange() {
    UUID providerId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    User manager = managerWithOtherDetails(providerId);
    when(users.findDetailedById(managerId)).thenReturn(Optional.of(manager));
    when(users.findByNormalizedEmail("inspector@example.com")).thenReturn(Optional.empty());
    when(passwords.encode(any(String.class))).thenReturn("{argon2}encoded");
    when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        staffService.createStaffUser(
            managerId,
            new ProviderStaffRequest(
                " Inspector@Example.com ", "Site Inspector", UserRole.INSPECTOR),
            "127.0.0.1",
            "JUnit",
            "test-correlation");

    verify(users).saveAndFlush(userCaptor.capture());
    User created = userCaptor.getValue();
    UUID createdId = created.getId();
    assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(created.getActorZone()).isEqualTo(ActorZone.SERVICE_WORKFORCE);
    assertThat(created.getOrganizationId()).isNull();
    assertThat(created.getProviderId()).isEqualTo(providerId);
    assertThat(created.roleValues()).containsExactly(UserRole.INSPECTOR.value());
    assertThat(created.isMustChangePassword()).isTrue();
    assertThat(response.user().providerId()).isEqualTo(providerId);
    assertThat(response.user().roles()).containsExactly(UserRole.INSPECTOR.value());
    assertThat(response.temporaryPassword()).isNotBlank();
    verify(audit)
        .record(
            org.mockito.ArgumentMatchers.eq(managerId),
            org.mockito.ArgumentMatchers.eq(createdId),
            org.mockito.ArgumentMatchers.eq("PROVIDER_STAFF_CREATED"),
            org.mockito.ArgumentMatchers.eq("SUCCESS"),
            org.mockito.ArgumentMatchers.eq("127.0.0.1"),
            org.mockito.ArgumentMatchers.eq("JUnit"),
            org.mockito.ArgumentMatchers.eq("test-correlation"));
  }

  @Test
  void rejectsNonManagerCaller() {
    UUID managerId = UUID.randomUUID();
    User manager = manager(UserRole.INSPECTOR);
    when(users.findDetailedById(managerId)).thenReturn(Optional.of(manager));

    assertThatThrownBy(
            () ->
                staffService.createStaffUser(
                    managerId,
                    new ProviderStaffRequest("n@example.com", "Name", UserRole.INSPECTOR),
                    null,
                    null,
                    null))
        .isInstanceOf(AuthException.class)
        .hasMessage("Not a provider manager.")
        .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo("NOT_PROVIDER_MANAGER"));
  }

  @Test
  void rejectsInvalidStaffRole() {
    UUID managerId = UUID.randomUUID();
    User manager = manager(UserRole.PROVIDER_MANAGER);
    when(users.findDetailedById(managerId)).thenReturn(Optional.of(manager));

    assertThatThrownBy(
            () ->
                staffService.createStaffUser(
                    managerId,
                    new ProviderStaffRequest("n@example.com", "Name", UserRole.PROVIDER_MANAGER),
                    null,
                    null,
                    null))
        .isInstanceOf(AuthException.class)
        .hasMessage("Role must be INSPECTOR or MAINTENANCE_ENGINEER.")
        .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo("INVALID_STAFF_ROLE"));
  }

  @Test
  void rejectsDuplicateEmail() {
    UUID managerId = UUID.randomUUID();
    User manager = manager(UserRole.PROVIDER_MANAGER);
    when(users.findDetailedById(managerId)).thenReturn(Optional.of(manager));
    when(users.findByNormalizedEmail("inspector@example.com"))
        .thenReturn(Optional.of(manager(UserRole.INSPECTOR)));

    assertThatThrownBy(
            () ->
                staffService.createStaffUser(
                    managerId,
                    new ProviderStaffRequest("inspector@example.com", "Name", UserRole.INSPECTOR),
                    null,
                    null,
                    null))
        .isInstanceOf(AuthException.class)
        .hasMessage("Email is already in use.")
        .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo("EMAIL_ALREADY_EXISTS"));
  }
}

package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.CreateUserRequest;
import com.smartdroneinspection.users.api.dto.request.UpdateRolesRequest;
import com.smartdroneinspection.users.domain.RolePolicy;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.service.AdminUserService;
import com.smartdroneinspection.users.service.AuthService;
import com.smartdroneinspection.users.service.SecurityAuditService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

  @Mock UserRepository users;
  @Mock RolePolicy rolePolicy;
  @Mock PasswordEncoder passwords;
  @Mock AuthService auth;
  @Mock SecurityAuditService audit;

  @Captor ArgumentCaptor<User> userCaptor;

  @InjectMocks AdminUserService service;

  @Test
  void flushesUserBeforeRecordingCreationAudit() {
    UUID actorId = UUID.randomUUID();
    when(users.findByNormalizedEmail("customer@example.com")).thenReturn(Optional.empty());
    when(passwords.encode(any(String.class))).thenReturn("{argon2}encoded-password");
    when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.create(
            actorId,
            new CreateUserRequest(
                "customer@example.com", "Customer Manager", Set.of(UserRole.CUSTOMER)),
            "127.0.0.1",
            "JUnit",
            "test-correlation");

    verify(users).saveAndFlush(userCaptor.capture());
    User created = userCaptor.getValue();
    assertThat(created.roleValues()).containsExactly(UserRole.CUSTOMER.value());
    verify(audit)
        .record(
            eq(actorId),
            eq(created.getId()),
            eq("USER_CREATED"),
            eq("SUCCESS"),
            eq("127.0.0.1"),
            eq("JUnit"),
            eq("test-correlation"));
    assertThat(response.user().email()).isEqualTo("customer@example.com");
  }

  @Test
  void rejectsDemotingTheLastActiveAdmin() {
    UUID actorId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    User target =
        new User(
            "last-admin@example.com",
            "Last Admin",
            "{argon2}encoded-password",
            com.smartdroneinspection.users.domain.enums.UserStatus.ACTIVE);
    target.addRole(UserRole.ADMIN);
    ReflectionTestUtils.setField(target, "id", targetId);

    when(users.findDetailedById(targetId)).thenReturn(Optional.of(target));
    when(users.findActiveAdmins()).thenReturn(List.of(target));

    assertThatThrownBy(
            () ->
                service.updateRoles(
                    actorId,
                    targetId,
                    new UpdateRolesRequest(Set.of(UserRole.MANAGER)),
                    null,
                    null,
                    null))
        .isInstanceOf(AuthException.class)
        .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo("LAST_ADMIN_REQUIRED"));
  }
}

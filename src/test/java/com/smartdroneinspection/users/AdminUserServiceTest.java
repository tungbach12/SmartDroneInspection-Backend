package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.users.api.dto.request.CreateUserRequest;
import com.smartdroneinspection.users.domain.RolePolicy;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.service.AdminUserService;
import com.smartdroneinspection.users.service.AuthService;
import com.smartdroneinspection.users.service.SecurityAuditService;
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
    when(users.findByNormalizedEmail("engineer@example.com")).thenReturn(Optional.empty());
    when(passwords.encode(any(String.class))).thenReturn("{argon2}encoded-password");
    when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.create(
            actorId,
            new CreateUserRequest(
                "engineer@example.com",
                "Maintenance Engineer",
                ActorZone.SERVICE_WORKFORCE,
                null,
                Set.of(UserRole.MAINTENANCE_ENGINEER)),
            "127.0.0.1",
            "JUnit",
            "test-correlation");

    verify(users).saveAndFlush(userCaptor.capture());
    User created = userCaptor.getValue();
    assertThat(created.roleValues()).containsExactly(UserRole.MAINTENANCE_ENGINEER.value());
    verify(audit)
        .record(
            eq(actorId),
            eq(created.getId()),
            eq("USER_CREATED"),
            eq("SUCCESS"),
            eq("127.0.0.1"),
            eq("JUnit"),
            eq("test-correlation"));
    assertThat(response.user().email()).isEqualTo("engineer@example.com");
  }
}

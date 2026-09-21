package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.ClientRegistrationRequest;
import com.smartdroneinspection.users.domain.Organization;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.service.ClientRegistrationService;
import com.smartdroneinspection.users.service.PasswordPolicy;
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
class ClientRegistrationServiceTest {

  @Mock OrganizationRepository organizations;
  @Mock UserRepository users;
  @Mock PasswordEncoder passwords;
  @Mock PasswordPolicy passwordPolicy;
  @Mock SecurityAuditService audit;
  @Mock Organization savedOrganization;

  @Captor ArgumentCaptor<User> userCaptor;

  @InjectMocks ClientRegistrationService registration;

  @Test
  void createsActiveOrganizationAndClientOnly() {
    UUID organizationId = UUID.randomUUID();
    when(users.findByNormalizedEmail("client@example.com")).thenReturn(Optional.empty());
    when(organizations.findByCodeIgnoreCase("ACME")).thenReturn(Optional.empty());
    when(organizations.save(any(Organization.class))).thenReturn(savedOrganization);
    when(savedOrganization.getId()).thenReturn(organizationId);
    when(savedOrganization.getName()).thenReturn("Acme Infrastructure");
    when(savedOrganization.getCode()).thenReturn("ACME");
    when(passwords.encode("a sufficiently long passphrase")).thenReturn("{argon2}encoded-password");
    when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        registration.register(
            new ClientRegistrationRequest(
                " Client@Example.com ",
                "Company Representative",
                "Acme Infrastructure",
                "acme",
                "a sufficiently long passphrase"),
            "127.0.0.1",
            "JUnit",
            "test-correlation");

    verify(users).save(userCaptor.capture());
    User created = userCaptor.getValue();
    assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(created.getActorZone()).isEqualTo(ActorZone.CUSTOMER_ORGANIZATION);
    assertThat(created.getOrganizationId()).isEqualTo(organizationId);
    assertThat(created.roleValues()).containsExactly(UserRole.CLIENT.value());
    assertThat(response.organizationId()).isEqualTo(organizationId);
    assertThat(response.organizationCode()).isEqualTo("ACME");
  }

  @Test
  void rejectsDuplicateEmailBeforeCreatingAnOrganization() {
    when(users.findByNormalizedEmail("client@example.com"))
        .thenReturn(Optional.of(mock(User.class)));

    assertThatThrownBy(
            () ->
                registration.register(
                    new ClientRegistrationRequest(
                        "client@example.com",
                        "Company Representative",
                        "Acme Infrastructure",
                        "ACME",
                        "a sufficiently long passphrase"),
                    null,
                    null,
                    null))
        .isInstanceOf(AuthException.class)
        .hasMessage("Email is already in use.");
  }
}

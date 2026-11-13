package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.users.domain.InspectorProfile;
import com.smartdroneinspection.users.domain.RolePolicy;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.InspectorProfileRepository;
import com.smartdroneinspection.users.repository.OrganizationRepository;
import com.smartdroneinspection.users.repository.UserRepository;
import com.smartdroneinspection.users.service.AdminUserService;
import com.smartdroneinspection.users.service.InspectorProfileService;
import com.smartdroneinspection.users.service.SecurityAuditService;
import com.smartdroneinspection.users.service.UserAccessService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserAccessInspectorProfileProjectionTest {

  @Mock private UserRepository users;
  @Mock private OrganizationRepository organizations;
  @Mock private AdminUserService adminUsers;
  @Mock private RolePolicy rolePolicy;
  @Mock private SecurityAuditService securityAudit;
  @Mock private InspectorProfileService inspectorProfiles;
  @Mock private InspectorProfileRepository inspectorProfileRepository;

  @Test
  void activeUserLicenseProjectionReadsInspectorProfile() {
    UUID userId = UUID.randomUUID();
    User user =
        new User(
            "inspector@example.test",
            "Inspector",
            "password-hash",
            UserStatus.ACTIVE,
            ActorZone.INSPECTOR_PARTNER,
            null);
    user.addRole(UserRole.INSPECTOR);
    ReflectionTestUtils.setField(user, "id", userId);
    InspectorProfile profile = new InspectorProfile(userId);
    Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");
    profile.updatePilotLicense("LIC-123", "CLASS-2", expiresAt, "licenses/scan.pdf");
    when(users.findDetailedById(userId)).thenReturn(Optional.of(user));
    when(inspectorProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

    UserAccess.ActiveUser activeUser =
        new UserAccessService(
                users,
                organizations,
                adminUsers,
                rolePolicy,
                securityAudit,
                inspectorProfiles,
                inspectorProfileRepository)
            .findActiveUser(userId)
            .orElseThrow();

    assertThat(activeUser.pilotLicenseNumber()).isEqualTo("LIC-123");
    assertThat(activeUser.pilotLicenseClass()).isEqualTo("CLASS-2");
    assertThat(activeUser.pilotLicenseExpiresAt()).isEqualTo(expiresAt);
    assertThat(activeUser.pilotLicenseFileKey()).isEqualTo("licenses/scan.pdf");
    assertThat(activeUser.avatarUrl()).isNull();
  }

  @Test
  void activeUserWithoutInspectorProfileKeepsNullLicenseProjection() {
    UUID userId = UUID.randomUUID();
    User user =
        new User(
            "inspector@example.test",
            "Inspector",
            "password-hash",
            UserStatus.ACTIVE,
            ActorZone.INSPECTOR_PARTNER,
            null);
    ReflectionTestUtils.setField(user, "id", userId);
    when(users.findDetailedById(userId)).thenReturn(Optional.of(user));
    when(inspectorProfileRepository.findById(userId)).thenReturn(Optional.empty());

    UserAccess.ActiveUser activeUser =
        new UserAccessService(
                users,
                organizations,
                adminUsers,
                rolePolicy,
                securityAudit,
                inspectorProfiles,
                inspectorProfileRepository)
            .findActiveUser(userId)
            .orElseThrow();

    assertThat(activeUser.pilotLicenseNumber()).isNull();
    assertThat(activeUser.pilotLicenseClass()).isNull();
    assertThat(activeUser.pilotLicenseExpiresAt()).isNull();
    assertThat(activeUser.pilotLicenseFileKey()).isNull();
    assertThat(activeUser.avatarUrl()).isNull();
  }
}

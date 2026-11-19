package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.contracts.api.dto.response.PlatformOperationsSettingsResponse;
import com.smartdroneinspection.contracts.domain.PlatformOperationsSettings;
import com.smartdroneinspection.contracts.repository.PlatformOperationsSettingsRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PlatformOperationsSettingsServiceTest {

  private PlatformOperationsSettingsRepository repository;
  private UserAccess users;
  private PlatformOperationsSettingsService service;

  @BeforeEach
  void setUp() {
    repository = mock(PlatformOperationsSettingsRepository.class);
    users = mock(UserAccess.class);
    service = new PlatformOperationsSettingsService(repository, users);
  }

  @Test
  void defaultsToTwentyFourHoursWhenNoSettingsExist() {
    assertThat(service.getAssignmentAcceptanceTimeoutHours()).isEqualTo(24);
  }

  @Test
  void adminCanConfigureCustomAssignmentAcceptanceTimeoutHours() {
    UUID adminId = UUID.randomUUID();
    when(users.findActiveUser(adminId))
        .thenReturn(
            Optional.of(
                new ActiveUser(
                    adminId,
                    Set.of(Roles.ADMIN),
                    "Admin User",
                    "admin@smartdrone.local",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)));
    when(repository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());
    when(repository.saveAndFlush(any(PlatformOperationsSettings.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    PlatformOperationsSettingsResponse updated = service.updateSettings(adminId, 48);
    assertThat(updated.assignmentAcceptanceTimeoutHours()).isEqualTo(48);
    assertThat(updated.updatedByUserId()).isEqualTo(adminId);
  }

  @Test
  void rejectsNonAdminFromReadingOrUpdatingSettings() {
    UUID managerId = UUID.randomUUID();
    when(users.findActiveUser(managerId))
        .thenReturn(
            Optional.of(
                new ActiveUser(
                    managerId,
                    Set.of(Roles.MANAGER),
                    "Manager User",
                    "manager@smartdrone.local",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)));

    assertThatThrownBy(() -> service.updateSettings(managerId, 12))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void rejectsInvalidTimeoutHours() {
    assertThatThrownBy(() -> new PlatformOperationsSettings(0, UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PlatformOperationsSettings(721, UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

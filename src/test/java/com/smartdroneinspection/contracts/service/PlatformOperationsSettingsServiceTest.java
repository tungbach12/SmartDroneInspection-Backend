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
  void defaultsToTwentyFourHoursAndTwentyLeadDaysWhenNoSettingsExist() {
    assertThat(service.getAssignmentAcceptanceTimeoutHours()).isEqualTo(24);
    assertThat(service.getServiceRequestLeadDays()).isEqualTo(20);
  }

  @Test
  void adminCanConfigureCustomAssignmentAcceptanceTimeoutHoursAndLeadDays() {
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

    PlatformOperationsSettingsResponse updated = service.updateSettings(adminId, 48, 10);
    assertThat(updated.assignmentAcceptanceTimeoutHours()).isEqualTo(48);
    assertThat(updated.serviceRequestLeadDays()).isEqualTo(10);
    assertThat(updated.updatedByUserId()).isEqualTo(adminId);
  }

  @Test
  void preservesExistingLeadDaysWhenOmittedInUpdate() {
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
    PlatformOperationsSettings existing = new PlatformOperationsSettings(24, 14, adminId);
    when(repository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(any(PlatformOperationsSettings.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    PlatformOperationsSettingsResponse updated = service.updateSettings(adminId, 36);
    assertThat(updated.assignmentAcceptanceTimeoutHours()).isEqualTo(36);
    assertThat(updated.serviceRequestLeadDays()).isEqualTo(14);
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
  void rejectsInvalidTimeoutHoursAndLeadDays() {
    assertThatThrownBy(() -> new PlatformOperationsSettings(0, UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PlatformOperationsSettings(721, UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PlatformOperationsSettings(24, -1, UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PlatformOperationsSettings(24, 366, UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

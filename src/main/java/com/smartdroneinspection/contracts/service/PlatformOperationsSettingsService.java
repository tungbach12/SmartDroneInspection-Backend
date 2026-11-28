package com.smartdroneinspection.contracts.service;

import com.smartdroneinspection.contracts.api.dto.response.PlatformOperationsSettingsResponse;
import com.smartdroneinspection.contracts.domain.PlatformOperationsSettings;
import com.smartdroneinspection.contracts.repository.PlatformOperationsSettingsRepository;
import com.smartdroneinspection.shared.api.PlatformOperationsPolicyPort;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing Admin-configurable operational policies (dispatch timeout and scheduling lead
 * days).
 */
@Service
public class PlatformOperationsSettingsService implements PlatformOperationsPolicyPort {

  private final PlatformOperationsSettingsRepository repository;
  private final UserAccess users;

  public PlatformOperationsSettingsService(
      PlatformOperationsSettingsRepository repository, UserAccess users) {
    this.repository = repository;
    this.users = users;
  }

  @Override
  @Transactional(readOnly = true)
  public int getAssignmentAcceptanceTimeoutHours() {
    return repository
        .findFirstByOrderByUpdatedAtDesc()
        .map(PlatformOperationsSettings::getAssignmentAcceptanceTimeoutHours)
        .orElse(DEFAULT_ASSIGNMENT_ACCEPTANCE_TIMEOUT_HOURS);
  }

  @Override
  @Transactional(readOnly = true)
  public int getServiceRequestLeadDays() {
    return repository
        .findFirstByOrderByUpdatedAtDesc()
        .map(PlatformOperationsSettings::getServiceRequestLeadDays)
        .orElse(DEFAULT_SERVICE_REQUEST_LEAD_DAYS);
  }

  @Override
  @Transactional(readOnly = true)
  public int getApiRequestTimeoutSeconds() {
    return repository
        .findFirstByOrderByUpdatedAtDesc()
        .map(PlatformOperationsSettings::getApiRequestTimeoutSeconds)
        .orElse(DEFAULT_API_REQUEST_TIMEOUT_SECONDS);
  }

  @Transactional(readOnly = true)
  public PlatformOperationsSettingsResponse getSettings(UUID actorId) {
    requireAdmin(actorId);
    return repository
        .findFirstByOrderByUpdatedAtDesc()
        .map(PlatformOperationsSettingsResponse::from)
        .orElseGet(PlatformOperationsSettingsResponse::defaults);
  }

  @Transactional
  public PlatformOperationsSettingsResponse updateSettings(
      UUID actorId, int assignmentAcceptanceTimeoutHours) {
    return updateSettings(actorId, assignmentAcceptanceTimeoutHours, null, null);
  }

  @Transactional
  public PlatformOperationsSettingsResponse updateSettings(
      UUID actorId, int assignmentAcceptanceTimeoutHours, Integer serviceRequestLeadDays) {
    return updateSettings(actorId, assignmentAcceptanceTimeoutHours, serviceRequestLeadDays, null);
  }

  @Transactional
  public PlatformOperationsSettingsResponse updateSettings(
      UUID actorId,
      int assignmentAcceptanceTimeoutHours,
      Integer serviceRequestLeadDays,
      Integer apiRequestTimeoutSeconds) {
    requireAdmin(actorId);
    PlatformOperationsSettings settings = repository.findFirstByOrderByUpdatedAtDesc().orElse(null);
    int resolvedLeadDays =
        serviceRequestLeadDays != null
            ? serviceRequestLeadDays
            : settings != null
                ? settings.getServiceRequestLeadDays()
                : DEFAULT_SERVICE_REQUEST_LEAD_DAYS;
    int resolvedTimeoutSeconds =
        apiRequestTimeoutSeconds != null
            ? apiRequestTimeoutSeconds
            : settings != null
                ? settings.getApiRequestTimeoutSeconds()
                : DEFAULT_API_REQUEST_TIMEOUT_SECONDS;
    if (settings == null) {
      settings =
          new PlatformOperationsSettings(
              assignmentAcceptanceTimeoutHours, resolvedLeadDays, resolvedTimeoutSeconds, actorId);
    } else {
      settings.updateSettings(
          assignmentAcceptanceTimeoutHours, resolvedLeadDays, resolvedTimeoutSeconds, actorId);
    }
    return PlatformOperationsSettingsResponse.from(repository.saveAndFlush(settings));
  }

  private void requireAdmin(UUID actorId) {
    UserAccess.ActiveUser actor =
        users
            .findActiveUser(actorId)
            .orElseThrow(
                () -> new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User not active"));
    if (!actor.hasRole(Roles.ADMIN)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only Admin can manage platform operations settings");
    }
  }
}

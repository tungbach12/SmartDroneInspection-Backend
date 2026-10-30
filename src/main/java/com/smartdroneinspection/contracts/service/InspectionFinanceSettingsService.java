package com.smartdroneinspection.contracts.service;

import com.smartdroneinspection.contracts.api.dto.response.InspectionFinanceSettingsResponse;
import com.smartdroneinspection.contracts.domain.InspectionFinanceSettings;
import com.smartdroneinspection.contracts.repository.InspectionFinanceSettingsRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing platform-wide inspection financial settlement rates.
 *
 * <p><strong>MVP ASSUMPTION NOTICE FOR ACCOUNTANT REVIEW:</strong> Rates configured here
 * (commission, VAT, TNCN) are snapshotted onto inspection occurrences at acceptance time. TNCN rate
 * applies to Inspector gross compensation after platform commission. This is a simplified product
 * assumption for MVP workflow and accounting validation, NOT statutory tax advice.
 */
@Service
public class InspectionFinanceSettingsService {

  private final InspectionFinanceSettingsRepository repository;
  private final UserAccess users;

  public InspectionFinanceSettingsService(
      InspectionFinanceSettingsRepository repository, UserAccess users) {
    this.repository = repository;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public InspectionFinanceSettingsResponse getSettings(UUID actorId) {
    requireAdmin(actorId);
    return repository
        .findCurrentSettings()
        .map(InspectionFinanceSettingsResponse::from)
        .orElseGet(InspectionFinanceSettingsResponse::unconfigured);
  }

  @Transactional
  public InspectionFinanceSettingsResponse updateSettings(
      UUID actorId, BigDecimal commissionRate, BigDecimal vatRate, BigDecimal tncnRate) {
    requireAdmin(actorId);
    InspectionFinanceSettings settings = repository.findCurrentSettings().orElse(null);
    if (settings == null) {
      settings = new InspectionFinanceSettings(commissionRate, vatRate, tncnRate, actorId);
    } else {
      settings.updateRates(commissionRate, vatRate, tncnRate, actorId);
    }
    return InspectionFinanceSettingsResponse.from(repository.saveAndFlush(settings));
  }

  @Transactional(readOnly = true)
  public InspectionFinanceSettings requireConfiguredRates() {
    InspectionFinanceSettings settings = repository.findCurrentSettings().orElse(null);
    if (settings == null || !settings.isFullyConfigured()) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINANCE_SETTINGS_REQUIRED",
          "Admin must configure inspection commission, VAT, and TNCN rates before payable quotes can be issued");
    }
    return settings;
  }

  @Transactional(readOnly = true)
  public Optional<InspectionFinanceSettings> findCurrentSettings() {
    return repository.findCurrentSettings();
  }

  private void requireAdmin(UUID actorId) {
    UserAccess.ActiveUser actor =
        users
            .findActiveUser(actorId)
            .orElseThrow(() -> failure(HttpStatus.FORBIDDEN, "FORBIDDEN", "User not active"));
    if (!actor.hasRole(Roles.ADMIN)) {
      throw failure(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only Admin can manage inspection finance settings");
    }
  }

  private static BusinessException failure(HttpStatus status, String code, String detail) {
    return new BusinessException(status, code, detail);
  }
}

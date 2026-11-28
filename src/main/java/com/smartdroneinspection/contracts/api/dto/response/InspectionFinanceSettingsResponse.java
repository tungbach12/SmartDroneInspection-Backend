package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.InspectionFinanceSettings;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InspectionFinanceSettingsResponse(
    BigDecimal commissionRate,
    BigDecimal vatRate,
    BigDecimal tncnRate,
    BigDecimal tncnThresholdAmount,
    boolean configured,
    UUID updatedByUserId,
    Instant updatedAt,
    String accountingDisclaimer) {

  private static final String MVP_DISCLAIMER =
      "MVP assumption: Tax base = Inspector gross payout after platform commission and before TNCN. "
          + "Configured TNCN rate applies when Inspector gross payout meets or exceeds the configured TNCN threshold (default 2,000,000 VND per payout; 0 VND applies to all payouts). "
          + "Rates, threshold, and withholding are snapshotted per occurrence for accounting validation, not statutory tax advice.";

  public static InspectionFinanceSettingsResponse from(InspectionFinanceSettings settings) {
    return new InspectionFinanceSettingsResponse(
        settings.getCommissionRate(),
        settings.getVatRate(),
        settings.getTncnRate(),
        settings.getTncnThresholdAmount(),
        settings.isFullyConfigured(),
        settings.getUpdatedByUserId(),
        settings.getUpdatedAt(),
        MVP_DISCLAIMER);
  }

  public static InspectionFinanceSettingsResponse unconfigured() {
    return new InspectionFinanceSettingsResponse(
        null,
        null,
        null,
        InspectionFinanceSettings.DEFAULT_TNCN_THRESHOLD_AMOUNT,
        false,
        null,
        null,
        MVP_DISCLAIMER);
  }
}

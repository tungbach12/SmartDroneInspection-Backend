package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.InspectionFinanceSettings;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InspectionFinanceSettingsResponse(
    BigDecimal commissionRate,
    BigDecimal vatRate,
    BigDecimal tncnRate,
    boolean configured,
    UUID updatedByUserId,
    Instant updatedAt,
    String accountingDisclaimer) {

  private static final String MVP_DISCLAIMER =
      "MVP assumption: Tax base = Inspector gross payout after platform commission and before TNCN. "
          + "Configured TNCN rate is applied to this base. Base, rate, and withholding are snapshotted per occurrence. "
          + "This is a simplified product assumption for MVP and accounting validation, not statutory tax advice.";

  public static InspectionFinanceSettingsResponse from(InspectionFinanceSettings settings) {
    return new InspectionFinanceSettingsResponse(
        settings.getCommissionRate(),
        settings.getVatRate(),
        settings.getTncnRate(),
        settings.isFullyConfigured(),
        settings.getUpdatedByUserId(),
        settings.getUpdatedAt(),
        MVP_DISCLAIMER);
  }

  public static InspectionFinanceSettingsResponse unconfigured() {
    return new InspectionFinanceSettingsResponse(
        null, null, null, false, null, null, MVP_DISCLAIMER);
  }
}

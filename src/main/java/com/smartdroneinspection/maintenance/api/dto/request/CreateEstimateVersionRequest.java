package com.smartdroneinspection.maintenance.api.dto.request;

import com.smartdroneinspection.maintenance.domain.enums.CostLineKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * MF4-06: a new estimate version with its priced lines.
 *
 * <p>The baseline total is not supplied by the client. The SYSTEM derives each line amount from
 * quantity and unit rate and derives the version total from the lines.
 */
public record CreateEstimateVersionRequest(
    @NotBlank String currency,
    String taxBasis,
    String assumptions,
    @NotBlank String snapshot,
    @NotNull List<EstimateLineRequest> lines) {

  /** One priced line. A missing or null unit rate is rejected rather than stored as zero. */
  public record EstimateLineRequest(
      UUID taskId,
      @NotNull CostLineKind lineKind,
      @NotBlank String description,
      BigDecimal quantity,
      String unit,
      BigDecimal unitRate,
      String evidenceReference) {}
}

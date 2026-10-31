package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.enums.CostLineKind;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * MF4: RAG-assisted cost and material estimate suggestion based on approved scope and planned
 * tasks.
 */
public record SuggestEstimateResponse(
    String currency, String assumptions, BigDecimal estimatedTotal, List<SuggestedLineDto> lines) {

  public record SuggestedLineDto(
      UUID taskId,
      CostLineKind lineKind,
      String description,
      BigDecimal quantity,
      String unit,
      BigDecimal unitRate,
      BigDecimal amount) {}
}

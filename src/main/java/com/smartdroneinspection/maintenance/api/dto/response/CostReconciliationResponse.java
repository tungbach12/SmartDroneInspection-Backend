package com.smartdroneinspection.maintenance.api.dto.response;

import java.math.BigDecimal;

/**
 * MF4-19: the reconciliation summary. Every figure is computed by the SYSTEM from stored records;
 * the client supplies only actual cost lines.
 *
 * <p>{@code variancePercent} is null when the authorized amount is zero, because a percentage
 * against a zero baseline has no meaning.
 */
public record CostReconciliationResponse(
    BigDecimal approvedBaselineTotal,
    BigDecimal approvedChangeDelta,
    BigDecimal authorizedAmount,
    BigDecimal actualTotal,
    BigDecimal variance,
    BigDecimal variancePercent,
    String currency) {}

package com.smartdroneinspection.maintenance.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * MF4-19: cost reconciliation for an accepted work order.
 *
 * <p>The authorized amount and variance are computed by the SYSTEM from the approved baseline, the
 * approved change deltas and the reconciled actual lines. The client supplies only the actuals.
 */
public record ReconcileCostsRequest(
    @NotEmpty List<@Valid RecordActualCostLineRequest> actualLines) {}

package com.smartdroneinspection.maintenance.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * MF4-10: the engineer records time on a task. The narrative travels inside {@code testReadings}
 * because the target schema has no dedicated summary column for work logs.
 */
public record RecordWorkLogRequest(
    @NotNull UUID taskId,
    Instant startedAt,
    Instant endedAt,
    @NotNull BigDecimal hours,
    String actualCostReferences,
    String asLeftCondition,
    String testReadings) {}

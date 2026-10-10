package com.smartdroneinspection.maintenance.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

/** MF4-05: the lead divides approved scope into a task. Task numbers are assigned by the server. */
public record CreateTaskRequest(
    @NotBlank String name,
    String method,
    UUID assignedEngineerUserId,
    Instant plannedStartAt,
    Instant plannedEndAt,
    String acceptanceCriteria) {}

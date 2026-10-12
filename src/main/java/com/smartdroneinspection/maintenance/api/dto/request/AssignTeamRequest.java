package com.smartdroneinspection.maintenance.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * MF4-03/04: designates exactly one lead, one report author and one independent accepting reviewer.
 * The domain rejects a reviewer drawn from the executing team.
 */
public record AssignTeamRequest(
    @NotNull UUID leadUserId,
    @NotNull UUID reportAuthorUserId,
    @NotNull UUID acceptingReviewerUserId,
    List<UUID> memberUserIds,
    String reason) {}

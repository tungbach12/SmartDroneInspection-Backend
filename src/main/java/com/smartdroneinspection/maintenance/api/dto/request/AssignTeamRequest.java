package com.smartdroneinspection.maintenance.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * MF4-02: designates the lead, report author and optional team members. The customer owner remains
 * the independent accepting party outside the executing team.
 */
public record AssignTeamRequest(
    @NotNull UUID leadUserId,
    @NotNull UUID reportAuthorUserId,
    UUID acceptingReviewerUserId,
    List<UUID> memberUserIds,
    String reason) {

  public AssignTeamRequest(
      UUID leadUserId, UUID reportAuthorUserId, List<UUID> memberUserIds, String reason) {
    this(leadUserId, reportAuthorUserId, null, memberUserIds, reason);
  }
}

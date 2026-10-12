package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.MaintenanceTeamMember;
import com.smartdroneinspection.maintenance.domain.enums.TeamMemberRole;
import java.time.Instant;
import java.util.UUID;

/** MF4-03/04: one team assignment, including the window it covered. */
public record TeamMemberResponse(
    UUID id,
    UUID engineerUserId,
    TeamMemberRole memberRole,
    Instant effectiveFrom,
    Instant effectiveUntil,
    UUID assignedByUserId,
    String reason,
    boolean active) {

  public static TeamMemberResponse from(MaintenanceTeamMember member) {
    return new TeamMemberResponse(
        member.getId(),
        member.getEngineerUserId(),
        member.getMemberRole(),
        member.getEffectiveFrom(),
        member.getEffectiveUntil(),
        member.getAssignedByUserId(),
        member.getReason(),
        member.isActive());
  }
}

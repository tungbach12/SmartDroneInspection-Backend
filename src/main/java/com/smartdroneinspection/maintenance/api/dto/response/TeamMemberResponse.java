package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.MaintenanceTeamMember;
import com.smartdroneinspection.maintenance.domain.enums.TeamMemberRole;
import java.time.Instant;
import java.util.UUID;

/** MF4-03/04: one team assignment, including the window it covered. */
public record TeamMemberResponse(
    UUID id,
    UUID engineerUserId,
    String engineerName,
    String engineerEmail,
    TeamMemberRole memberRole,
    Instant effectiveFrom,
    Instant effectiveUntil,
    UUID assignedByUserId,
    String reason,
    boolean active) {

  public static TeamMemberResponse from(MaintenanceTeamMember member) {
    return from(member, null, null);
  }

  public static TeamMemberResponse from(
      MaintenanceTeamMember member, String engineerName, String engineerEmail) {
    return new TeamMemberResponse(
        member.getId(),
        member.getEngineerUserId(),
        engineerName,
        engineerEmail,
        member.getMemberRole(),
        member.getEffectiveFrom(),
        member.getEffectiveUntil(),
        member.getAssignedByUserId(),
        member.getReason(),
        member.isActive());
  }
}

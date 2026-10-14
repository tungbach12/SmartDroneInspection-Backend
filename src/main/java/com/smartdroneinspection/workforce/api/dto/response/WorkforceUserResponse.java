package com.smartdroneinspection.workforce.api.dto.response;

import com.smartdroneinspection.users.UserAccess;
import java.util.Set;
import java.util.UUID;

/** Organization-scoped workforce user projection. Never carries a password or token. */
public record WorkforceUserResponse(
    UUID userId,
    String email,
    String fullName,
    Set<String> roles,
    String status,
    UUID organizationId) {

  public static WorkforceUserResponse from(UserAccess.WorkforceMember member) {
    return new WorkforceUserResponse(
        member.id(),
        member.email(),
        member.fullName(),
        member.roles(),
        member.status(),
        member.organizationId());
  }
}

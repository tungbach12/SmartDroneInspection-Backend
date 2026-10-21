package com.smartdroneinspection.users.api.dto.request;

import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record CreateUserRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String fullName,
    @Size(max = 32) String phoneNumber,
    @Size(max = 500) String avatarUrl,
    @NotNull ActorZone actorZone,
    UUID organizationId,
    @NotEmpty Set<UserRole> roles) {

  public CreateUserRequest(
      String email,
      String fullName,
      ActorZone actorZone,
      UUID organizationId,
      Set<UserRole> roles) {
    this(email, fullName, null, null, actorZone, organizationId, roles);
  }
}

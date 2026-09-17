package com.smartdroneinspection.users.api.dto.request;

import com.smartdroneinspection.users.domain.ActorZone;
import com.smartdroneinspection.users.domain.UserRole;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record UpdateRolesRequest(
    @NotNull ActorZone actorZone, UUID organizationId, @NotEmpty Set<UserRole> roles) {}

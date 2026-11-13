package com.smartdroneinspection.users.api.dto.request;

import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateRolesRequest(@NotNull ActorZone actorZone, @NotEmpty Set<UserRole> roles) {}

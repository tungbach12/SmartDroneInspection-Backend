package com.smartdroneinspection.users.api.dto.request;

import com.smartdroneinspection.users.domain.enums.UserRole;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UpdateRolesRequest(@NotEmpty Set<UserRole> roles) {}

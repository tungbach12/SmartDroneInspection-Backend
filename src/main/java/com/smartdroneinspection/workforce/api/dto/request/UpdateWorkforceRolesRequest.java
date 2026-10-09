package com.smartdroneinspection.workforce.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UpdateWorkforceRolesRequest(@NotEmpty Set<String> roles) {}

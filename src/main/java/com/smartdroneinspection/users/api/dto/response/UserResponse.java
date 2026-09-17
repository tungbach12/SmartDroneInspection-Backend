package com.smartdroneinspection.users.api.dto.response;

import java.util.List;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String fullName,
    List<String> roles,
    String actorZone,
    UUID organizationId) {}

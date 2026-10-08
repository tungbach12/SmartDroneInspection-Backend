package com.smartdroneinspection.users.api.dto.response;

import java.util.UUID;

public record OrganizationRegistrationResponse(
    UUID organizationId, String organizationName, String organizationCode, UserResponse user) {}

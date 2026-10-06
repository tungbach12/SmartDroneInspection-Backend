package com.smartdroneinspection.users.api.dto.response;

import java.util.UUID;

public record ProviderRegistrationResponse(UUID providerId, UUID userId, String activationLink) {}

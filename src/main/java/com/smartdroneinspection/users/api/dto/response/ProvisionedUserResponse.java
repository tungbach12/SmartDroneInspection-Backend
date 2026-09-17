package com.smartdroneinspection.users.api.dto.response;

public record ProvisionedUserResponse(UserResponse user, String temporaryPassword) {}

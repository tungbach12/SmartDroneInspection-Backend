package com.smartdroneinspection.users.api.dto.response;

public record SendPasswordResetOtpResponse(String challengeToken, long expiresInSeconds) {}

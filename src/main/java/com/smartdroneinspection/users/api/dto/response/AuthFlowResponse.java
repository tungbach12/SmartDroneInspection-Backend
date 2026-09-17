package com.smartdroneinspection.users.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthFlowResponse(
    AuthStep step,
    String accessToken,
    String refreshToken,
    long accessTokenExpiresInSeconds,
    UserResponse user) {}

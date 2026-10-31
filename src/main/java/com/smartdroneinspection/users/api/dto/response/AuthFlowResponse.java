package com.smartdroneinspection.users.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthFlowResponse(
    AuthStep step,
    String accessToken,
    String refreshToken,
    long accessTokenExpiresInSeconds,
    UserResponse user,
    String challengeToken,
    List<String> availableOtpChannels) {

  public AuthFlowResponse(
      AuthStep step,
      String accessToken,
      String refreshToken,
      long accessTokenExpiresInSeconds,
      UserResponse user) {
    this(step, accessToken, refreshToken, accessTokenExpiresInSeconds, user, null, null);
  }

  public AuthFlowResponse(
      AuthStep step,
      String accessToken,
      String refreshToken,
      long accessTokenExpiresInSeconds,
      UserResponse user,
      String challengeToken) {
    this(step, accessToken, refreshToken, accessTokenExpiresInSeconds, user, challengeToken, null);
  }
}

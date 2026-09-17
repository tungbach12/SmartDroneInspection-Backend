package com.smartdroneinspection.shared.exception;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public AuthException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static AuthException invalidCredentials() {
    return new AuthException(
        HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication failed.");
  }
}

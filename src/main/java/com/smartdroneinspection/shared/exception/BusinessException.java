package com.smartdroneinspection.shared.exception;

import org.springframework.http.HttpStatus;

/** An expected business failure that should be returned as a stable API problem. */
public class BusinessException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public BusinessException(HttpStatus status, String code, String message) {
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
}

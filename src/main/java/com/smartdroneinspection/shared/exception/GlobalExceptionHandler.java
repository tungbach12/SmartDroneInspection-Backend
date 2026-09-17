package com.smartdroneinspection.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(AuthException.class)
  public ProblemDetail handleAuthentication(AuthException ex, HttpServletRequest request) {
    return problem(ex.status(), ex.code(), ex.getMessage(), request);
  }

  @ExceptionHandler(MissingRequestCookieException.class)
  public ProblemDetail handleMissingRefreshCookie(
      MissingRequestCookieException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Authentication is required.", request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleBeanValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.", request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleValidation(IllegalArgumentException ex, HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage(), request);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
    String traceId = traceId(request);
    LOG.error("Unhandled request failure traceId={}", traceId, ex);
    var detail =
        problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "An unexpected error occurred.",
            request);
    detail.setProperty("traceId", traceId);
    return detail;
  }

  private ProblemDetail problem(
      HttpStatus status, String code, String message, HttpServletRequest request) {
    var detail = ProblemDetail.forStatusAndDetail(status, message);
    detail.setTitle(status.getReasonPhrase());
    detail.setInstance(URI.create(request.getRequestURI()));
    detail.setProperty("code", code);
    detail.setProperty("timestamp", Instant.now().toString());
    detail.setProperty("traceId", traceId(request));
    return detail;
  }

  private String traceId(HttpServletRequest request) {
    String value = request.getHeader("X-Correlation-ID");
    return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
  }
}

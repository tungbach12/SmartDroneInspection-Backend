package com.smartdroneinspection.shared.exception;

import java.net.URI;
import java.time.Instant;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleValidation(IllegalArgumentException ex) {
    var detail = ProblemDetail.forStatus(400);
    detail.setTitle("Bad Request");
    detail.setDetail(ex.getMessage());
    detail.setProperty("timestamp", Instant.now().toString());
    detail.setInstance(URI.create("/"));
    return detail;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    var detail = ProblemDetail.forStatus(500);
    detail.setTitle("Internal Server Error");
    detail.setInstance(URI.create("/"));
    return detail;
  }
}

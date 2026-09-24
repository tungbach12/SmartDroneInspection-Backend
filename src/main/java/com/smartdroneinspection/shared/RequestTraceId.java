package com.smartdroneinspection.shared;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public final class RequestTraceId {

  private RequestTraceId() {}

  public static String from(HttpServletRequest request) {
    String candidate = request.getHeader("X-Correlation-ID");
    if (candidate != null && candidate.matches("[A-Za-z0-9._-]{1,128}")) {
      return candidate;
    }
    return UUID.randomUUID().toString();
  }
}

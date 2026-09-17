package com.smartdroneinspection.users.api;

import jakarta.servlet.http.HttpServletRequest;

final class AuthHttpSupport {

  private AuthHttpSupport() {}

  static String clientIp(HttpServletRequest request) {
    return request.getRemoteAddr();
  }

  static String userAgent(HttpServletRequest request) {
    return request.getHeader("User-Agent");
  }

  static String correlationId(HttpServletRequest request) {
    return request.getHeader("X-Correlation-ID");
  }
}

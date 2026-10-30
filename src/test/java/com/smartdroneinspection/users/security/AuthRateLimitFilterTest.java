package com.smartdroneinspection.users.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

  @Test
  void limitsPublicPartnerLicenseUploadsPerClientIp() throws ServletException, IOException {
    AuthRateLimitFilter filter = new AuthRateLimitFilter();
    AtomicInteger continued = new AtomicInteger();

    for (int index = 0; index < 11; index++) {
      MockHttpServletRequest request =
          new MockHttpServletRequest(
              "POST", "/api/v1/inspector-partner-applications/license-document");
      request.setRemoteAddr("198.51.100.42");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(request, response, (req, res) -> continued.incrementAndGet());
      if (index == 10) {
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotNull();
      }
    }

    assertThat(continued).hasValue(10);
  }
}

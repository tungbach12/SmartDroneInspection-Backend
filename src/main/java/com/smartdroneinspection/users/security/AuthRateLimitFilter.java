package com.smartdroneinspection.users.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

  private static final int LIMIT = 10;
  private static final long WINDOW_SECONDS = 60;
  private static final int MAX_TRACKED_CLIENTS = 10_000;
  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
  private final AtomicInteger requests = new AtomicInteger();

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!"POST".equals(request.getMethod())) {
      return true;
    }
    String path = request.getRequestURI();
    return !(path.endsWith("/auth/login") || path.endsWith("/auth/password/setup"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long now = Instant.now().getEpochSecond();
    String key = request.getRemoteAddr();
    Window window =
        windows.compute(
            key,
            (ignored, existing) -> {
              if (existing == null || existing.startedAt() + WINDOW_SECONDS <= now) {
                return new Window(now, 1);
              }
              return new Window(existing.startedAt(), existing.count() + 1);
            });

    if ((requests.incrementAndGet() & 1023) == 0 || windows.size() > MAX_TRACKED_CLIENTS) {
      windows.entrySet().removeIf(entry -> entry.getValue().startedAt() + WINDOW_SECONDS <= now);
    }

    if (window.count() > LIMIT) {
      long retryAfter = Math.max(1, window.startedAt() + WINDOW_SECONDS - now);
      response.setStatus(429);
      response.setHeader("Retry-After", Long.toString(retryAfter));
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      response
          .getWriter()
          .write(
              "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"code\":\"RATE_LIMITED\"}");
      return;
    }
    filterChain.doFilter(request, response);
  }

  private record Window(long startedAt, int count) {}
}

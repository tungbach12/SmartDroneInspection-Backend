package com.smartdroneinspection.shared.config;

import com.smartdroneinspection.shared.RequestTraceId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final ObjectMapper objectMapper;

  public SecurityConfig(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Bean
  @Order(1)
  SecurityFilterChain browserAuthChain(
      HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
    http.securityMatcher("/api/v1/auth/**")
        .cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(this::writeUnauthorized)
                    .accessDeniedHandler(this::writeForbidden))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/password/setup",
                        "/api/v1/auth/refresh")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer.jwt(
                    jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable);
    return http.build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain apiChain(
      HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
    http.cors(Customizer.withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(this::writeUnauthorized)
                    .accessDeniedHandler(this::writeForbidden))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/api/v1/mobile/auth/**", "/actuator/health/**", "/error")
                    .permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer.jwt(
                    jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable);
    return http.build();
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    var authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("roles");
    authorities.setAuthorityPrefix("ROLE_");
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(properties.getAllowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "X-XSRF-TOKEN", "X-Correlation-ID"));
    configuration.setExposedHeaders(List.of("X-Correlation-ID"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }

  private void writeUnauthorized(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    writeSecurityProblem(request, response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
  }

  private void writeForbidden(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    writeSecurityProblem(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
  }

  private void writeSecurityProblem(
      HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code)
      throws IOException {
    String detail =
        status == HttpStatus.UNAUTHORIZED ? "Authentication is required." : "Access is denied.";
    var problem =
        java.util.Map.of(
            "type", "about:blank",
            "title", status.getReasonPhrase(),
            "status", status.value(),
            "detail", detail,
            "instance", URI.create(request.getRequestURI()).toString(),
            "code", code,
            "timestamp", Instant.now().toString(),
            "traceId", RequestTraceId.from(request));

    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setHeader("Cache-Control", "no-store");
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}

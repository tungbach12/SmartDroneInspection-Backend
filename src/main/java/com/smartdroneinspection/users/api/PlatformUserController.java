package com.smartdroneinspection.users.api;

import static com.smartdroneinspection.users.api.AuthHttpSupport.clientIp;
import static com.smartdroneinspection.users.api.AuthHttpSupport.correlationId;
import static com.smartdroneinspection.users.api.AuthHttpSupport.userAgent;

import com.smartdroneinspection.users.api.dto.request.CreateUserRequest;
import com.smartdroneinspection.users.api.dto.request.UpdateRolesRequest;
import com.smartdroneinspection.users.api.dto.request.UpdateStatusRequest;
import com.smartdroneinspection.users.api.dto.response.ProvisionedUserResponse;
import com.smartdroneinspection.users.api.dto.response.UserResponse;
import com.smartdroneinspection.users.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/users")
@PreAuthorize("hasRole('ADMIN')")
public class PlatformUserController {

  private final AdminUserService users;

  public PlatformUserController(AdminUserService users) {
    this.users = users;
  }

  @PostMapping
  public ResponseEntity<ProvisionedUserResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateUserRequest body,
      HttpServletRequest request) {
    return ResponseEntity.status(201)
        .cacheControl(CacheControl.noStore())
        .body(
            users.create(
                subject(jwt), body, clientIp(request), userAgent(request), correlationId(request)));
  }

  @PostMapping("/{userId}/reset-password")
  public ResponseEntity<ProvisionedUserResponse> resetPassword(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId, HttpServletRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            users.resetPassword(
                subject(jwt),
                userId,
                clientIp(request),
                userAgent(request),
                correlationId(request)));
  }

  @PutMapping("/{userId}/roles")
  public UserResponse updateRoles(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID userId,
      @Valid @RequestBody UpdateRolesRequest body,
      HttpServletRequest request) {
    return users.updateRoles(
        subject(jwt), userId, body, clientIp(request), userAgent(request), correlationId(request));
  }

  @PatchMapping("/{userId}/status")
  public UserResponse updateStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID userId,
      @Valid @RequestBody UpdateStatusRequest body,
      HttpServletRequest request) {
    return users.updateStatus(
        subject(jwt),
        userId,
        body.status(),
        clientIp(request),
        userAgent(request),
        correlationId(request));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}

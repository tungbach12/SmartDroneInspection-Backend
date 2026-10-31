package com.smartdroneinspection.users.api;

import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.users.api.dto.request.UpdateAiFallbackPreferenceRequest;
import com.smartdroneinspection.users.api.dto.request.UpdatePilotLicenseRequest;
import com.smartdroneinspection.users.api.dto.response.UserResponse;
import com.smartdroneinspection.users.service.AuthService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserProfileController {

  private final AuthService authService;

  public UserProfileController(AuthService authService) {
    this.authService = authService;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<UserResponse>> getProfile(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(authService.me(UUID.fromString(jwt.getSubject()))));
  }

  @PutMapping("/pilot-license")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ResponseEntity<ApiResponse<UserResponse>> updatePilotLicense(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdatePilotLicenseRequest body) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                authService.updatePilotLicense(UUID.fromString(jwt.getSubject()), body)));
  }

  @PutMapping("/ai-fallback-preference")
  @PreAuthorize("hasRole('INSPECTOR')")
  public ResponseEntity<ApiResponse<UserResponse>> updateAiFallbackPreference(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdateAiFallbackPreferenceRequest body) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                authService.updateVisionFallbackPreference(
                    UUID.fromString(jwt.getSubject()), body.visionFallbackEnabled())));
  }
}

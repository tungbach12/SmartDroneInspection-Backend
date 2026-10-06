package com.smartdroneinspection.users.api;

import static com.smartdroneinspection.users.api.AuthHttpSupport.clientIp;
import static com.smartdroneinspection.users.api.AuthHttpSupport.correlationId;
import static com.smartdroneinspection.users.api.AuthHttpSupport.userAgent;

import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.users.api.dto.request.ProviderStaffRequest;
import com.smartdroneinspection.users.api.dto.response.ProvisionedUserResponse;
import com.smartdroneinspection.users.service.ProviderUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/providers/users")
public class ProviderUsersController {

  private final ProviderUserService staff;

  public ProviderUsersController(ProviderUserService staff) {
    this.staff = staff;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ProvisionedUserResponse>> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ProviderStaffRequest body,
      HttpServletRequest request) {
    return ResponseEntity.status(201)
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                staff.createStaffUser(
                    UUID.fromString(jwt.getSubject()),
                    body,
                    clientIp(request),
                    userAgent(request),
                    correlationId(request))));
  }
}

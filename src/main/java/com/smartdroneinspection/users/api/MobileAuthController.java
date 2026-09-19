package com.smartdroneinspection.users.api;

import static com.smartdroneinspection.users.api.AuthHttpSupport.clientIp;
import static com.smartdroneinspection.users.api.AuthHttpSupport.correlationId;
import static com.smartdroneinspection.users.api.AuthHttpSupport.userAgent;

import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.users.api.dto.request.InitialPasswordChangeRequest;
import com.smartdroneinspection.users.api.dto.request.LoginRequest;
import com.smartdroneinspection.users.api.dto.request.RefreshRequest;
import com.smartdroneinspection.users.api.dto.response.AuthFlowResponse;
import com.smartdroneinspection.users.domain.enums.ClientType;
import com.smartdroneinspection.users.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mobile/auth")
public class MobileAuthController {

  private final AuthService auth;

  public MobileAuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/login")
  public ResponseEntity<AuthFlowResponse> login(
      @Valid @RequestBody LoginRequest body, HttpServletRequest request) {
    rejectBrowserOrigin(request);
    return response(
        auth.login(
            body.email(),
            body.password(),
            ClientType.MOBILE,
            clientIp(request),
            userAgent(request),
            correlationId(request)));
  }

  @PostMapping("/password/setup")
  public ResponseEntity<AuthFlowResponse> initialPassword(
      @Valid @RequestBody InitialPasswordChangeRequest body, HttpServletRequest request) {
    rejectBrowserOrigin(request);
    return response(
        auth.changeInitialPassword(
            body.email(),
            body.currentPassword(),
            body.password(),
            ClientType.MOBILE,
            clientIp(request),
            userAgent(request),
            correlationId(request)));
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthFlowResponse> refresh(
      @Valid @RequestBody RefreshRequest body, HttpServletRequest request) {
    rejectBrowserOrigin(request);
    return response(
        auth.refresh(
            body.refreshToken(),
            ClientType.MOBILE,
            clientIp(request),
            userAgent(request),
            correlationId(request)));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @Valid @RequestBody RefreshRequest body, HttpServletRequest request) {
    rejectBrowserOrigin(request);
    auth.logout(body.refreshToken(), clientIp(request), userAgent(request), correlationId(request));
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  private ResponseEntity<AuthFlowResponse> response(AuthService.AuthResult result) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result.response());
  }

  private void rejectBrowserOrigin(HttpServletRequest request) {
    String origin = request.getHeader("Origin");
    if (origin != null && !origin.isBlank()) {
      throw new AuthException(
          HttpStatus.FORBIDDEN, "MOBILE_ENDPOINT_REQUIRED", "This endpoint is for native clients.");
    }
  }
}

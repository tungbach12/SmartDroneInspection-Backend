package com.smartdroneinspection.users.api;

import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.users.api.dto.request.ApproveProspectiveCustomerRequest;
import com.smartdroneinspection.users.api.dto.request.RejectProspectiveCustomerRequest;
import com.smartdroneinspection.users.api.dto.response.ProspectiveCustomerResponse;
import com.smartdroneinspection.users.service.ProspectiveCustomerReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/customer-airspace-reviews")
@PreAuthorize("hasRole('ADMIN')")
public class AirspaceReviewController {

  private final ProspectiveCustomerReviewService reviews;

  public AirspaceReviewController(ProspectiveCustomerReviewService reviews) {
    this.reviews = reviews;
  }

  @GetMapping("/pending")
  public ApiResponse<List<ProspectiveCustomerResponse>> pending(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(reviews.pending(subject(jwt)));
  }

  @PostMapping("/{registrationId}/approve")
  public ApiResponse<ProspectiveCustomerResponse> approve(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID registrationId,
      @Valid @RequestBody ApproveProspectiveCustomerRequest body,
      HttpServletRequest request) {
    return ApiResponse.success(
        reviews.approve(
            subject(jwt),
            registrationId,
            body,
            request.getRemoteAddr(),
            request.getHeader("User-Agent"),
            request.getHeader("X-Correlation-ID")));
  }

  @PostMapping("/{registrationId}/reject")
  public ApiResponse<ProspectiveCustomerResponse> reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID registrationId,
      @Valid @RequestBody RejectProspectiveCustomerRequest body,
      HttpServletRequest request) {
    return ApiResponse.success(
        reviews.reject(
            subject(jwt),
            registrationId,
            body,
            request.getRemoteAddr(),
            request.getHeader("User-Agent"),
            request.getHeader("X-Correlation-ID")));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}

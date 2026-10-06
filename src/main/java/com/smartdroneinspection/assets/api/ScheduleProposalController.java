package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.request.ReviewProposalRequest;
import com.smartdroneinspection.assets.api.dto.response.ScheduleProposalResponse;
import com.smartdroneinspection.assets.service.ScheduleProposalService;
import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedule-proposals")
public class ScheduleProposalController {

  private final ScheduleProposalService proposals;
  private final UserAccess userAccess;

  public ScheduleProposalController(ScheduleProposalService proposals, UserAccess userAccess) {
    this.proposals = proposals;
    this.userAccess = userAccess;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('CLIENT', 'PROVIDER_MANAGER', 'PLATFORM_ADMIN')")
  public ApiResponse<List<ScheduleProposalResponse>> list(
      @AuthenticationPrincipal Jwt jwt, @RequestParam UUID assetId) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));
    if (actor.hasRole("PROVIDER_MANAGER") || actor.hasRole("PLATFORM_ADMIN")) {
      return ApiResponse.success(proposals.listForManager(assetId));
    }
    return ApiResponse.success(proposals.listForClient(actorId, assetId));
  }

  @PostMapping("/{proposalId}/review")
  @PreAuthorize("hasRole('PROVIDER_MANAGER')")
  public ApiResponse<ScheduleProposalResponse> review(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID proposalId,
      @Valid @RequestBody ReviewProposalRequest request) {
    return ApiResponse.success(
        proposals.review(UUID.fromString(jwt.getSubject()), proposalId, request));
  }

  @PostMapping("/{proposalId}/select")
  @PreAuthorize("hasRole('CLIENT')")
  public ApiResponse<ScheduleProposalResponse> select(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID proposalId) {
    return ApiResponse.success(proposals.select(UUID.fromString(jwt.getSubject()), proposalId));
  }
}

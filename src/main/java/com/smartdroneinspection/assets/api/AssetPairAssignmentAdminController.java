package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.request.ActivateAssetPairAssignmentRequest;
import com.smartdroneinspection.assets.api.dto.request.CreateAssetPairAssignmentRequest;
import com.smartdroneinspection.assets.api.dto.response.AssetPairAdminResponse;
import com.smartdroneinspection.assets.service.AssetPairAdminService;
import com.smartdroneinspection.shared.api.ApiResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assets/{assetId}/pair-assignments")
@PreAuthorize("hasRole('ORG_MANAGER')")
public class AssetPairAssignmentAdminController {

  private final AssetPairAdminService pairAssignments;

  public AssetPairAssignmentAdminController(AssetPairAdminService pairAssignments) {
    this.pairAssignments = pairAssignments;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<AssetPairAdminResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID assetId,
      @Valid @RequestBody CreateAssetPairAssignmentRequest request) {
    return ApiResponse.success(pairAssignments.create(subject(jwt), assetId, request));
  }

  @GetMapping
  public ApiResponse<List<AssetPairAdminResponse>> list(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID assetId) {
    return ApiResponse.success(pairAssignments.list(subject(jwt), assetId));
  }

  @PostMapping("/{pairId}/activate")
  public ApiResponse<AssetPairAdminResponse> activate(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID assetId,
      @PathVariable UUID pairId,
      @Valid @RequestBody ActivateAssetPairAssignmentRequest request) {
    return ApiResponse.success(pairAssignments.activate(subject(jwt), assetId, pairId, request));
  }

  private static UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}

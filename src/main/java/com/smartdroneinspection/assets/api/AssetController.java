package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.request.CreateAssetRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateAssetRequest;
import com.smartdroneinspection.assets.api.dto.response.AssetPageResponse;
import com.smartdroneinspection.assets.api.dto.response.AssetResponse;
import com.smartdroneinspection.assets.service.AssetService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

  private final AssetService assets;

  public AssetController(AssetService assets) {
    this.assets = assets;
  }

  @PostMapping
  @PreAuthorize("hasRole('CLIENT')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<AssetResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAssetRequest request) {
    return ApiResponse.success(assets.create(subject(jwt), request));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
  public ApiResponse<AssetPageResponse> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String search) {
    return ApiResponse.success(assets.list(subject(jwt), page, pageSize, search));
  }

  @GetMapping("/{assetId}")
  @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
  public ApiResponse<AssetResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID assetId) {
    return ApiResponse.success(assets.get(subject(jwt), assetId));
  }

  @PutMapping("/{assetId}")
  @PreAuthorize("hasRole('CLIENT')")
  public ApiResponse<AssetResponse> update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID assetId,
      @Valid @RequestBody UpdateAssetRequest request) {
    return ApiResponse.success(assets.update(subject(jwt), assetId, request));
  }

  private static UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}

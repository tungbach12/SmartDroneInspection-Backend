package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.CreateAssetRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateAssetRequest;
import com.smartdroneinspection.assets.api.dto.response.AssetPageResponse;
import com.smartdroneinspection.assets.api.dto.response.AssetResponse;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService {

  private final AssetRepository assets;
  private final AssetCategoryRepository categories;
  private final UserAccess userAccess;

  public AssetService(
      AssetRepository assets, AssetCategoryRepository categories, UserAccess userAccess) {
    this.assets = assets;
    this.categories = categories;
    this.userAccess = userAccess;
  }

  @Transactional
  public AssetResponse create(UUID actorId, CreateAssetRequest request) {
    UUID organizationId = requireOrganization(requireActive(actorId));
    AssetCategory category =
        categories
            .findById(request.categoryId())
            .filter(AssetCategory::isActive)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_FAILED",
                        "Unknown or inactive category"));
    if (assets.existsByOrganizationIdAndCode(organizationId, normalize(request.code()))) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "DUPLICATE_CODE", "Asset code already exists");
    }
    Asset asset =
        new Asset(
            organizationId,
            category.getId(),
            request.code(),
            request.name(),
            request.description(),
            request.locationText(),
            request.latitude(),
            request.longitude(),
            request.ownershipInformation(),
            actorId);
    return toResponse(assets.saveAndFlush(asset));
  }

  @Transactional(readOnly = true)
  public AssetPageResponse list(UUID actorId, int page, int pageSize, String search) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    UUID organizationId = requireOrganization(actor);
    int safePage = Math.max(page, 1);
    int safePageSize = Math.min(Math.max(pageSize, 1), 100);
    PageRequest pageRequest = PageRequest.of(safePage - 1, safePageSize, Sort.by("name"));
    Page<Asset> result =
        search == null || search.isBlank()
            ? assets.findByOrganizationId(organizationId, pageRequest)
            : assets.searchByOrganizationId(organizationId, search.trim(), pageRequest);
    return new AssetPageResponse(
        result.getContent().stream().map(this::toResponse).toList(),
        safePage,
        safePageSize,
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public AssetResponse get(UUID actorId, UUID assetId) {
    return toResponse(requireOwnedAsset(actorId, assetId));
  }

  @Transactional
  public AssetResponse update(UUID actorId, UUID assetId, UpdateAssetRequest request) {
    Asset asset = requireOwnedAsset(actorId, assetId);
    if (asset.getStatus() == AssetStatus.RETIRED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INVALID_STATE", "Retired assets cannot be edited");
    }
    asset.update(
        request.name(),
        request.description(),
        request.locationText(),
        request.latitude(),
        request.longitude(),
        request.ownershipInformation());
    return toResponse(assets.saveAndFlush(asset));
  }

  private Asset requireOwnedAsset(UUID actorId, UUID assetId) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    UUID organizationId = requireOrganization(actor);
    return assets
        .findByIdAndOrganizationId(assetId, organizationId)
        .orElseThrow(
            () -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Asset not found"));
  }

  private UUID requireOrganization(UserAccess.ActiveUser actor) {
    if (actor.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Actor has no organization scope");
    }
    return actor.organizationId();
  }

  private UserAccess.ActiveUser requireActive(UUID actorId) {
    return userAccess
        .findActiveUser(actorId)
        .orElseThrow(
            () -> new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));
  }

  private static String normalize(String code) {
    return code.trim().toUpperCase(Locale.ROOT);
  }

  private AssetResponse toResponse(Asset asset) {
    return new AssetResponse(
        asset.getId(),
        asset.getCode(),
        asset.getName(),
        asset.getDescription(),
        asset.getLocationText(),
        asset.getLatitude(),
        asset.getLongitude(),
        asset.getStatus().name(),
        asset.getCategoryId(),
        asset.getCreatedAt());
  }
}

package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.CreateAssetRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateAssetRequest;
import com.smartdroneinspection.assets.api.dto.response.AirspaceEvaluationResponse;
import com.smartdroneinspection.assets.api.dto.response.AirspaceZoneResponse;
import com.smartdroneinspection.assets.api.dto.response.AssetPageResponse;
import com.smartdroneinspection.assets.api.dto.response.AssetResponse;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.InspectionItem;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.InspectionItemRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final InspectionItemRepository inspectionItems;
  private final UserAccess userAccess;
  private final AirspaceAdvisoryService airspaceAdvisory;

  public AssetService(
      AssetRepository assets,
      AssetCategoryRepository categories,
      UserAccess userAccess,
      AirspaceAdvisoryService airspaceAdvisory) {
    this(assets, categories, null, userAccess, airspaceAdvisory);
  }

  @Autowired
  public AssetService(
      AssetRepository assets,
      AssetCategoryRepository categories,
      InspectionItemRepository inspectionItems,
      UserAccess userAccess,
      AirspaceAdvisoryService airspaceAdvisory) {
    this.assets = assets;
    this.categories = categories;
    this.inspectionItems = inspectionItems;
    this.userAccess = userAccess;
    this.airspaceAdvisory = airspaceAdvisory;
  }

  @Transactional
  public AssetResponse create(UUID actorId, CreateAssetRequest request) {
    UUID organizationId = requireOrganization(requireActive(actorId));

    if (request.latitude() != null && request.longitude() != null) {
      validateNoFlyZone(request.latitude(), request.longitude());
    }

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

    List<String> defaultCategoryItems =
        inspectionItems == null
            ? List.of()
            : inspectionItems
                .findByCategoryIdOrderBySortOrderAscCreatedAtAsc(category.getId())
                .stream()
                .filter(InspectionItem::isActive)
                .map(InspectionItem::getLabel)
                .toList();
    List<String> initialInspectionItems =
        !defaultCategoryItems.isEmpty()
            ? defaultCategoryItems
            : (request.inspectionItems() != null ? request.inspectionItems() : List.of());

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
            initialInspectionItems,
            actorId);

    Asset savedAsset = assets.saveAndFlush(asset);
    return toResponse(savedAsset);
  }

  @Transactional(readOnly = true)
  public AssetPageResponse list(UUID actorId, int page, int pageSize, String search) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    int safePage = Math.max(page, 1);
    int safePageSize = Math.min(Math.max(pageSize, 1), 100);
    PageRequest pageRequest = PageRequest.of(safePage - 1, safePageSize, Sort.by("name"));

    Page<Asset> result;
    if (actor.hasRole("ADMIN") || actor.hasRole("MANAGER")) {
      if (actor.organizationId() != null) {
        result =
            search == null || search.isBlank()
                ? assets.findByOrganizationId(actor.organizationId(), pageRequest)
                : assets.searchByOrganizationId(actor.organizationId(), search.trim(), pageRequest);
      } else {
        result =
            search == null || search.isBlank()
                ? assets.findAll(pageRequest)
                : assets.searchAll(search.trim(), pageRequest);
      }
    } else {
      UUID organizationId = requireOrganization(actor);
      result =
          search == null || search.isBlank()
              ? assets.findByOrganizationId(organizationId, pageRequest)
              : assets.searchByOrganizationId(organizationId, search.trim(), pageRequest);
    }
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
    if (request.latitude() != null || request.longitude() != null) {
      java.math.BigDecimal nextLat =
          request.latitude() != null ? request.latitude() : asset.getLatitude();
      java.math.BigDecimal nextLon =
          request.longitude() != null ? request.longitude() : asset.getLongitude();
      if (nextLat != null && nextLon != null) {
        validateNoFlyZone(nextLat, nextLon);
      }
    }
    asset.update(
        request.name(),
        request.description(),
        request.locationText(),
        request.latitude(),
        request.longitude(),
        request.ownershipInformation(),
        request.inspectionItems());
    Asset saved = assets.saveAndFlush(asset);
    return toResponse(saved);
  }

  @Transactional
  public AssetResponse updateInspectionItems(
      UUID actorId, UUID assetId, List<String> requestedItems) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    Asset asset;
    if (actor.hasRole("ADMIN") || actor.hasRole("MANAGER")) {
      asset =
          assets
              .findById(assetId)
              .orElseThrow(
                  () ->
                      new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Asset not found"));
    } else {
      UUID organizationId = requireOrganization(actor);
      asset =
          assets
              .findByIdAndOrganizationId(assetId, organizationId)
              .orElseThrow(
                  () ->
                      new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Asset not found"));
    }
    if (asset.getStatus() == AssetStatus.RETIRED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INVALID_STATE", "Retired assets cannot be edited");
    }
    List<String> sanitized =
        requestedItems == null
            ? List.of()
            : requestedItems.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    asset.setInspectionItems(sanitized);
    Asset saved = assets.saveAndFlush(asset);
    return toResponse(saved);
  }

  private void validateNoFlyZone(java.math.BigDecimal latitude, java.math.BigDecimal longitude) {
    if (airspaceAdvisory == null) {
      return;
    }
    AirspaceEvaluationResponse airspace = airspaceAdvisory.evaluate(latitude, longitude);
    if ("NO_FLY_ZONE".equals(airspace.classification())) {
      String zoneName =
          airspace.matchingZones().stream()
              .filter(z -> "NO_FLY_ZONE".equals(z.zoneType()))
              .map(AirspaceZoneResponse::name)
              .findFirst()
              .orElse("Vùng cấm bay tuyệt đối");
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "NO_FLY_ZONE_RESTRICTION",
          "Tọa độ công trình nằm trong "
              + zoneName
              + " (Khu vực cấm bay tuyệt đối theo Quyết định 18/2020/QĐ-TTg và Nghị định 288/2025/NĐ-CP). Hệ thống không cho phép tạo hoặc di dời tài sản kiểm định drone vào khu vực cấm bay.");
    }
  }

  @Transactional
  public AssetResponse retire(UUID actorId, UUID assetId) {
    Asset asset = requireOwnedAsset(actorId, assetId);
    if (asset.getStatus() == AssetStatus.RETIRED) {
      throw new BusinessException(HttpStatus.CONFLICT, "INVALID_STATE", "Asset is already retired");
    }
    asset.retire();
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
    List<String> items = asset.getInspectionItems();
    if ((items == null || items.isEmpty())
        && inspectionItems != null
        && asset.getCategoryId() != null) {
      items =
          inspectionItems
              .findByCategoryIdOrderBySortOrderAscCreatedAtAsc(asset.getCategoryId())
              .stream()
              .filter(InspectionItem::isActive)
              .map(InspectionItem::getLabel)
              .toList();
    }
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
        asset.getCreatedAt(),
        items);
  }
}

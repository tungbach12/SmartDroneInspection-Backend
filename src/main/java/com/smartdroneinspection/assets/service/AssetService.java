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
import com.smartdroneinspection.shared.api.AssetInspectionUsagePort;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
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
  private final AssetInspectionUsagePort assetInspectionUsage;

  public AssetService(
      AssetRepository assets,
      AssetCategoryRepository categories,
      UserAccess userAccess,
      AirspaceAdvisoryService airspaceAdvisory) {
    this(assets, categories, null, userAccess, airspaceAdvisory, null);
  }

  public AssetService(
      AssetRepository assets,
      AssetCategoryRepository categories,
      InspectionItemRepository inspectionItems,
      UserAccess userAccess,
      AirspaceAdvisoryService airspaceAdvisory) {
    this(assets, categories, inspectionItems, userAccess, airspaceAdvisory, null);
  }

  @Autowired
  public AssetService(
      AssetRepository assets,
      AssetCategoryRepository categories,
      InspectionItemRepository inspectionItems,
      UserAccess userAccess,
      AirspaceAdvisoryService airspaceAdvisory,
      AssetInspectionUsagePort assetInspectionUsage) {
    this.assets = assets;
    this.categories = categories;
    this.inspectionItems = inspectionItems;
    this.userAccess = userAccess;
    this.airspaceAdvisory = airspaceAdvisory;
    this.assetInspectionUsage = assetInspectionUsage;
  }

  @Transactional
  public AssetResponse create(UUID actorId, CreateAssetRequest request) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    UUID ownerCustomerId = requireOwnerCustomer(actor);

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
    if (assets.existsByOwnerCustomerIdAndCode(ownerCustomerId, normalize(request.code()))) {
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
            actor.ownerCustomerId(),
            category.getId(),
            request.code(),
            request.name(),
            request.description(),
            request.locationText(),
            request.latitude(),
            request.longitude(),
            request.ownershipInformation(),
            initialInspectionItems,
            request.structureHeightMeters(),
            request.footprintAreaSquareMeters(),
            actorId);
    asset.markCustomerOwned(actorId);

    Asset savedAsset = assets.saveAndFlush(asset);
    return toResponse(savedAsset);
  }

  @Transactional(readOnly = true)
  public AssetPageResponse list(UUID actorId, int page, int pageSize, String search) {
    return list(actorId, page, pageSize, search, null);
  }

  @Transactional(readOnly = true)
  public AssetPageResponse list(
      UUID actorId, int page, int pageSize, String search, AssetStatus status) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    int safePage = Math.max(page, 1);
    int safePageSize = Math.min(Math.max(pageSize, 1), 100);
    PageRequest pageRequest = PageRequest.of(safePage - 1, safePageSize, Sort.by("name"));
    String trimmedSearch = search != null && !search.isBlank() ? search.trim() : null;

    Page<Asset> result;
    long allCount;
    long activeCount;
    long checkpointsConfiguredCount;
    if (actor.hasRole("ADMIN") || actor.hasRole("MANAGER")) {
      result = queryAllAssets(status, trimmedSearch, pageRequest);
      allCount = assets.count();
      activeCount = assets.countByStatus(AssetStatus.ACTIVE);
      checkpointsConfiguredCount = assets.countAllWithCheckpoints();
    } else {
      UUID ownerCustomerId = requireOwnerCustomer(actor);
      result = queryCustomerAssets(ownerCustomerId, status, trimmedSearch, pageRequest);
      allCount = assets.countByOwnerCustomerId(ownerCustomerId);
      activeCount = assets.countByOwnerCustomerIdAndStatus(ownerCustomerId, AssetStatus.ACTIVE);
      checkpointsConfiguredCount = assets.countByOwnerCustomerIdWithCheckpoints(ownerCustomerId);
    }
    List<Asset> pageAssets = result.getContent();
    Set<UUID> lockedAssetIds =
        assetInspectionUsage == null
            ? Set.of()
            : assetInspectionUsage.inspectedAssetIds(
                pageAssets.stream().map(Asset::getId).toList());
    return new AssetPageResponse(
        pageAssets.stream()
            .map(asset -> toResponse(asset, lockedAssetIds.contains(asset.getId())))
            .toList(),
        safePage,
        safePageSize,
        result.getTotalElements(),
        result.getTotalPages(),
        allCount,
        activeCount,
        checkpointsConfiguredCount);
  }

  private Page<Asset> queryAllAssets(AssetStatus status, String search, PageRequest pageRequest) {
    if (status == null) {
      return search == null ? assets.findAll(pageRequest) : assets.searchAll(search, pageRequest);
    }
    return search == null
        ? assets.findByStatus(status, pageRequest)
        : assets.searchAllByStatus(status, search, pageRequest);
  }

  private Page<Asset> queryCustomerAssets(
      UUID ownerCustomerId, AssetStatus status, String search, PageRequest pageRequest) {
    if (status == null) {
      return search == null
          ? assets.findByOwnerCustomerId(ownerCustomerId, pageRequest)
          : assets.searchByOwnerCustomerId(ownerCustomerId, search, pageRequest);
    }
    return search == null
        ? assets.findByOwnerCustomerIdAndStatus(ownerCustomerId, status, pageRequest)
        : assets.searchByOwnerCustomerIdAndStatus(ownerCustomerId, status, search, pageRequest);
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
    BigDecimal proposedLatitude =
        request.clearCoordinates()
            ? null
            : request.latitude() != null ? request.latitude() : asset.getLatitude();
    BigDecimal proposedLongitude =
        request.clearCoordinates()
            ? null
            : request.longitude() != null ? request.longitude() : asset.getLongitude();
    if (proposedLatitude != null && proposedLongitude != null) {
      validateNoFlyZone(proposedLatitude, proposedLongitude);
    }
    asset.update(
        request.name(),
        request.description(),
        request.locationText(),
        request.latitude(),
        request.longitude(),
        request.ownershipInformation(),
        request.inspectionItems(),
        request.structureHeightMeters(),
        request.footprintAreaSquareMeters(),
        request.clearCoordinates(),
        request.clearStructureHeight(),
        request.clearFootprintArea(),
        request.clearDescription());
    Asset saved = assets.saveAndFlush(asset);
    return toResponse(saved);
  }

  @Transactional
  public AssetResponse updateInspectionItems(
      UUID actorId, UUID assetId, List<String> requestedItems) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (!actor.hasRole("CUSTOMER")) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only the owning customer can change asset inspection checkpoints");
    }
    UUID ownerCustomerId = requireOwnerCustomer(actor);
    Asset asset =
        assets
            .findByIdAndOwnerCustomerId(assetId, ownerCustomerId)
            .orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Asset not found"));
    if (asset.getStatus() == AssetStatus.RETIRED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INVALID_STATE", "Retired assets cannot be edited");
    }
    if (assetInspectionUsage != null && assetInspectionUsage.hasInspection(assetId)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INSPECTION_HISTORY_EXISTS",
          "Inspection checkpoints are locked once an inspection exists for this asset. Create a new request for a new checkpoint set.");
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
    if (actor.hasRole("ADMIN") || actor.hasRole("MANAGER")) {
      return assets
          .findById(assetId)
          .orElseThrow(
              () -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Asset not found"));
    }
    UUID ownerCustomerId = requireOwnerCustomer(actor);
    return assets
        .findByIdAndOwnerCustomerId(assetId, ownerCustomerId)
        .orElseThrow(
            () -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Asset not found"));
  }

  private UUID requireOwnerCustomer(UserAccess.ActiveUser actor) {
    UUID ownerCustomerId = actor.ownerCustomerId();
    if (ownerCustomerId == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Actor has no customer ownership scope");
    }
    return ownerCustomerId;
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
    boolean checkpointsLocked =
        assetInspectionUsage != null && assetInspectionUsage.hasInspection(asset.getId());
    return toResponse(asset, checkpointsLocked);
  }

  private AssetResponse toResponse(Asset asset, boolean checkpointsLocked) {
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
        items,
        asset.getStructureHeightMeters(),
        asset.getFootprintAreaSquareMeters(),
        checkpointsLocked);
  }
}

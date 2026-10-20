package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.CreateAssetRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateAssetRequest;
import com.smartdroneinspection.assets.api.dto.response.AssetDefaultPairResponse;
import com.smartdroneinspection.assets.api.dto.response.AssetPageResponse;
import com.smartdroneinspection.assets.api.dto.response.AssetResponse;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.domain.AssetPairAssignment;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetPairAssignmentRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.SubscriptionEntitlement;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
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
  private final AssetPairAssignmentRepository pairings;
  private final DroneRepository drones;
  private final UserAccess userAccess;
  private final SubscriptionEntitlement entitlement;

  public AssetService(
      AssetRepository assets,
      AssetCategoryRepository categories,
      AssetPairAssignmentRepository pairings,
      DroneRepository drones,
      UserAccess userAccess,
      SubscriptionEntitlement entitlement) {
    this.assets = assets;
    this.categories = categories;
    this.pairings = pairings;
    this.drones = drones;
    this.userAccess = userAccess;
    this.entitlement = entitlement;
  }

  @Transactional
  public AssetResponse create(UUID actorId, CreateAssetRequest request) {
    UUID organizationId = requireOrganization(requireActive(actorId));
    entitlement.requireActive(organizationId, Instant.now());
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

    UserAccess.ActiveUser inspector = null;
    Drone drone = null;
    if (request.defaultInspectorId() != null || request.defaultDroneId() != null) {
      if (request.defaultInspectorId() == null || request.defaultDroneId() == null) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "PAIR_INCOMPLETE",
            "Both default inspector and drone must be selected together.");
      }
      inspector =
          userAccess
              .findActiveUser(request.defaultInspectorId())
              .filter(u -> organizationId.equals(u.organizationId()) && u.hasRole(Roles.INSPECTOR))
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.BAD_REQUEST,
                          "INVALID_INSPECTOR",
                          "Assigned inspector must be active in the organization."));

      drone =
          drones
              .findByIdAndOrganizationId(request.defaultDroneId(), organizationId)
              .filter(d -> d.getServiceability() != DroneServiceability.RETIRED)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.BAD_REQUEST,
                          "INVALID_DRONE",
                          "Assigned drone must be active in the organization."));
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
    Asset savedAsset = assets.saveAndFlush(asset);

    AssetDefaultPairResponse pairResponse = null;
    if (inspector != null && drone != null) {
      AssetPairAssignment pairing =
          new AssetPairAssignment(
              organizationId,
              savedAsset.getId(),
              inspector.id(),
              drone.getId(),
              Instant.now(),
              actorId);
      pairing.activate("Default operational assignment", null);
      AssetPairAssignment savedPairing = pairings.saveAndFlush(pairing);
      pairResponse =
          new AssetDefaultPairResponse(
              savedPairing.getId(),
              inspector.id(),
              inspector.fullName(),
              drone.getId(),
              drone.getSerialNumber(),
              drone.getModel(),
              savedPairing.getStatus().name(),
              savedPairing.getValidFrom(),
              savedPairing.getValidUntil());
    }

    return toResponse(savedAsset, pairResponse);
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

  @Transactional(readOnly = true)
  public AssetDefaultPairResponse getActivePair(UUID actorId, UUID assetId) {
    Asset asset = requireOwnedAsset(actorId, assetId);
    return pairings
        .findByOrganizationIdAndAssetIdAndStatus(
            asset.getOrganizationId(), asset.getId(), AssetPairAssignmentStatus.ACTIVE)
        .map(this::toPairResponse)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "PAIR_NOT_FOUND", "No active pair assigned to asset"));
  }

  @Transactional
  public AssetDefaultPairResponse reassignPair(
      UUID actorId, UUID assetId, UUID inspectorId, UUID droneId, String reason) {
    Asset asset = requireOwnedAsset(actorId, assetId);
    UUID organizationId = asset.getOrganizationId();

    UserAccess.ActiveUser inspector =
        userAccess
            .findActiveUser(inspectorId)
            .filter(u -> organizationId.equals(u.organizationId()) && u.hasRole(Roles.INSPECTOR))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_INSPECTOR",
                        "Assigned inspector must be active in the organization."));

    Drone drone =
        drones
            .findByIdAndOrganizationId(droneId, organizationId)
            .filter(d -> d.getServiceability() != DroneServiceability.RETIRED)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_DRONE",
                        "Assigned drone must be active in the organization."));

    pairings
        .findByOrganizationIdAndAssetIdAndStatus(
            organizationId, asset.getId(), AssetPairAssignmentStatus.ACTIVE)
        .ifPresent(
            existing -> {
              existing.supersede(
                  reason == null || reason.isBlank()
                      ? "Reassigned by administrator"
                      : reason.trim());
              pairings.saveAndFlush(existing);
            });

    AssetPairAssignment newPairing =
        new AssetPairAssignment(
            organizationId, asset.getId(), inspector.id(), drone.getId(), Instant.now(), actorId);
    newPairing.activate(reason, null);
    return toPairResponse(pairings.saveAndFlush(newPairing));
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
    AssetDefaultPairResponse pairResponse =
        pairings
            .findByOrganizationIdAndAssetIdAndStatus(
                asset.getOrganizationId(), asset.getId(), AssetPairAssignmentStatus.ACTIVE)
            .map(this::toPairResponse)
            .orElse(null);
    return toResponse(asset, pairResponse);
  }

  private AssetDefaultPairResponse toPairResponse(AssetPairAssignment pairing) {
    String inspectorName =
        userAccess
            .findActiveUser(pairing.getInspectorUserId())
            .map(UserAccess.ActiveUser::fullName)
            .orElse(null);
    Drone drone =
        drones
            .findByIdAndOrganizationId(pairing.getDroneId(), pairing.getOrganizationId())
            .orElse(null);
    return new AssetDefaultPairResponse(
        pairing.getId(),
        pairing.getInspectorUserId(),
        inspectorName,
        pairing.getDroneId(),
        drone == null ? null : drone.getSerialNumber(),
        drone == null ? null : drone.getModel(),
        pairing.getStatus().name(),
        pairing.getValidFrom(),
        pairing.getValidUntil());
  }

  private AssetResponse toResponse(Asset asset, AssetDefaultPairResponse pairResponse) {
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
        pairResponse);
  }
}

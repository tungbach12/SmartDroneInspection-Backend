package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.ActivateAssetPairAssignmentRequest;
import com.smartdroneinspection.assets.api.dto.request.CreateAssetPairAssignmentRequest;
import com.smartdroneinspection.assets.api.dto.response.AssetPairAdminResponse;
import com.smartdroneinspection.assets.domain.AssetPairAssignment;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus;
import com.smartdroneinspection.assets.repository.AssetPairAssignmentRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.SubscriptionEntitlement;
import com.smartdroneinspection.users.UserAccess;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetPairAdminService {

  private final AssetPairAssignmentRepository pairings;
  private final AssetRepository assets;
  private final DroneRepository drones;
  private final UserAccess userAccess;
  private final SubscriptionEntitlement entitlement;
  private final JdbcTemplate jdbcTemplate;

  public AssetPairAdminService(
      AssetPairAssignmentRepository pairings,
      AssetRepository assets,
      DroneRepository drones,
      UserAccess userAccess,
      SubscriptionEntitlement entitlement,
      JdbcTemplate jdbcTemplate) {
    this.pairings = pairings;
    this.assets = assets;
    this.drones = drones;
    this.userAccess = userAccess;
    this.entitlement = entitlement;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public AssetPairAdminResponse create(
      UUID actorId, UUID assetId, CreateAssetPairAssignmentRequest request) {
    UserAccess.ActiveUser actor = requireOrgAdmin(actorId);
    UUID organizationId = actor.organizationId();
    entitlement.requireActive(organizationId, Instant.now());
    var asset =
        assets.findByIdAndOrganizationId(assetId, organizationId).orElseThrow(this::assetNotFound);
    if (asset.getStatus() != com.smartdroneinspection.assets.domain.enums.AssetStatus.ACTIVE) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "ASSET_NOT_ASSIGNABLE", "Only active assets can receive a pair");
    }
    UserAccess.ActiveUser inspector =
        userAccess
            .findActiveUser(request.inspectorUserId())
            .filter(user -> organizationId.equals(user.organizationId()))
            .filter(user -> user.hasRole("INSPECTOR"))
            .orElseThrow(this::inspectorNotAssignable);
    Drone drone =
        drones
            .findByIdAndOrganizationId(request.droneId(), organizationId)
            .orElseThrow(this::droneNotFound);
    if (!drone.isEligibleForAssignment()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "DRONE_NOT_ASSIGNABLE", "Drone is not available for assignment");
    }

    AssetPairAssignment assignment =
        new AssetPairAssignment(
            organizationId, assetId, inspector.id(), drone.getId(), request.validFrom(), actorId);
    try {
      assignment.prepareDraft(request.reason(), request.validUntil());
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", exception.getMessage());
    }
    assignment = pairings.saveAndFlush(assignment);
    recordAudit(
        organizationId,
        actorId,
        assignment,
        null,
        assignment.getStatus().name(),
        request.reason().trim());
    return AssetPairAdminResponse.from(assignment);
  }

  @Transactional(readOnly = true)
  public List<AssetPairAdminResponse> list(UUID actorId, UUID assetId) {
    UserAccess.ActiveUser actor = requireOrgAdmin(actorId);
    UUID organizationId = actor.organizationId();
    if (assets.findByIdAndOrganizationId(assetId, organizationId).isEmpty()) {
      throw assetNotFound();
    }
    return pairings
        .findByOrganizationIdAndAssetIdOrderByAssignedAtDesc(organizationId, assetId)
        .stream()
        .map(AssetPairAdminResponse::from)
        .toList();
  }

  @Transactional
  public AssetPairAdminResponse activate(
      UUID actorId, UUID assetId, UUID pairingId, ActivateAssetPairAssignmentRequest request) {
    UserAccess.ActiveUser actor = requireOrgAdmin(actorId);
    UUID organizationId = actor.organizationId();
    entitlement.requireActive(organizationId, Instant.now());
    var asset =
        assets
            .findWithLockByIdAndOrganizationId(assetId, organizationId)
            .orElseThrow(this::assetNotFound);
    if (asset.getStatus() != com.smartdroneinspection.assets.domain.enums.AssetStatus.ACTIVE) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "ASSET_NOT_ASSIGNABLE", "Only active assets can receive a pair");
    }
    AssetPairAssignment target =
        pairings
            .findByIdAndOrganizationId(pairingId, organizationId)
            .filter(pairing -> pairing.getAssetId().equals(assetId))
            .orElseThrow(this::pairingNotFound);
    if (target.getStatus() != AssetPairAssignmentStatus.DRAFT) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "ASSET_PAIR_NOT_DRAFT", "Only a draft pair can be activated");
    }
    Instant now = Instant.now();
    if (target.getValidFrom().isAfter(now)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "ASSET_PAIR_NOT_EFFECTIVE",
          "Future-effective pair assignments cannot become the current default yet");
    }
    if (target.getValidUntil() != null && !target.getValidUntil().isAfter(now)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "ASSET_PAIR_EXPIRED", "Pair validity has already ended");
    }
    userAccess
        .findActiveUser(target.getInspectorUserId())
        .filter(user -> organizationId.equals(user.organizationId()))
        .filter(user -> user.hasRole("INSPECTOR"))
        .orElseThrow(this::inspectorNotAssignable);
    Drone drone =
        drones
            .findByIdAndOrganizationId(target.getDroneId(), organizationId)
            .orElseThrow(this::droneNotFound);
    if (!drone.isEligibleForAssignment()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "DRONE_NOT_ASSIGNABLE", "Drone is not available for assignment");
    }

    AssetPairAssignment activePair =
        pairings.findActiveWithLockByOrganizationIdAndAssetId(organizationId, assetId).orElse(null);
    if (activePair != null && !activePair.getId().equals(pairingId)) {
      String reason = request.reason().trim();
      AssetPairAssignmentStatus oldStatus = activePair.getStatus();
      activePair.supersede(reason);
      pairings.saveAndFlush(activePair);
      recordAudit(
          organizationId,
          actorId,
          activePair,
          oldStatus.name(),
          activePair.getStatus().name(),
          reason);
    }
    try {
      target.activate(request.reason().trim(), target.getValidUntil());
    } catch (IllegalArgumentException | IllegalStateException exception) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "ASSET_PAIR_NOT_ACTIVATABLE", exception.getMessage());
    }
    AssetPairAssignment activated = pairings.saveAndFlush(target);
    recordAudit(
        organizationId,
        actorId,
        activated,
        AssetPairAssignmentStatus.DRAFT.name(),
        AssetPairAssignmentStatus.ACTIVE.name(),
        request.reason().trim());
    return AssetPairAdminResponse.from(activated);
  }

  private UserAccess.ActiveUser requireOrgAdmin(UUID actorId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .filter(user -> user.hasRole("ORG_ADMIN"))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.FORBIDDEN, "FORBIDDEN", "Organization Admin role is required"));
    if (actor.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Organization Admin role is required");
    }
    return actor;
  }

  private void recordAudit(
      UUID organizationId,
      UUID actorId,
      AssetPairAssignment pair,
      String beforeStatus,
      String afterStatus,
      String reason) {
    jdbcTemplate.update(
        """
        INSERT INTO audit_events
          (organization_id, actor_user_id, action, aggregate_type, aggregate_id,
           before_status, after_status, reason, safe_metadata, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        """,
        organizationId,
        actorId,
        "ASSET_PAIR_" + afterStatus,
        "ASSET_PAIR_ASSIGNMENT",
        pair.getId(),
        beforeStatus,
        afterStatus,
        reason,
        tools.jackson.databind.json.JsonMapper.shared()
            .writeValueAsString(
                java.util.Map.of(
                    "assetId", pair.getAssetId().toString(),
                    "inspectorUserId", pair.getInspectorUserId().toString(),
                    "droneId", pair.getDroneId().toString())),
        Timestamp.from(Instant.now()));
  }

  private BusinessException assetNotFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "Asset not found.");
  }

  private BusinessException inspectorNotAssignable() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST,
        "INSPECTOR_NOT_ASSIGNABLE",
        "Inspector is inactive, outside this organization, or lacks the INSPECTOR role");
  }

  private BusinessException droneNotFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, "DRONE_NOT_FOUND", "Drone not found.");
  }

  private BusinessException pairingNotFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "ASSET_PAIR_NOT_FOUND", "Pair assignment not found.");
  }
}

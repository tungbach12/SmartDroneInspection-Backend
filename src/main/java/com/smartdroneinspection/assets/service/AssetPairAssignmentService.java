package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.RespondToAssignmentRequest;
import com.smartdroneinspection.assets.api.dto.response.AssetPairAssignmentResponse;
import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetPairAssignment;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus;
import com.smartdroneinspection.assets.repository.AssetPairAssignmentRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MF2-01 and MF2-02: the assigned inspector opens a pairing and accepts or declines it, and the
 * platform records that answer against the pairing.
 *
 * <p>Accepting is deliberately not readiness. It records that the inspector took the job; MF2-07
 * decides separately, with a named independent reviewer, whether the mission may fly.
 */
@Service
public class AssetPairAssignmentService {

  private final AssetPairAssignmentRepository pairings;
  private final AssetRepository assets;
  private final DroneRepository drones;
  private final UserAccess userAccess;

  public AssetPairAssignmentService(
      AssetPairAssignmentRepository pairings,
      AssetRepository assets,
      DroneRepository drones,
      UserAccess userAccess) {
    this.pairings = pairings;
    this.assets = assets;
    this.drones = drones;
    this.userAccess = userAccess;
  }

  /**
   * The pairings this inspector has been given and not yet answered (MF2-01 inbox).
   *
   * <p>Scoped by organization as well as inspector so the answer screen cannot surface another
   * tenant's work even if an inspector id were guessed.
   */
  @Transactional(readOnly = true)
  public List<AssetPairAssignmentResponse> listUnansweredAssignments(UUID inspectorId) {
    UserAccess.ActiveUser inspector = requireActive(inspectorId);
    requireRole(inspector, Roles.INSPECTOR);
    UUID organizationId = requireOrganization(inspector);

    return pairings
        .findByOrganizationIdAndInspectorUserIdAndAssignmentResponseIsNull(
            organizationId, inspectorId)
        .stream()
        .filter(pairing -> pairing.getStatus() == AssetPairAssignmentStatus.ACTIVE)
        .map(this::toResponse)
        .toList();
  }

  /** One pairing as the assigned inspector sees it, or a scope error if it is not theirs. */
  @Transactional(readOnly = true)
  public AssetPairAssignmentResponse getAssignment(UUID inspectorId, UUID pairingId) {
    UserAccess.ActiveUser inspector = requireActive(inspectorId);
    requireRole(inspector, Roles.INSPECTOR);
    UUID organizationId = requireOrganization(inspector);

    return toResponse(requireOwnedPairing(pairingId, organizationId, inspectorId));
  }

  /**
   * Records the inspector's answer (MF2-01/02).
   *
   * <p>A rejection returns the pairing to the administrator, which MF2-01 states explicitly, so a
   * decline suspends rather than supersedes: superseding is reserved for replacing a pairing with a
   * new one.
   */
  @Transactional
  public AssetPairAssignmentResponse respond(
      UUID inspectorId, UUID pairingId, RespondToAssignmentRequest request) {
    UserAccess.ActiveUser inspector = requireActive(inspectorId);
    requireRole(inspector, Roles.INSPECTOR);
    UUID organizationId = requireOrganization(inspector);

    AssetPairAssignment pairing =
        pairings
            .findWithLockByIdAndOrganizationId(pairingId, organizationId)
            .orElseThrow(this::pairingNotFound);

    if (!pairing.getInspectorUserId().equals(inspectorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "ASSIGNMENT_SCOPE_DENIED",
          "This assignment belongs to another inspector.");
    }

    if (pairing.getStatus() != AssetPairAssignmentStatus.ACTIVE) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "ASSIGNMENT_NOT_ACTIVE",
          "This assignment is no longer open for a response.");
    }

    try {
      pairing.respond(inspectorId, request.response(), request.rejectionReason());
    } catch (IllegalArgumentException | IllegalStateException exception) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "ASSIGNMENT_RESPONSE_REJECTED", exception.getMessage());
    }

    if (request.response()
        == com.smartdroneinspection.assets.domain.enums.AssignmentResponse.REJECTED) {
      pairing.suspend(request.rejectionReason());
    }

    return toResponse(pairings.saveAndFlush(pairing));
  }

  private AssetPairAssignment requireOwnedPairing(
      UUID pairingId, UUID organizationId, UUID inspectorId) {
    AssetPairAssignment pairing =
        pairings
            .findByIdAndOrganizationId(pairingId, organizationId)
            .orElseThrow(this::pairingNotFound);
    if (!pairing.getInspectorUserId().equals(inspectorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "ASSIGNMENT_SCOPE_DENIED",
          "This assignment belongs to another inspector.");
    }
    return pairing;
  }

  private AssetPairAssignmentResponse toResponse(AssetPairAssignment pairing) {
    Asset asset =
        assets
            .findByIdAndOrganizationId(pairing.getAssetId(), pairing.getOrganizationId())
            .orElse(null);
    Drone drone =
        drones
            .findByIdAndOrganizationId(pairing.getDroneId(), pairing.getOrganizationId())
            .orElse(null);

    return new AssetPairAssignmentResponse(
        pairing.getId(),
        pairing.getOrganizationId(),
        pairing.getAssetId(),
        asset == null ? null : asset.getName(),
        pairing.getInspectorUserId(),
        pairing.getDroneId(),
        drone == null ? null : drone.getSerialNumber(),
        drone == null ? null : drone.getServiceability(),
        pairing.getValidFrom(),
        pairing.getValidUntil(),
        pairing.getStatus(),
        pairing.getReason(),
        pairing.getAssignmentResponse(),
        pairing.getRespondedAt(),
        pairing.getAssignedAt());
  }

  private BusinessException pairingNotFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "Assignment was not found.");
  }

  private UUID requireOrganization(UserAccess.ActiveUser actor) {
    if (actor.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "User has no organization scope");
    }
    return actor.organizationId();
  }

  private void requireRole(UserAccess.ActiveUser actor, String role) {
    if (!actor.hasRole(role)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Role " + role + " is required");
    }
  }

  private UserAccess.ActiveUser requireActive(UUID actorId) {
    return userAccess
        .findActiveUser(actorId)
        .orElseThrow(
            () -> new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));
  }
}

package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.CreateFlightPermitRequest;
import com.smartdroneinspection.assets.api.dto.request.ReviewFlightPermitRequest;
import com.smartdroneinspection.assets.api.dto.response.FlightPermitResponse;
import com.smartdroneinspection.assets.domain.FlightPermit;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.FlightPermitRepository;
import com.smartdroneinspection.inspections.EvidenceOwnership;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.SubscriptionEntitlement;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MF1-07 permit and compliance records for one customer organization.
 *
 * <p><strong>What this slice is.</strong> A transcription service. It stores what an organization
 * says about a permit it holds, and who inside that organization read the record.
 *
 * <p><strong>What this slice is not.</strong> It is not a verification service. No registry, no
 * issuing authority, no government service and no external API is consulted anywhere here, and
 * nothing in this class or its response types asserts that a permit is authentic, current, legally
 * cleared or confirmed. An {@code ACTIVE} status is a record of the organization's own account of
 * its application; an internal review is an attributable human decision by one of its
 * administrators, recorded in {@code reviewedByUserId} / {@code reviewedAt} / {@code reviewReason}
 * for audit. {@code NOT_APPLICABLE} is the organization recording its own conclusion that no permit
 * applies — it is not a regulator's determination.
 *
 * <p>The tenant comes from the authenticated principal, never from the request, and a reference to
 * another organization's asset or evidence is answered exactly like a missing one, so a 404
 * discloses nothing about what other tenants hold.
 */
@Service
public class ComplianceService {

  /**
   * Deliberately identical for a foreign asset, for foreign evidence and for a permit belonging to
   * another tenant, so a 404 reveals nothing about whether another tenant's record exists.
   */
  private static final String NOT_FOUND_MESSAGE = "Permit reference was not found.";

  private final FlightPermitRepository permits;
  private final AssetRepository assets;
  private final UserAccess users;
  private final EvidenceOwnership evidence;
  private final SubscriptionEntitlement entitlement;

  public ComplianceService(
      FlightPermitRepository permits,
      AssetRepository assets,
      UserAccess users,
      EvidenceOwnership evidence,
      SubscriptionEntitlement entitlement) {
    this.permits = permits;
    this.assets = assets;
    this.users = users;
    this.evidence = evidence;
    this.entitlement = entitlement;
  }

  @Transactional
  public FlightPermitResponse create(UUID actorId, CreateFlightPermitRequest request) {
    UUID organizationId = requireActorOrganization(actorId);
    requireOwnedAsset(request.assetId(), organizationId);
    requireOwnedEvidence(request.sourceEvidenceId(), organizationId);
    requireJsonDocument(request.geographicScope(), "Geographic scope must be a JSON document.");
    requireJsonDocument(request.conditions(), "Conditions must be a JSON document.");
    requireEntitled(organizationId);

    FlightPermit saved =
        permits.saveAndFlush(
            new FlightPermit(
                organizationId,
                request.assetId(),
                request.areaReference(),
                request.permitType(),
                request.issuingAuthority(),
                request.permitReference(),
                request.geographicScope(),
                request.validFrom(),
                request.validUntil(),
                request.conditions(),
                request.status(),
                request.sourceEvidenceId()));
    return FlightPermitResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public List<FlightPermitResponse> list(UUID actorId) {
    UUID organizationId = requireActorOrganization(actorId);
    return permits.listByOrganization(organizationId).stream()
        .map(FlightPermitResponse::from)
        .toList();
  }

  @Transactional
  public FlightPermitResponse review(
      UUID actorId, UUID permitId, ReviewFlightPermitRequest request) {
    UUID organizationId = requireActorOrganization(actorId);
    requireEntitled(organizationId);

    FlightPermit permit =
        permits
            .findInOrganization(permitId, organizationId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "PERMIT_REFERENCE_NOT_FOUND", NOT_FOUND_MESSAGE));

    permit.recordInternalReview(request.status(), actorId, Instant.now(), request.reason());
    return FlightPermitResponse.from(permits.saveAndFlush(permit));
  }

  /**
   * Resolves the acting ORG_ADMIN's own organization. A suspended or disabled actor is absent here
   * and is rejected; the tenant is never taken from the request.
   */
  private UUID requireActorOrganization(UUID actorId) {
    UserAccess.ActiveUser actor =
        users
            .findActiveUser(actorId)
            .filter(candidate -> candidate.hasRole(Roles.ORG_ADMIN))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "COMPLIANCE_ACCESS_DENIED",
                        "Only an active organization administrator may manage compliance records."));
    if (actor.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "COMPLIANCE_ACCESS_DENIED",
          "Only an active organization administrator may manage compliance records.");
    }
    return actor.organizationId();
  }

  /** An asset of another tenant is reported exactly like one that does not exist. */
  private void requireOwnedAsset(UUID assetId, UUID organizationId) {
    if (assetId != null && assets.findByIdAndOrganizationId(assetId, organizationId).isEmpty()) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND, "PERMIT_REFERENCE_NOT_FOUND", NOT_FOUND_MESSAGE);
    }
  }

  /** Evidence of another tenant is reported exactly like a missing asset reference. */
  private void requireOwnedEvidence(UUID evidenceId, UUID organizationId) {
    if (evidenceId != null && !evidence.existsInOrganization(evidenceId, organizationId)) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND, "PERMIT_REFERENCE_NOT_FOUND", NOT_FOUND_MESSAGE);
    }
  }

  /**
   * Rejects a value that is not a JSON document before it reaches the {@code jsonb} column, where
   * the failure would otherwise surface as an opaque server error. The document is still stored
   * verbatim: the platform reads no structure into it and draws no conclusion from it.
   */
  private void requireJsonDocument(String value, String message) {
    if (value == null) {
      return;
    }
    try {
      tools.jackson.databind.json.JsonMapper.shared().readTree(value);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException(message);
    }
  }

  /** Guards new MF1 compliance work; an unentitled tenant keeps its existing records. */
  private void requireEntitled(UUID organizationId) {
    entitlement.requireActive(organizationId, Instant.now());
  }
}

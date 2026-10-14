package com.smartdroneinspection.workforce.service;

import com.smartdroneinspection.shared.EvidenceOwnership;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.SubscriptionEntitlement;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.workforce.api.dto.request.CreateWorkforceCredentialRequest;
import com.smartdroneinspection.workforce.api.dto.request.ReviewWorkforceCredentialRequest;
import com.smartdroneinspection.workforce.api.dto.response.WorkforceCredentialResponse;
import com.smartdroneinspection.workforce.config.WorkforceProperties;
import com.smartdroneinspection.workforce.domain.WorkforceCredential;
import com.smartdroneinspection.workforce.repository.WorkforceCredentialRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MF1-04/05 credential records for one customer organization.
 *
 * <p>The tenant comes from the authenticated principal, never from the request. A credential
 * records a document the organization holds about one of its own workforce members, and the review
 * it carries is an attributable internal human decision — the platform neither consults nor claims
 * an external verification.
 */
@Service
public class WorkforceCredentialService {

  /**
   * Deliberately identical for a foreign subject and for evidence the caller may not see, so a 404
   * reveals nothing about whether another tenant's record exists.
   */
  private static final String NOT_FOUND_MESSAGE = "Credential subject was not found.";

  private final WorkforceCredentialRepository credentials;
  private final UserAccess users;
  private final EvidenceOwnership evidence;
  private final SubscriptionEntitlement entitlement;
  private final WorkforceProperties properties;

  public WorkforceCredentialService(
      WorkforceCredentialRepository credentials,
      UserAccess users,
      EvidenceOwnership evidence,
      SubscriptionEntitlement entitlement,
      WorkforceProperties properties) {
    this.credentials = credentials;
    this.users = users;
    this.evidence = evidence;
    this.entitlement = entitlement;
    this.properties = properties;
  }

  @Transactional
  public WorkforceCredentialResponse create(
      UUID actorId, CreateWorkforceCredentialRequest request) {
    UUID organizationId = requireActorOrganization(actorId);
    requireSubjectOfOrganization(request.userId(), organizationId);
    requireOwnedEvidence(request.evidenceId(), organizationId);
    requireDateOrder(request.issuedAt(), request.expiresAt());
    requireEntitled(organizationId);

    WorkforceCredential saved =
        credentials.saveAndFlush(
            new WorkforceCredential(
                organizationId,
                request.userId(),
                request.credentialType().trim(),
                request.issuer(),
                request.credentialReference(),
                request.issuedAt(),
                request.expiresAt(),
                request.evidenceId(),
                null,
                null,
                null));
    return WorkforceCredentialResponse.from(saved, Instant.now());
  }

  @Transactional(readOnly = true)
  public List<WorkforceCredentialResponse> list(UUID actorId) {
    UUID organizationId = requireActorOrganization(actorId);
    Instant now = Instant.now();
    return credentials.listByOrganization(organizationId).stream()
        .map(
            credential ->
                WorkforceCredentialResponse.from(
                    credential, now, properties.getCredentialExpiringSoonWindow()))
        .toList();
  }

  @Transactional
  public WorkforceCredentialResponse review(
      UUID actorId, UUID credentialId, ReviewWorkforceCredentialRequest request) {
    UUID organizationId = requireActorOrganization(actorId);
    requireEntitled(organizationId);

    WorkforceCredential credential =
        credentials
            .findInOrganization(credentialId, organizationId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "CREDENTIAL_NOT_FOUND", NOT_FOUND_MESSAGE));

    WorkforceCredential.Status outcome =
        request.decision() == ReviewWorkforceCredentialRequest.Decision.APPROVE
            ? WorkforceCredential.Status.ACTIVE
            : WorkforceCredential.Status.REJECTED;
    credential.review(outcome, actorId, Instant.now(), request.reason());
    return WorkforceCredentialResponse.from(
        credentials.saveAndFlush(credential),
        Instant.now(),
        properties.getCredentialExpiringSoonWindow());
  }

  /**
   * Resolves the acting ORG_ADMIN's own organization. A suspended or disabled actor is absent here
   * and is rejected.
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
                        "WORKFORCE_ACCESS_DENIED",
                        "Only an active organization administrator may manage workforce users."));
    UUID organizationId = actor.organizationId();
    if (organizationId == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "WORKFORCE_ACCESS_DENIED",
          "Only an active organization administrator may manage workforce users.");
    }
    return organizationId;
  }

  private void requireSubjectOfOrganization(UUID subjectId, UUID organizationId) {
    users
        .findWorkforceMember(subjectId, organizationId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "CREDENTIAL_NOT_FOUND", NOT_FOUND_MESSAGE));
  }

  /**
   * Evidence that is missing, or belongs to another tenant, is reported exactly like a missing
   * subject so the endpoint cannot be used to discover foreign evidence identifiers.
   */
  private void requireOwnedEvidence(UUID evidenceId, UUID organizationId) {
    if (evidenceId != null && !evidence.existsInOrganization(evidenceId, organizationId)) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "CREDENTIAL_NOT_FOUND", NOT_FOUND_MESSAGE);
    }
  }

  private void requireDateOrder(Instant issuedAt, Instant expiresAt) {
    if (issuedAt != null && expiresAt != null && !expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("Expiry must be later than the issue date.");
    }
  }

  /** Guards new MF1 credential work; an expired tenant keeps its existing records. */
  private void requireEntitled(UUID organizationId) {
    entitlement.requireActive(organizationId, Instant.now());
  }
}

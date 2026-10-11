package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.readiness.DroneDocumentReadinessAccess;
import com.smartdroneinspection.assets.readiness.DroneDocumentSummary;
import com.smartdroneinspection.inspections.api.dto.response.ReadinessSourcesResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.workforce.credential.WorkforceCredentialAccess;
import com.smartdroneinspection.workforce.credential.WorkforceCredentialSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What MF2-07 asks a reviewer to look at, before they approve.
 *
 * <p>The approval request names a reviewer credential, the assigned Inspector's credentials and the
 * assigned Drone's documents. None of those ids can be invented by a client, so without this
 * service the only way to perform MF2-07 over HTTP would be to type UUIDs by hand.
 *
 * <p>Nothing here decides anything. It reads stored records and reports what they say, including
 * whether verification or review attribution is missing, because a reviewer entitled to see a
 * document's validity window is entitled to see that nobody has reviewed it. The decision rules
 * stay in {@link InspectionReadinessService}.
 */
@Service
public class ReadinessSourcesService {

  private final InspectionRepository inspections;
  private final WorkforceCredentialAccess workforceCredentials;
  private final DroneDocumentReadinessAccess assetReadiness;
  private final UserAccess userAccess;

  public ReadinessSourcesService(
      InspectionRepository inspections,
      WorkforceCredentialAccess workforceCredentials,
      DroneDocumentReadinessAccess assetReadiness,
      UserAccess userAccess) {
    this.inspections = inspections;
    this.workforceCredentials = workforceCredentials;
    this.assetReadiness = assetReadiness;
    this.userAccess = userAccess;
  }

  /**
   * The assigned Inspector's credentials and the assigned Drone's documents for this inspection.
   *
   * <p>A caller from another organization receives {@code INSPECTION_NOT_FOUND} rather than an
   * empty result, because an empty one would confirm that an inspection with that id exists.
   */
  @Transactional(readOnly = true)
  public ReadinessSourcesResponse sources(UUID reviewerId, UUID inspectionId) {
    UUID organizationId = requireReviewer(reviewerId);
    Inspection inspection =
        inspections
            .findByIdAndOrganizationId(inspectionId, organizationId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection was not found."));

    List<ReadinessSourcesResponse.CredentialSummaryResponse> credentials =
        inspection.getInspectorId() == null
            ? List.of()
            : workforceCredentials
                .listByOrganizationIdAndSubjectId(organizationId, inspection.getInspectorId())
                .stream()
                .map(ReadinessSourcesService::credential)
                .toList();

    List<ReadinessSourcesResponse.DocumentSummaryResponse> documents =
        inspection.getDroneId() == null
            ? List.of()
            : assetReadiness.listForDrone(organizationId, inspection.getDroneId()).stream()
                .map(ReadinessSourcesService::document)
                .toList();

    return new ReadinessSourcesResponse(
        inspection.getId(),
        inspection.getInspectorId(),
        inspection.getDroneId(),
        credentials,
        documents);
  }

  /** The caller's own credentials, which an approval must reference. */
  @Transactional(readOnly = true)
  public List<WorkforceCredentialSummary> ownCredentials(UUID userId) {
    UUID organizationId = requireReviewer(userId);
    return workforceCredentials.listByOrganizationIdAndUserId(organizationId, userId);
  }

  private static ReadinessSourcesResponse.CredentialSummaryResponse credential(
      WorkforceCredentialSummary credential) {
    return new ReadinessSourcesResponse.CredentialSummaryResponse(
        credential.id(),
        credential.credentialType(),
        credential.issuer(),
        credential.credentialReference(),
        credential.issuedAt(),
        credential.expiresAt(),
        credential.status().name(),
        credential.verifiedAt());
  }

  private static ReadinessSourcesResponse.DocumentSummaryResponse document(
      DroneDocumentSummary document) {
    return new ReadinessSourcesResponse.DocumentSummaryResponse(
        document.id(),
        document.documentType(),
        document.issuer(),
        document.documentReference(),
        document.validFrom(),
        document.validUntil(),
        document.status().name(),
        document.reviewedAt());
  }

  private UUID requireReviewer(UUID userId) {
    UserAccess.ActiveUser user =
        userAccess
            .findActiveUser(userId)
            .filter(candidate -> candidate.hasRole(Roles.ORG_ADMIN))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "READINESS_REVIEW_DENIED",
                        "Only an active organization reviewer may read readiness sources."));
    if (user.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "READINESS_REVIEW_DENIED",
          "This reviewer has no organization scope.");
    }
    return user.organizationId();
  }
}

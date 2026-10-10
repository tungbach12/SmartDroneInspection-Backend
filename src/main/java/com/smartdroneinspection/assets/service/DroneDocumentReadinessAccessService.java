package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.domain.AssetPairAssignment;
import com.smartdroneinspection.assets.domain.DroneDocument;
import com.smartdroneinspection.assets.readiness.AssetPairReadinessStatus;
import com.smartdroneinspection.assets.readiness.AssetPairReadinessSummary;
import com.smartdroneinspection.assets.readiness.AssignmentReadinessResponse;
import com.smartdroneinspection.assets.readiness.DroneDocumentReadinessAccess;
import com.smartdroneinspection.assets.readiness.DroneDocumentReadinessStatus;
import com.smartdroneinspection.assets.readiness.DroneDocumentSummary;
import com.smartdroneinspection.assets.repository.AssetPairAssignmentRepository;
import com.smartdroneinspection.assets.repository.DroneDocumentRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DroneDocumentReadinessAccessService implements DroneDocumentReadinessAccess {

  private final DroneDocumentRepository documents;
  private final AssetPairAssignmentRepository pairs;

  public DroneDocumentReadinessAccessService(
      DroneDocumentRepository documents, AssetPairAssignmentRepository pairs) {
    this.documents = documents;
    this.pairs = pairs;
  }

  @Override
  @Transactional(readOnly = true)
  public List<DroneDocumentSummary> findForDrone(
      UUID organizationId, UUID droneId, List<UUID> documentIds) {
    if (documentIds.isEmpty()) {
      return List.of();
    }
    return documents.findForReadiness(organizationId, droneId, documentIds).stream()
        .sorted(Comparator.comparing(DroneDocument::getId))
        .map(DroneDocumentReadinessAccessService::toSummary)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AssetPairReadinessSummary> findPairForInspection(
      UUID organizationId, UUID pairId, UUID assetId) {
    return pairs
        .findForReadiness(organizationId, pairId, assetId)
        .map(DroneDocumentReadinessAccessService::toSummary);
  }

  private static DroneDocumentSummary toSummary(DroneDocument document) {
    return new DroneDocumentSummary(
        document.getId(),
        document.getDroneId(),
        document.getDocumentType(),
        document.getIssuer(),
        document.getDocumentReference(),
        document.getValidFrom(),
        document.getValidUntil(),
        DroneDocumentReadinessStatus.valueOf(document.getStatus().name()),
        document.getReviewedByUserId(),
        document.getReviewedAt(),
        document.getUploadedByUserId(),
        document.getCreatedAt(),
        document.getChecksumSha256());
  }

  private static AssetPairReadinessSummary toSummary(AssetPairAssignment pairing) {
    return new AssetPairReadinessSummary(
        pairing.getId(),
        pairing.getOrganizationId(),
        pairing.getAssetId(),
        pairing.getInspectorUserId(),
        pairing.getDroneId(),
        AssetPairReadinessStatus.valueOf(pairing.getStatus().name()),
        pairing.getValidFrom(),
        pairing.getValidUntil(),
        pairing.getAssignmentResponse() == null
            ? null
            : AssignmentReadinessResponse.valueOf(pairing.getAssignmentResponse().name()),
        pairing.getRespondedAt());
  }
}

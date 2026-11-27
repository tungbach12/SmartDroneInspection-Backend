package com.smartdroneinspection.contracts.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
import com.smartdroneinspection.inspections.spi.Mf2InspectionAssignment;
import com.smartdroneinspection.shared.api.InspectionContractSyncPort;
import com.smartdroneinspection.shared.api.InspectionReassignCandidatePort;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements shared boundary ports for {@code inspections} to stay synchronized with MF2 contract
 * occurrences and reuse partner candidate evaluation during reassignment.
 */
@Service
public class InspectionContractSyncAdapter
    implements InspectionContractSyncPort, InspectionReassignCandidatePort {

  private final ServiceRequestOccurrenceRepository occurrences;
  private final ServiceRequestRepository requests;
  private final AssetRepository assets;
  private final PartnerRegistryService partners;
  private final Mf2InspectionAssignment assignment;

  public InspectionContractSyncAdapter(
      ServiceRequestOccurrenceRepository occurrences,
      ServiceRequestRepository requests,
      AssetRepository assets,
      PartnerRegistryService partners,
      Mf2InspectionAssignment assignment) {
    this.occurrences = occurrences;
    this.requests = requests;
    this.assets = assets;
    this.partners = partners;
    this.assignment = assignment;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasScheduleConflict(
      UUID inspectorId, UUID droneId, Instant start, Instant end, UUID excludeInspectionId) {
    if (inspectorId == null || start == null || end == null) {
      return false;
    }
    UUID effectiveDrone = droneId != null ? droneId : inspectorId;
    if (excludeInspectionId == null) {
      return occurrences.hasScheduleConflict(inspectorId, effectiveDrone, start, end);
    }
    return occurrences.hasScheduleConflictExcludingInspection(
        inspectorId, effectiveDrone, start, end, excludeInspectionId);
  }

  @Override
  @Transactional
  public void syncRescheduleFromInspection(
      UUID inspectionId, Instant plannedStartAt, Instant plannedEndAt) {
    if (inspectionId == null) {
      return;
    }
    occurrences
        .findWithLockByInspectionId(inspectionId)
        .ifPresent(
            occurrence -> {
              try {
                occurrence.syncRescheduleFromInspection(plannedStartAt, plannedEndAt);
                occurrences.saveAndFlush(occurrence);
              } catch (IllegalArgumentException | IllegalStateException ex) {
                throw new BusinessException(
                    HttpStatus.CONFLICT, "OCCURRENCE_SYNC_INVALID", ex.getMessage());
              }
            });
  }

  @Override
  @Transactional
  public void syncReassignFromInspection(UUID inspectionId, UUID newInspectorId, UUID newDroneId) {
    if (inspectionId == null) {
      return;
    }
    occurrences
        .findWithLockByInspectionId(inspectionId)
        .ifPresent(
            occurrence -> {
              try {
                occurrence.syncReassignFromInspection(newInspectorId, newDroneId);
                occurrences.saveAndFlush(occurrence);
              } catch (IllegalArgumentException | IllegalStateException ex) {
                throw new BusinessException(
                    HttpStatus.CONFLICT, "OCCURRENCE_SYNC_INVALID", ex.getMessage());
              }
            });
  }

  @Override
  @Transactional
  public void syncCancelFromInspection(UUID inspectionId, String reason) {
    if (inspectionId == null) {
      return;
    }
    occurrences
        .findWithLockByInspectionId(inspectionId)
        .ifPresent(
            occurrence -> {
              try {
                occurrence.syncCancelFromInspection();
                occurrences.saveAndFlush(occurrence);
              } catch (IllegalStateException ex) {
                throw new BusinessException(
                    HttpStatus.CONFLICT, "OCCURRENCE_ALREADY_SETTLED", ex.getMessage());
              }
            });
  }

  @Override
  @Transactional
  public Optional<TimeoutRevertResult> syncTimeoutRevertFromInspection(
      UUID inspectionId, UUID timedOutInspectorId, Instant timedOutAt) {
    if (inspectionId == null) {
      return Optional.empty();
    }
    Optional<ServiceRequestOccurrence> found = occurrences.findWithLockByInspectionId(inspectionId);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    ServiceRequestOccurrence occurrence = found.get();
    boolean reverted = occurrence.revertTimeoutToPendingAssignment(timedOutInspectorId, timedOutAt);
    if (!reverted) {
      return Optional.empty();
    }
    occurrences.saveAndFlush(occurrence);
    ServiceRequest request = requests.findWithLockById(occurrence.getRequestId()).orElse(null);
    if (request != null) {
      request.reopenForAssignment();
      requests.saveAndFlush(request);
    }
    return Optional.of(
        new TimeoutRevertResult(
            occurrence.getRequestId(),
            request == null ? null : request.getAssignedManagerId(),
            occurrence.getOccurrenceIndex()));
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isLinkedOccurrencePaidOrAccepted(UUID inspectionId) {
    if (inspectionId == null) {
      return false;
    }
    return occurrences
        .findByInspectionId(inspectionId)
        .map(
            occurrence ->
                occurrence.getReportAcceptedAt() != null
                    || occurrence.getPaymentStatus() == OccurrencePaymentStatus.PAID)
        .orElse(false);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasActiveReinspectionRequest(
      UUID ownerCustomerId, UUID assetId, UUID sourceInspectionId) {
    return requests.existsActiveRequestForAsset(
        ownerCustomerId, assetId, false, sourceInspectionId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ReassignCandidate> evaluateReassignCandidates(
      UUID managerId,
      UUID inspectionId,
      UUID assetId,
      UUID ownerCustomerId,
      Instant plannedStartAt,
      Instant plannedEndAt) {
    Asset asset =
        assets
            .findByIdAndOwnerCustomerId(assetId, ownerCustomerId)
            .filter(value -> value.getStatus() != AssetStatus.RETIRED)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "Asset not found"));
    List<String> requiredFeatures = resolveRequiredFeatures(inspectionId);
    List<Instant> starts = plannedStartAt != null ? List.of(plannedStartAt) : List.of();
    return partners
        .evaluateCandidates(
            managerId,
            asset,
            plannedStartAt,
            requiredFeatures,
            starts,
            (inspectorId, droneId) ->
                occurrences.hasScheduleConflictExcludingInspection(
                    inspectorId, droneId, plannedStartAt, plannedEndAt, inspectionId),
            (inspectorId, droneId) ->
                assignment.hasPlatformScheduleConflictExcluding(
                    inspectorId, droneId, plannedStartAt, plannedEndAt, inspectionId))
        .stream()
        .map(InspectionContractSyncAdapter::toReassignCandidate)
        .toList();
  }

  private List<String> resolveRequiredFeatures(UUID inspectionId) {
    ServiceRequestOccurrence occurrence = occurrences.findByInspectionId(inspectionId).orElse(null);
    if (occurrence == null) {
      return List.of();
    }
    ServiceRequest request = requests.findById(occurrence.getRequestId()).orElse(null);
    if (request == null || request.getAcceptedTermsSnapshot() == null) {
      return List.of();
    }
    Map<String, Object> snapshot = request.getAcceptedTermsSnapshot();
    Object raw = snapshot.get("requiredDroneFeatureCodes");
    if (!(raw instanceof List<?> items)) {
      return List.of();
    }
    return items.stream().filter(String.class::isInstance).map(String.class::cast).toList();
  }

  private static ReassignCandidate toReassignCandidate(PartnerCandidateEvaluation.Result r) {
    return new ReassignCandidate(
        r.inspectorId(),
        r.droneId(),
        r.inspectorName(),
        r.inspectorAvatarUrl(),
        r.droneSerialNumber(),
        r.droneModel(),
        r.droneHasImage(),
        r.droneFeatureCodes(),
        r.eligibility().name(),
        r.capabilityFit().name(),
        r.droneMaxAltitudeMeters(),
        r.structureHeightMeters(),
        r.footprintAreaSquareMeters(),
        r.hardGates().stream()
            .map(c -> new CandidateCheck(c.code(), c.status().name(), c.explanation()))
            .toList(),
        r.capabilityChecks().stream()
            .map(c -> new CandidateCheck(c.code(), c.status().name(), c.explanation()))
            .toList(),
        r.disclaimer(),
        r.declineCount(),
        r.noShowCount(),
        r.abortCount());
  }
}

package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.repository.ServiceRequestOccurrenceRepository;
import com.smartdroneinspection.contracts.repository.ServiceRequestRepository;
import com.smartdroneinspection.inspections.spi.Mf2InspectionAssignment;
import com.smartdroneinspection.shared.api.InspectionReassignCandidatePort;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InspectionContractSyncAdapterTest {

  private ServiceRequestOccurrenceRepository occurrences;
  private ServiceRequestRepository requests;
  private AssetRepository assets;
  private PartnerRegistryService partners;
  private Mf2InspectionAssignment assignment;
  private InspectionContractSyncAdapter adapter;

  @BeforeEach
  void setUp() {
    occurrences = mock(ServiceRequestOccurrenceRepository.class);
    requests = mock(ServiceRequestRepository.class);
    assets = mock(AssetRepository.class);
    partners = mock(PartnerRegistryService.class);
    assignment = mock(Mf2InspectionAssignment.class);
    adapter =
        new InspectionContractSyncAdapter(occurrences, requests, assets, partners, assignment);
  }

  @Test
  void syncRescheduleReassignCancelAndConflictWorkAsExpected() {
    UUID inspectionId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    UUID droneId = UUID.randomUUID();
    Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
    Instant end = start.plus(2, ChronoUnit.HOURS);

    when(occurrences.hasScheduleConflictExcludingInspection(
            inspectorId, droneId, start, end, inspectionId))
        .thenReturn(true);
    assertThat(adapter.hasScheduleConflict(inspectorId, droneId, start, end, inspectionId))
        .isTrue();
    assertThat(adapter.hasScheduleConflict(null, droneId, start, end, inspectionId)).isFalse();

    ServiceRequestOccurrence occ = mock(ServiceRequestOccurrence.class);
    when(occurrences.findWithLockByInspectionId(inspectionId)).thenReturn(Optional.of(occ));

    adapter.syncRescheduleFromInspection(inspectionId, start, end);
    verify(occ).syncRescheduleFromInspection(start, end);
    verify(occurrences).saveAndFlush(occ);

    UUID newInspector = UUID.randomUUID();
    UUID newDrone = UUID.randomUUID();
    adapter.syncReassignFromInspection(inspectionId, newInspector, newDrone);
    verify(occ).syncReassignFromInspection(newInspector, newDrone);

    adapter.syncCancelFromInspection(inspectionId, "Postponed");
    verify(occ).syncCancelFromInspection();

    when(occ.getReportAcceptedAt()).thenReturn(Instant.now());
    when(occurrences.findByInspectionId(inspectionId)).thenReturn(Optional.of(occ));
    assertThat(adapter.isLinkedOccurrencePaidOrAccepted(inspectionId)).isTrue();
    assertThat(adapter.isLinkedOccurrencePaidOrAccepted(null)).isFalse();
  }

  @Test
  void syncCancelPropagatesConflictWhenOccurrenceAlreadySettled() {
    UUID inspectionId = UUID.randomUUID();
    ServiceRequestOccurrence occ = mock(ServiceRequestOccurrence.class);
    when(occurrences.findWithLockByInspectionId(inspectionId)).thenReturn(Optional.of(occ));
    org.mockito.Mockito.doThrow(new IllegalStateException("Already paid"))
        .when(occ)
        .syncCancelFromInspection();

    assertThatThrownBy(() -> adapter.syncCancelFromInspection(inspectionId, "Reason"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Already paid");
  }

  @Test
  void evaluateReassignCandidatesDelegatesToPartnerRegistryWithReliability() {
    UUID managerId = UUID.randomUUID();
    UUID inspectionId = UUID.randomUUID();
    UUID assetId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    UUID droneId = UUID.randomUUID();
    Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
    Instant end = start.plus(2, ChronoUnit.HOURS);

    Asset asset = mock(Asset.class);
    when(asset.getStatus()).thenReturn(AssetStatus.ACTIVE);
    when(assets.findByIdAndOwnerCustomerId(assetId, ownerId)).thenReturn(Optional.of(asset));

    ServiceRequestOccurrence occ = mock(ServiceRequestOccurrence.class);
    when(occ.getRequestId()).thenReturn(requestId);
    when(occurrences.findByInspectionId(inspectionId)).thenReturn(Optional.of(occ));

    ServiceRequest request = mock(ServiceRequest.class);
    when(request.getAcceptedTermsSnapshot())
        .thenReturn(Map.of("requiredDroneFeatureCodes", List.of("RTK_POSITIONING")));
    when(requests.findById(requestId)).thenReturn(Optional.of(request));

    PartnerCandidateEvaluation.Result evalResult =
        new PartnerCandidateEvaluation.Result(
            inspectorId,
            droneId,
            "Partner 1",
            null,
            "SN-1",
            "DJI M350",
            true,
            List.of("RTK_POSITIONING"),
            PartnerCandidateEvaluation.Status.PASS,
            PartnerCandidateEvaluation.Status.PASS,
            BigDecimal.TEN,
            new BigDecimal("100.0"),
            new BigDecimal("0.0"),
            List.of(),
            List.of(),
            "PASS",
            2L,
            1L,
            0L);
    when(partners.evaluateCandidates(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(evalResult));

    List<InspectionReassignCandidatePort.ReassignCandidate> result =
        adapter.evaluateReassignCandidates(managerId, inspectionId, assetId, ownerId, start, end);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).declineCount()).isEqualTo(2);
    assertThat(result.get(0).noShowCount()).isEqualTo(1);
    assertThat(result.get(0).abortCount()).isZero();
  }
}

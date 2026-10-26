package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.api.dto.request.FindingSourceFilter;
import com.smartdroneinspection.inspections.api.dto.request.FindingStatusFilter;
import com.smartdroneinspection.inspections.api.dto.response.FindingPageResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportVersionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FindingServiceQueryTest {

  private EvidenceRepository evidenceRepository;
  private FindingRepository findingRepository;
  private EvidenceService evidenceService;
  private InspectionReportVersionRepository reportVersions;
  private InspectionRepository inspections;
  private UserAccess userAccess;
  private FindingService findingService;

  private final UUID actorId = UUID.randomUUID();
  private final UUID inspectionId = UUID.randomUUID();
  private final UUID evidenceId1 = UUID.randomUUID();
  private final UUID evidenceId2 = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    evidenceRepository = mock(EvidenceRepository.class);
    findingRepository = mock(FindingRepository.class);
    evidenceService = mock(EvidenceService.class);
    reportVersions = mock(InspectionReportVersionRepository.class);
    inspections = mock(InspectionRepository.class);
    userAccess = mock(UserAccess.class);

    findingService =
        new FindingService(
            evidenceRepository,
            findingRepository,
            evidenceService,
            reportVersions,
            inspections,
            Optional.empty(),
            userAccess);

    when(evidenceService.requireInspectionInScope(actorId, inspectionId))
        .thenReturn(mock(Inspection.class));
  }

  @Test
  void queryFiltersByStatusAndKeywords() {
    Evidence ev1 = mock(Evidence.class);
    when(ev1.getId()).thenReturn(evidenceId1);
    when(ev1.getFileName()).thenReturn("pier-crack.jpg");
    when(ev1.getLocationDescription()).thenReturn("Pier T1");

    Evidence ev2 = mock(Evidence.class);
    when(ev2.getId()).thenReturn(evidenceId2);
    when(ev2.getFileName()).thenReturn("deck-spall.jpg");
    when(ev2.getLocationDescription()).thenReturn("Main Span Deck");

    when(evidenceRepository.findByInspectionIdOrderByCreatedAtAsc(inspectionId))
        .thenReturn(List.of(ev1, ev2));

    Finding f1 =
        new Finding(
            inspectionId,
            evidenceId1,
            "F-1",
            "Longitudinal Crack",
            FindingSeverity.HIGH,
            "Visible crack along Pier T1",
            "Patch with epoxy",
            null,
            "yolo11s",
            null);

    Finding f2 =
        new Finding(
            inspectionId,
            evidenceId2,
            "F-2",
            "Surface Spall",
            FindingSeverity.MEDIUM,
            "Spall on concrete surface",
            null,
            null,
            null,
            null);
    f2.confirmByInspector();

    Finding f3 =
        new Finding(
            inspectionId,
            evidenceId2,
            "F-3",
            "Corrosion Staining",
            FindingSeverity.LOW,
            "Water run-off staining",
            null,
            null,
            null,
            null);
    f3.delete();

    when(findingRepository.findAllByInspectionIdIncludingRemoved(inspectionId))
        .thenReturn(List.of(f1, f2, f3));

    // Default ACTIVE filter
    FindingPageResponse activeRes =
        findingService.query(
            actorId,
            inspectionId,
            FindingStatusFilter.ACTIVE,
            null,
            FindingSourceFilter.ALL,
            null,
            null,
            1,
            10);
    assertThat(activeRes.totalCount()).isEqualTo(2);
    assertThat(activeRes.activeCount()).isEqualTo(2);
    assertThat(activeRes.removedCount()).isEqualTo(1);
    assertThat(activeRes.pendingCount()).isEqualTo(1);
    assertThat(activeRes.confirmedCount()).isEqualTo(1);

    // Filter by REMOVED
    FindingPageResponse removedRes =
        findingService.query(
            actorId,
            inspectionId,
            FindingStatusFilter.REMOVED,
            null,
            FindingSourceFilter.ALL,
            null,
            null,
            1,
            10);
    assertThat(removedRes.totalCount()).isEqualTo(1);
    assertThat(removedRes.items().getFirst().defectLabel()).isEqualTo("Corrosion Staining");

    // Filter by Defect keyword
    FindingPageResponse defectSearchRes =
        findingService.query(
            actorId,
            inspectionId,
            FindingStatusFilter.ALL,
            null,
            FindingSourceFilter.ALL,
            "crack",
            null,
            1,
            10);
    assertThat(defectSearchRes.totalCount()).isEqualTo(1);
    assertThat(defectSearchRes.items().getFirst().defectLabel()).isEqualTo("Longitudinal Crack");

    // Filter by Evidence keyword
    FindingPageResponse evidenceSearchRes =
        findingService.query(
            actorId,
            inspectionId,
            FindingStatusFilter.ALL,
            null,
            FindingSourceFilter.ALL,
            null,
            "deck-spall",
            1,
            10);
    assertThat(evidenceSearchRes.totalCount()).isEqualTo(2);
  }
}

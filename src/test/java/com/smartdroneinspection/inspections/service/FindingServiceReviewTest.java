package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.api.dto.request.ManualFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReviewFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.UpdateFindingRequest;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.domain.enums.FindingStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FindingServiceReviewTest {

  private static final UUID ACTOR_ID = UUID.randomUUID();
  private static final UUID INSPECTION_ID = UUID.randomUUID();
  private static final UUID EVIDENCE_ID = UUID.randomUUID();
  private static final UUID FINDING_ID = UUID.randomUUID();

  private EvidenceRepository evidence;
  private FindingRepository findings;
  private EvidenceService evidenceService;
  private InspectionReportRepository reportVersions;
  private InspectionRepository inspections;
  private PlatformAiSettingsService aiSettings;
  private FindingService service;
  private Inspection inspection;

  @BeforeEach
  void setUp() {
    evidence = mock(EvidenceRepository.class);
    findings = mock(FindingRepository.class);
    evidenceService = mock(EvidenceService.class);
    reportVersions = mock(InspectionReportRepository.class);
    inspections = mock(InspectionRepository.class);
    aiSettings = mock(PlatformAiSettingsService.class);
    service =
        new FindingService(
            evidence,
            findings,
            evidenceService,
            reportVersions,
            inspections,
            Optional.empty(),
            mock(UserAccess.class),
            Optional.of(aiSettings),
            Optional.empty());

    inspection =
        new Inspection(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2027-01-01T08:00:00Z"));
    ReflectionTestUtils.setField(inspection, "id", INSPECTION_ID);
    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.FIELD_COMPLETED);
    when(evidenceService.requireAssignedInspection(ACTOR_ID, INSPECTION_ID)).thenReturn(inspection);
  }

  @Test
  void confirmsAggregateFindingsAndMarksInspection() {
    Finding finding = manualFinding("Crack");
    when(evidence.existsByInspectionIdAndIsDeletedFalse(INSPECTION_ID)).thenReturn(true);
    when(findings.findByInspectionIdOrderByCreatedAtAsc(INSPECTION_ID))
        .thenReturn(List.of(finding));

    service.confirmFindings(ACTOR_ID, INSPECTION_ID, false);

    assertThat(finding.getStatus())
        .isEqualTo(com.smartdroneinspection.inspections.domain.enums.FindingStatus.CONFIRMED);
    assertThat(inspection.hasFindingsConfirmation()).isTrue();
    verify(findings).saveAllAndFlush(List.of(finding));
    verify(inspections).saveAndFlush(inspection);
  }

  @Test
  void confirmFindingsRequiresFieldCompletionAndActiveEvidence() {
    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.IN_PROGRESS);
    assertBusinessCode(
        () -> service.confirmFindings(ACTOR_ID, INSPECTION_ID, false), "INSPECTION_STATE_CONFLICT");

    ReflectionTestUtils.setField(inspection, "status", InspectionStatus.FIELD_COMPLETED);
    when(evidence.existsByInspectionIdAndIsDeletedFalse(INSPECTION_ID)).thenReturn(false);
    assertBusinessCode(
        () -> service.confirmFindings(ACTOR_ID, INSPECTION_ID, false), "ACTIVE_EVIDENCE_REQUIRED");
  }

  @Test
  void confirmFindingsRejectsPendingSuggestions() {
    Finding suggestion =
        new Finding(
            INSPECTION_ID,
            EVIDENCE_ID,
            "F-1",
            "Crack",
            FindingSeverity.HIGH,
            "notes",
            null,
            "yolo11",
            null);
    when(evidence.existsByInspectionIdAndIsDeletedFalse(INSPECTION_ID)).thenReturn(true);
    when(findings.findByInspectionIdOrderByCreatedAtAsc(INSPECTION_ID))
        .thenReturn(List.of(suggestion));

    assertBusinessCode(
        () -> service.confirmFindings(ACTOR_ID, INSPECTION_ID, false), "PENDING_FINDINGS_EXIST");
  }

  @Test
  void confirmFindingsRequiresNoFindingsAcknowledgmentWhenSetIsEmpty() {
    when(evidence.existsByInspectionIdAndIsDeletedFalse(INSPECTION_ID)).thenReturn(true);
    when(findings.findByInspectionIdOrderByCreatedAtAsc(INSPECTION_ID)).thenReturn(List.of());

    assertBusinessCode(
        () -> service.confirmFindings(ACTOR_ID, INSPECTION_ID, false),
        "NO_FINDINGS_ACKNOWLEDGMENT_REQUIRED");

    service.confirmFindings(ACTOR_ID, INSPECTION_ID, true);

    assertThat(inspection.hasFindingsConfirmation()).isTrue();
    verify(findings, never()).saveAllAndFlush(any());
  }

  @Test
  void reopensConfirmedFindingsAndClearsOfficialConfirmation() {
    inspection.confirmFindings();
    Finding finding = manualFinding("Crack");
    finding.confirmByInspector();
    when(reportVersions.findFirstByInspectionIdOrderByVersionNoDesc(INSPECTION_ID))
        .thenReturn(Optional.empty());
    when(findings.findByInspectionIdOrderByCreatedAtAsc(INSPECTION_ID))
        .thenReturn(List.of(finding));

    service.reopenFindings(ACTOR_ID, INSPECTION_ID);

    assertThat(finding.getStatus())
        .isEqualTo(com.smartdroneinspection.inspections.domain.enums.FindingStatus.PENDING);
    assertThat(inspection.hasFindingsConfirmation()).isFalse();
    verify(findings).flush();
    verify(inspections).saveAndFlush(inspection);
  }

  @Test
  void reopenFindingsRejectsUnconfirmedState() {
    when(reportVersions.findFirstByInspectionIdOrderByVersionNoDesc(INSPECTION_ID))
        .thenReturn(Optional.empty());

    assertBusinessCode(
        () -> service.reopenFindings(ACTOR_ID, INSPECTION_ID), "INSPECTION_STATE_CONFLICT");
  }

  @Test
  void confirmsSingleFindingAndKeepsExistingValuesForOmittedFields() {
    Finding finding = manualFinding("Crack");
    editableInspection();
    when(findings.findByIdAndInspectionId(FINDING_ID, INSPECTION_ID))
        .thenReturn(Optional.of(finding));
    when(findings.saveAndFlush(finding)).thenReturn(finding);

    var response =
        service.confirmFinding(
            ACTOR_ID, INSPECTION_ID, FINDING_ID, new ReviewFindingRequest(null, null, null, null));

    assertThat(response.defectLabel()).isEqualTo("Crack");
    assertThat(finding.getStatus())
        .isEqualTo(com.smartdroneinspection.inspections.domain.enums.FindingStatus.CONFIRMED);
    verify(findings).saveAndFlush(finding);
  }

  @Test
  void confirmFindingRejectsAlreadyConfirmedFinding() {
    Finding finding = manualFinding("Crack");
    finding.confirmByInspector();
    editableInspection();
    when(findings.findByIdAndInspectionId(FINDING_ID, INSPECTION_ID))
        .thenReturn(Optional.of(finding));

    assertBusinessCode(
        () ->
            service.confirmFinding(
                ACTOR_ID,
                INSPECTION_ID,
                FINDING_ID,
                new ReviewFindingRequest(null, null, null, null)),
        "FINDING_ALREADY_CONFIRMED");
  }

  @Test
  void createsManualFindingWithNextCode() {
    createEditableInspection();
    Evidence record = evidenceRecord();
    when(evidence.findByIdAndInspectionId(EVIDENCE_ID, INSPECTION_ID))
        .thenReturn(Optional.of(record));
    when(findings.findMaxFindingCodeNumberByInspectionId(INSPECTION_ID)).thenReturn(2L);
    when(findings.saveAndFlush(any(Finding.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.createManualFinding(
            ACTOR_ID,
            INSPECTION_ID,
            new ManualFindingRequest(
                EVIDENCE_ID, "Spall", FindingSeverity.MEDIUM, "Surface spall"));

    assertThat(response.findingCode()).isEqualTo("F-3");
    assertThat(response.defectLabel()).isEqualTo("Spall");
    verify(findings).saveAndFlush(any(Finding.class));
  }

  @Test
  void updatesFindingAndReopensConfirmedSet() {
    createEditableInspection();
    inspection.confirmFindings();
    Evidence record = evidenceRecord();
    Finding finding = manualFinding("Crack");
    when(findings.findByIdAndInspectionId(FINDING_ID, INSPECTION_ID))
        .thenReturn(Optional.of(finding));
    when(evidence.findByIdAndInspectionId(EVIDENCE_ID, INSPECTION_ID))
        .thenReturn(Optional.of(record));
    when(findings.saveAndFlush(finding)).thenReturn(finding);

    var response =
        service.updateFinding(
            ACTOR_ID,
            INSPECTION_ID,
            FINDING_ID,
            new UpdateFindingRequest(EVIDENCE_ID, "Spall", FindingSeverity.MEDIUM, "Updated note"));

    assertThat(response.defectLabel()).isEqualTo("Spall");
    assertThat(inspection.hasFindingsConfirmation()).isFalse();
    verify(inspections).saveAndFlush(inspection);
  }

  @Test
  void preservesConfirmedStatusWhenUpdatingFindingNotes() {
    createEditableInspection();
    Evidence record = evidenceRecord();
    Finding finding = manualFinding("Crack");
    finding.confirmByInspector();
    when(findings.findByIdAndInspectionId(FINDING_ID, INSPECTION_ID))
        .thenReturn(Optional.of(finding));
    when(evidence.findByIdAndInspectionId(EVIDENCE_ID, INSPECTION_ID))
        .thenReturn(Optional.of(record));
    when(findings.saveAndFlush(finding)).thenReturn(finding);

    var response =
        service.updateFinding(
            ACTOR_ID,
            INSPECTION_ID,
            FINDING_ID,
            new UpdateFindingRequest(
                EVIDENCE_ID, "Crack", FindingSeverity.MEDIUM, "AI drafted technical note"));

    assertThat(response.status()).isEqualTo(FindingStatus.CONFIRMED);
    assertThat(response.technicalNotes()).isEqualTo("AI drafted technical note");
  }

  @Test
  void deletesPendingAiSuggestionWithoutTombstone() {
    createEditableInspection();
    Finding suggestion =
        new Finding(
            INSPECTION_ID,
            EVIDENCE_ID,
            "F-1",
            "Crack",
            FindingSeverity.HIGH,
            "notes",
            null,
            "yolo11",
            null);
    ReflectionTestUtils.setField(suggestion, "id", FINDING_ID);
    when(findings.findByIdAndInspectionId(FINDING_ID, INSPECTION_ID))
        .thenReturn(Optional.of(suggestion));

    service.deleteFinding(ACTOR_ID, INSPECTION_ID, FINDING_ID);

    verify(aiSettings).recordRejectedPendingSuggestion();
    verify(findings).deleteByIdAndInspectionId(FINDING_ID, INSPECTION_ID);
    verify(findings).flush();
  }

  @Test
  void deletesAndRestoresManualFinding() {
    createEditableInspection();
    Finding finding = manualFinding("Crack");
    when(findings.findByIdAndInspectionId(FINDING_ID, INSPECTION_ID))
        .thenReturn(Optional.of(finding));
    when(findings.saveAndFlush(finding)).thenReturn(finding);

    service.deleteFinding(ACTOR_ID, INSPECTION_ID, FINDING_ID);
    assertThat(finding.isDeleted()).isTrue();

    when(findings.findRemovedByInspectionId(INSPECTION_ID)).thenReturn(List.of(finding));
    var response = service.restoreFinding(ACTOR_ID, INSPECTION_ID, FINDING_ID);
    assertThat(finding.isDeleted()).isFalse();
    assertThat(response.defectLabel()).isEqualTo("Crack");
  }

  @Test
  void findsRejectsMissingFindingAndRetiredEvidence() {
    createEditableInspection();
    when(findings.findByIdAndInspectionId(FINDING_ID, INSPECTION_ID)).thenReturn(Optional.empty());
    assertBusinessCode(
        () ->
            service.updateFinding(
                ACTOR_ID,
                INSPECTION_ID,
                FINDING_ID,
                new UpdateFindingRequest(
                    EVIDENCE_ID, "Spall", FindingSeverity.MEDIUM, "Updated note")),
        "FINDING_NOT_FOUND");

    Finding finding = manualFinding("Crack");
    when(findings.findByIdAndInspectionId(FINDING_ID, INSPECTION_ID))
        .thenReturn(Optional.of(finding));
    Evidence retired = evidenceRecord();
    when(retired.isRetired()).thenReturn(true);
    when(evidence.findByIdAndInspectionId(EVIDENCE_ID, INSPECTION_ID))
        .thenReturn(Optional.of(retired));
    assertBusinessCode(
        () ->
            service.updateFinding(
                ACTOR_ID,
                INSPECTION_ID,
                FINDING_ID,
                new UpdateFindingRequest(
                    EVIDENCE_ID, "Spall", FindingSeverity.MEDIUM, "Updated note")),
        "EVIDENCE_RETIRED");
  }

  @Test
  void listsActiveAndRemovedFindings() {
    when(evidenceService.requireInspectionInScope(ACTOR_ID, INSPECTION_ID)).thenReturn(inspection);
    Finding active = manualFinding("Crack");
    Finding removed = manualFinding("Spall");
    removed.delete();
    when(findings.findByInspectionIdOrderByCreatedAtAsc(INSPECTION_ID)).thenReturn(List.of(active));
    when(findings.findRemovedByInspectionId(INSPECTION_ID)).thenReturn(List.of(removed));

    assertThat(service.listFindings(ACTOR_ID, INSPECTION_ID)).hasSize(1);
    assertThat(service.listRemovedFindings(ACTOR_ID, INSPECTION_ID).getFirst().isDeleted())
        .isTrue();
  }

  private void editableInspection() {
    when(inspections.findById(INSPECTION_ID)).thenReturn(Optional.of(inspection));
    when(reportVersions.findFirstByInspectionIdOrderByVersionNoDesc(INSPECTION_ID))
        .thenReturn(Optional.empty());
  }

  private void createEditableInspection() {
    editableInspection();
  }

  private Finding manualFinding(String label) {
    Finding finding =
        new Finding(
            INSPECTION_ID,
            EVIDENCE_ID,
            "F-1",
            label,
            FindingSeverity.HIGH,
            "notes",
            null,
            null,
            null);
    ReflectionTestUtils.setField(finding, "id", FINDING_ID);
    return finding;
  }

  private static Evidence evidenceRecord() {
    Evidence record = mock(Evidence.class);
    when(record.getId()).thenReturn(EVIDENCE_ID);
    return record;
  }

  private static void assertBusinessCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
    assertThatThrownBy(callable)
        .isInstanceOfSatisfying(
            BusinessException.class, exception -> assertThat(exception.code()).isEqualTo(code));
  }
}

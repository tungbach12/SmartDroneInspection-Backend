package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspectionrequests.domain.InspectionAssignment;
import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionAssignmentStatus;
import com.smartdroneinspection.inspectionrequests.repository.InspectionAssignmentRepository;
import com.smartdroneinspection.inspections.api.dto.request.CreateManualFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReviewFindingCandidateRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReviewFindingCandidateRequest.Decision;
import com.smartdroneinspection.inspections.domain.AiFindingCandidate;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.VerifiedFinding;
import com.smartdroneinspection.inspections.domain.enums.AiFindingCandidateStatus;
import com.smartdroneinspection.inspections.domain.enums.EvidenceKind;
import com.smartdroneinspection.inspections.domain.enums.EvidenceSource;
import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.UploadStatus;
import com.smartdroneinspection.inspections.domain.enums.VerifiedFindingSource;
import com.smartdroneinspection.inspections.repository.AiFindingCandidateRepository;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.VerifiedFindingRepository;
import com.smartdroneinspection.inspections.service.AiFindingService;
import com.smartdroneinspection.inspections.spi.AiInferencePort;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AiFindingServiceTest {

  @Mock InspectionRepository inspections;
  @Mock InspectionAssignmentRepository assignments;
  @Mock EvidenceRepository evidenceRepository;
  @Mock AiFindingCandidateRepository candidates;
  @Mock VerifiedFindingRepository findings;
  @Mock UserAccess users;
  @Mock EvidenceObjectStore objectStore;
  @Mock AiInferencePort inference;
  @Mock Inspection inspection;
  @Mock InspectionAssignment assignment;

  private final UUID inspectorId = UUID.randomUUID();
  private final UUID inspectionId = UUID.randomUUID();
  private final UUID assignmentId = UUID.randomUUID();
  private final UUID evidenceId = UUID.randomUUID();
  private final UUID candidateId = UUID.randomUUID();
  private final byte[] imageBytes = new byte[] {(byte) 0x89, 'P', 'N', 'G'};
  private AiFindingService service;

  @BeforeEach
  void setUp() throws Exception {
    service =
        new AiFindingService(
            inspections,
            assignments,
            evidenceRepository,
            candidates,
            findings,
            users,
            Optional.of(objectStore),
            Optional.of(inference),
            new ObjectMapper());
    lenient()
        .when(users.findActiveUser(inspectorId))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(inspectorId, Set.of("INSPECTOR"))));
    lenient()
        .when(inspections.findByIdAndAuthorUserId(inspectionId, inspectorId))
        .thenReturn(Optional.of(inspection));
    lenient()
        .when(inspections.findForUpdateByIdAndAuthorUserId(inspectionId, inspectorId))
        .thenReturn(Optional.of(inspection));
    lenient().when(inspection.getAuthorUserId()).thenReturn(inspectorId);
    lenient().when(inspection.getAcceptedAssignmentId()).thenReturn(assignmentId);
    lenient().when(inspection.getStatus()).thenReturn(InspectionStatus.IN_PROGRESS);
    lenient()
        .when(assignments.findByIdAndInspectorUserId(assignmentId, inspectorId))
        .thenReturn(Optional.of(assignment));
    lenient().when(assignment.getStatus()).thenReturn(InspectionAssignmentStatus.ACCEPTED);
    lenient().when(assignment.getInspectorUserId()).thenReturn(inspectorId);
    lenient()
        .when(
            evidenceRepository.findByIdAndInspectionIdAndUploadStatus(
                evidenceId, inspectionId, UploadStatus.AVAILABLE))
        .thenReturn(Optional.of(availableEvidence()));
    lenient().when(objectStore.open("object-key")).thenReturn(new ByteArrayInputStream(imageBytes));
    lenient()
        .when(candidates.saveAndFlush(any(AiFindingCandidate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(candidates.saveAllAndFlush(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(findings.saveAndFlush(any(VerifiedFinding.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void persistsModelProvenanceAsPendingCandidateWithoutCreatingAnOfficialFinding()
      throws Exception {
    when(inference.analyze(imageBytes, "image/png"))
        .thenReturn(
            List.of(
                new AiInferencePort.Detection(
                    "yolo-crack",
                    "v3",
                    "surface-crack",
                    new BigDecimal("0.92"),
                    "{\"x\":0.1,\"y\":0.2,\"width\":0.3,\"height\":0.4}")));

    var results = service.analyze(inspectorId, inspectionId, evidenceId);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().modelName()).isEqualTo("yolo-crack");
    assertThat(results.getFirst().modelVersion()).isEqualTo("v3");
    assertThat(results.getFirst().predictedLabel()).isEqualTo("surface-crack");
    assertThat(results.getFirst().confidence()).isEqualByComparingTo("0.92");
    assertThat(results.getFirst().status()).isEqualTo(AiFindingCandidateStatus.PENDING);
    verify(findings, never()).saveAndFlush(any(VerifiedFinding.class));
  }

  @Test
  void keepsEvidenceUsableAndCreatesNoCandidateWhenInferenceFails() throws Exception {
    when(inference.analyze(imageBytes, "image/png")).thenThrow(new IOException("offline"));

    assertThatThrownBy(() -> service.analyze(inspectorId, inspectionId, evidenceId))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("AI_INFERENCE_UNAVAILABLE"));

    verify(candidates, never()).saveAndFlush(any(AiFindingCandidate.class));
    verify(evidenceRepository)
        .findByIdAndInspectionIdAndUploadStatus(evidenceId, inspectionId, UploadStatus.AVAILABLE);
  }

  @Test
  void confirmingCandidateCreatesAnOfficialVerifiedFinding() {
    var candidate = pendingCandidate();
    when(candidates.findForUpdateById(candidateId)).thenReturn(Optional.of(candidate));
    when(evidenceRepository.findByIdAndInspectionIdAndUploadStatus(
            evidenceId, inspectionId, UploadStatus.AVAILABLE))
        .thenReturn(Optional.of(availableEvidence()));
    var request =
        new ReviewFindingCandidateRequest(
            Decision.CONFIRM,
            null,
            FindingSeverity.HIGH,
            "North span, joint 4",
            "Crack verified during inspection",
            "Seal and monitor",
            null);

    var result = service.reviewCandidate(inspectorId, inspectionId, candidateId, request);

    assertThat(candidate.getStatus()).isEqualTo(AiFindingCandidateStatus.CONFIRMED);
    assertThat(result.status()).isEqualTo(AiFindingCandidateStatus.CONFIRMED);
    verify(findings)
        .saveAndFlush(
            org.mockito.ArgumentMatchers.argThat(
                finding ->
                    finding.getSource() == VerifiedFindingSource.AI_CONFIRMED
                        && finding.getAiCandidateId().equals(candidateId)
                        && finding.getDefectLabel().equals("surface-crack")));
  }

  @Test
  void rejectingCandidateNeverCreatesAnOfficialFinding() {
    var candidate = pendingCandidate();
    when(candidates.findForUpdateById(candidateId)).thenReturn(Optional.of(candidate));
    when(evidenceRepository.findByIdAndInspectionIdAndUploadStatus(
            evidenceId, inspectionId, UploadStatus.AVAILABLE))
        .thenReturn(Optional.of(availableEvidence()));

    var result =
        service.reviewCandidate(
            inspectorId,
            inspectionId,
            candidateId,
            new ReviewFindingCandidateRequest(
                Decision.REJECT, null, null, null, null, null, "False positive"));

    assertThat(candidate.getStatus()).isEqualTo(AiFindingCandidateStatus.REJECTED);
    assertThat(result.status()).isEqualTo(AiFindingCandidateStatus.REJECTED);
    verify(findings, never()).saveAndFlush(any(VerifiedFinding.class));
  }

  @Test
  void supportsInspectorCreatedManualFinding() {
    var result =
        service.createManualFinding(
            inspectorId,
            inspectionId,
            new CreateManualFindingRequest(
                evidenceId,
                "concrete-spall",
                FindingSeverity.MEDIUM,
                "Pier 2, east face",
                "Manual finding based on visual inspection",
                "Patch and inspect next cycle"));

    assertThat(result.source()).isEqualTo(VerifiedFindingSource.MANUAL);
    assertThat(result.defectLabel()).isEqualTo("concrete-spall");
    verify(findings)
        .saveAndFlush(
            org.mockito.ArgumentMatchers.argThat(
                finding -> finding.getSource() == VerifiedFindingSource.MANUAL));
  }

  @Test
  void deniesCandidateReviewByAnUnassignedInspector() {
    UUID otherInspector = UUID.randomUUID();
    when(users.findActiveUser(otherInspector))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(otherInspector, Set.of("INSPECTOR"))));
    when(inspections.findForUpdateByIdAndAuthorUserId(inspectionId, otherInspector))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.reviewCandidate(
                    otherInspector,
                    inspectionId,
                    candidateId,
                    new ReviewFindingCandidateRequest(
                        Decision.REJECT, null, null, null, null, null, "False positive")))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).code())
                    .isEqualTo("INSPECTION_SCOPE_DENIED"));
    verify(candidates, never()).findById(candidateId);
  }

  private Evidence availableEvidence() {
    return new Evidence(
        inspectionId,
        null,
        inspectorId,
        EvidenceKind.INSPECTION,
        "bridge.png",
        "image/png",
        imageBytes.length,
        "checksum",
        "object-key",
        EvidenceSource.WEB_UPLOAD,
        UploadStatus.AVAILABLE);
  }

  private AiFindingCandidate pendingCandidate() {
    var candidate =
        new AiFindingCandidate(
            evidenceId,
            "yolo-crack",
            "v3",
            "surface-crack",
            new BigDecimal("0.92"),
            "{\"x\":0.1,\"y\":0.2,\"width\":0.3,\"height\":0.4}");
    var spy = org.mockito.Mockito.spy(candidate);
    when(spy.getId()).thenReturn(candidateId);
    return spy;
  }
}

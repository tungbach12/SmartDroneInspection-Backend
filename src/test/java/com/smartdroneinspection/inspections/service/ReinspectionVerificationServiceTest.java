package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.api.dto.request.UpdateDefectVerificationRequest;
import com.smartdroneinspection.inspections.api.dto.response.ReinspectionVerificationResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.ReinspectionDefectVerification;
import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.ReinspectionDefectVerificationRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReinspectionVerificationServiceTest {

  @Mock private ReinspectionDefectVerificationRepository verifications;
  @Mock private InspectionRepository inspections;
  @Mock private FindingRepository findings;
  @Mock private EvidenceRepository evidences;
  @Mock private UserAccess userAccess;

  private ReinspectionVerificationService service;

  private final UUID actorId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();
  private final UUID inspectionId = UUID.randomUUID();
  private final UUID findingId = UUID.randomUUID();
  private final UUID verificationId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new ReinspectionVerificationService(
            verifications, inspections, findings, evidences, userAccess);
  }

  @Test
  @DisplayName("getVerifications returns mapped list of defects with resolution status")
  void getVerificationsReturnsMappedList() {
    UserAccess.ActiveUser actor =
        new UserAccess.ActiveUser(
            actorId,
            Set.of(Roles.INSPECTOR),
            orgId,
            "INSPECTOR_PARTNER",
            "Inspector Guy",
            "inspector@drone.test",
            "LIC-123",
            "CLASS_A",
            Instant.now().plusSeconds(86400),
            "lic-key");

    Inspection inspection =
        new Inspection(orgId, UUID.randomUUID(), actorId, UUID.randomUUID(), null, null);
    ReflectionTestUtils.setField(inspection, "id", inspectionId);

    ReinspectionDefectVerification verif =
        new ReinspectionDefectVerification(inspectionId, findingId);
    ReflectionTestUtils.setField(verif, "id", verificationId);

    Finding finding =
        new Finding(
            UUID.randomUUID(),
            null,
            "CRACK-01",
            "Hairline Concrete Crack",
            FindingSeverity.HIGH,
            "Observed on south beam",
            "Seal crack",
            null,
            null,
            null);
    ReflectionTestUtils.setField(finding, "id", findingId);

    when(userAccess.findActiveUser(actorId)).thenReturn(Optional.of(actor));
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));
    when(verifications.findByReinspectionId(inspectionId)).thenReturn(List.of(verif));
    when(findings.findAllById(List.of(findingId))).thenReturn(List.of(finding));
    when(evidences.findAllById(List.of())).thenReturn(List.of());

    List<ReinspectionVerificationResponse> result = service.getVerifications(actorId, inspectionId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).sourceDefectLabel()).isEqualTo("Hairline Concrete Crack");
    assertThat(result.get(0).resolutionStatus()).isEqualTo("UNRESOLVED");
  }

  @Test
  @DisplayName("updateVerification successfully updates status and evidence")
  void updateVerificationSuccess() {
    UserAccess.ActiveUser actor =
        new UserAccess.ActiveUser(
            actorId,
            Set.of(Roles.INSPECTOR),
            orgId,
            "INSPECTOR_PARTNER",
            "Inspector Guy",
            "inspector@drone.test",
            "LIC-123",
            "CLASS_A",
            Instant.now().plusSeconds(86400),
            "lic-key");

    Inspection inspection =
        new Inspection(orgId, UUID.randomUUID(), actorId, UUID.randomUUID(), null, null);
    ReflectionTestUtils.setField(inspection, "id", inspectionId);

    ReinspectionDefectVerification verif =
        new ReinspectionDefectVerification(inspectionId, findingId);
    ReflectionTestUtils.setField(verif, "id", verificationId);

    UUID newEvidenceId = UUID.randomUUID();
    Evidence evidence =
        new Evidence(
            inspectionId,
            UUID.randomUUID(),
            "new-shot.jpg",
            "image/jpeg",
            2048,
            "evidence/shot.jpg",
            Instant.now(),
            null,
            null,
            "South beam",
            Instant.now());
    ReflectionTestUtils.setField(evidence, "id", newEvidenceId);

    when(userAccess.findActiveUser(actorId)).thenReturn(Optional.of(actor));
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));
    when(verifications.findById(verificationId)).thenReturn(Optional.of(verif));
    when(evidences.findByIdAndInspectionId(newEvidenceId, inspectionId))
        .thenReturn(Optional.of(evidence));
    when(verifications.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    UpdateDefectVerificationRequest request =
        new UpdateDefectVerificationRequest(
            "RESOLVED", newEvidenceId, "Repaired thoroughly with epoxy filler");

    ReinspectionVerificationResponse response =
        service.updateVerification(actorId, inspectionId, verificationId, request);

    assertThat(response.resolutionStatus()).isEqualTo("RESOLVED");
    assertThat(response.newEvidenceId()).isEqualTo(newEvidenceId);
    assertThat(response.inspectorNotes()).isEqualTo("Repaired thoroughly with epoxy filler");
    verify(verifications).saveAndFlush(verif);
  }

  @Test
  @DisplayName("updateVerification throws error when status is invalid")
  void updateVerificationInvalidStatus() {
    UserAccess.ActiveUser actor =
        new UserAccess.ActiveUser(
            actorId,
            Set.of(Roles.INSPECTOR),
            orgId,
            "INSPECTOR_PARTNER",
            "Inspector Guy",
            "inspector@drone.test",
            "LIC-123",
            "CLASS_A",
            Instant.now().plusSeconds(86400),
            "lic-key");

    Inspection inspection =
        new Inspection(orgId, UUID.randomUUID(), actorId, UUID.randomUUID(), null, null);
    ReflectionTestUtils.setField(inspection, "id", inspectionId);

    ReinspectionDefectVerification verif =
        new ReinspectionDefectVerification(inspectionId, findingId);
    ReflectionTestUtils.setField(verif, "id", verificationId);

    when(userAccess.findActiveUser(actorId)).thenReturn(Optional.of(actor));
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));
    when(verifications.findById(verificationId)).thenReturn(Optional.of(verif));

    UpdateDefectVerificationRequest request =
        new UpdateDefectVerificationRequest("UNKNOWN_STATUS", null, null);

    assertThatThrownBy(
            () -> service.updateVerification(actorId, inspectionId, verificationId, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Invalid resolution status");
  }
}

package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspectionrequests.domain.InspectionAssignment;
import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionAssignmentStatus;
import com.smartdroneinspection.inspectionrequests.repository.InspectionAssignmentRepository;
import com.smartdroneinspection.inspections.api.dto.request.EvidenceUploadMetadataRequest;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.EvidenceSource;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.UploadStatus;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.service.EvidenceService;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {

  private static final byte[] ONE_PIXEL_PNG =
      java.util.Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/o2cAAAAASUVORK5CYII=");

  @Mock InspectionRepository inspections;
  @Mock InspectionAssignmentRepository assignments;
  @Mock EvidenceRepository evidence;
  @Mock InspectionAssignment assignment;
  @Mock Inspection inspection;
  @Mock UserAccess users;
  @Mock EvidenceObjectStore objectStore;

  private final UUID actorId = UUID.randomUUID();
  private final UUID inspectionId = UUID.randomUUID();
  private final UUID assignmentId = UUID.randomUUID();
  private EvidenceService service;

  @BeforeEach
  void setUp() {
    service =
        new EvidenceService(
            inspections,
            assignments,
            evidence,
            users,
            Optional.of(objectStore),
            DataSize.ofBytes(1024));
    lenient()
        .when(users.findActiveUser(actorId))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(actorId, Set.of("INSPECTOR"))));
    lenient()
        .when(inspections.findForUpdateByIdAndAuthorUserId(inspectionId, actorId))
        .thenReturn(Optional.of(inspection));
    lenient().when(inspection.getAuthorUserId()).thenReturn(actorId);
    lenient().when(inspection.getId()).thenReturn(inspectionId);
    lenient().when(inspection.getAcceptedAssignmentId()).thenReturn(assignmentId);
    lenient().when(inspection.getStatus()).thenReturn(InspectionStatus.IN_PROGRESS);
    lenient()
        .when(assignments.findByIdAndInspectorUserId(assignmentId, actorId))
        .thenReturn(Optional.of(assignment));
    lenient().when(assignment.getStatus()).thenReturn(InspectionAssignmentStatus.ACCEPTED);
    lenient().when(assignment.getInspectorUserId()).thenReturn(actorId);
    lenient()
        .when(evidence.findByInspectionIdAndChecksumSha256(any(), anyString()))
        .thenReturn(Optional.empty());
    lenient()
        .when(evidence.saveAndFlush(any(Evidence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void storesVerifiedBytesAndComputesChecksumWhenGpsIsMissing() throws Exception {
    var file = image(ONE_PIXEL_PNG);
    var metadata =
        new EvidenceUploadMetadataRequest(EvidenceSource.WEB_UPLOAD, null, null, null, null);
    var result = service.upload(actorId, inspectionId, file, metadata);

    assertThat(result.checksumSha256()).isEqualTo(sha256(ONE_PIXEL_PNG));
    assertThat(result.contentType()).isEqualTo("image/png");
    assertThat(result.sizeBytes()).isEqualTo(ONE_PIXEL_PNG.length);
    assertThat(result.source()).isEqualTo(EvidenceSource.WEB_UPLOAD);
    assertThat(result.uploadStatus()).isEqualTo(UploadStatus.AVAILABLE);
    assertThat(result.latitude()).isNull();
    assertThat(result.longitude()).isNull();
    verify(objectStore).put(anyString(), anyString(), anyLong(), any());
    verify(evidence).saveAndFlush(any(Evidence.class));
  }

  @Test
  void returnsExistingEvidenceForAnIdenticalRetryWithoutWritingAnotherObject() throws Exception {
    var file = image(ONE_PIXEL_PNG);
    var existing =
        new Evidence(
            inspectionId,
            null,
            actorId,
            com.smartdroneinspection.inspections.domain.enums.EvidenceKind.INSPECTION,
            "bridge.png",
            "image/png",
            ONE_PIXEL_PNG.length,
            sha256(ONE_PIXEL_PNG),
            "inspection/existing-object",
            EvidenceSource.WEB_UPLOAD,
            UploadStatus.AVAILABLE);
    when(evidence.findByInspectionIdAndChecksumSha256(inspectionId, sha256(ONE_PIXEL_PNG)))
        .thenReturn(Optional.of(existing));

    var result =
        service.upload(
            actorId,
            inspectionId,
            file,
            new EvidenceUploadMetadataRequest(EvidenceSource.WEB_UPLOAD, null, null, null, null));

    assertThat(result.checksumSha256()).isEqualTo(sha256(ONE_PIXEL_PNG));
    verify(objectStore, never()).put(anyString(), anyString(), anyLong(), any());
    verify(evidence, never()).saveAndFlush(any(Evidence.class));
  }

  @Test
  void rejectsUnsupportedOrCorruptContentBeforeStorage() throws Exception {
    var unsupported =
        new MockMultipartFile("file", "payload.txt", "text/plain", "not evidence".getBytes());
    var corruptImage = image("not a real png".getBytes(StandardCharsets.UTF_8));

    assertBusinessCode(
        () ->
            service.upload(
                actorId,
                inspectionId,
                unsupported,
                new EvidenceUploadMetadataRequest(
                    EvidenceSource.WEB_UPLOAD, null, null, null, null)),
        "EVIDENCE_INVALID");
    assertBusinessCode(
        () ->
            service.upload(
                actorId,
                inspectionId,
                corruptImage,
                new EvidenceUploadMetadataRequest(
                    EvidenceSource.WEB_UPLOAD, null, null, null, null)),
        "EVIDENCE_INVALID");
    verify(objectStore, never()).put(anyString(), anyString(), anyLong(), any());
  }

  @Test
  void rejectsOversizedEvidenceAndPartialGps() throws Exception {
    var tooLarge = image(new byte[2048]);
    var partialGps =
        new EvidenceUploadMetadataRequest(
            EvidenceSource.WEB_UPLOAD, Instant.now(), BigDecimal.valueOf(10), null, null);

    assertBusinessCode(
        () ->
            service.upload(
                actorId,
                inspectionId,
                tooLarge,
                new EvidenceUploadMetadataRequest(
                    EvidenceSource.WEB_UPLOAD, null, null, null, null)),
        "EVIDENCE_TOO_LARGE");
    assertBusinessCode(
        () -> service.upload(actorId, inspectionId, image(ONE_PIXEL_PNG), partialGps),
        "EVIDENCE_INVALID");
    verify(objectStore, never()).put(anyString(), anyString(), anyLong(), any());
  }

  @Test
  void deniesAnotherInspectorAndDoesNotStoreEvidence() throws Exception {
    UUID otherInspector = UUID.randomUUID();
    when(users.findActiveUser(otherInspector))
        .thenReturn(Optional.of(new UserAccess.ActiveUser(otherInspector, Set.of("INSPECTOR"))));
    when(inspections.findForUpdateByIdAndAuthorUserId(inspectionId, otherInspector))
        .thenReturn(Optional.empty());

    assertBusinessCode(
        () ->
            service.upload(
                otherInspector,
                inspectionId,
                image(ONE_PIXEL_PNG),
                new EvidenceUploadMetadataRequest(
                    EvidenceSource.WEB_UPLOAD, null, null, null, null)),
        "INSPECTION_SCOPE_DENIED");
    verify(objectStore, never()).put(anyString(), anyString(), anyLong(), any());
    verify(evidence, never()).saveAndFlush(any(Evidence.class));
  }

  @Test
  void storageFailureDoesNotPersistAvailableMetadata() throws Exception {
    doThrow(new IOException("storage unavailable"))
        .when(objectStore)
        .put(anyString(), anyString(), anyLong(), any());

    assertBusinessCode(
        () ->
            service.upload(
                actorId,
                inspectionId,
                image(ONE_PIXEL_PNG),
                new EvidenceUploadMetadataRequest(
                    EvidenceSource.WEB_UPLOAD, null, null, null, null)),
        "EVIDENCE_STORAGE_UNAVAILABLE");
    verify(evidence, never()).saveAndFlush(any(Evidence.class));
  }

  private MockMultipartFile image(byte[] bytes) {
    return new MockMultipartFile("file", "bridge.png", "image/png", bytes);
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }

  private void assertBusinessCode(Runnable action, String expectedCode) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(expectedCode));
  }
}

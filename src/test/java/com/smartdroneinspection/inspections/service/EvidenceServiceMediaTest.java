package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EvidenceServiceMediaTest {

  private static final UUID MANAGER_ID = UUID.randomUUID();
  private static final UUID INSPECTION_ID = UUID.randomUUID();
  private static final UUID EVIDENCE_ID = UUID.randomUUID();
  private static final String OBJECT_KEY = "evidence/primary.mp4";

  private EvidenceRepository evidence;
  private InspectionRepository inspections;
  private EvidenceObjectStore objectStore;
  private UserAccess userAccess;
  private EvidenceService service;
  private Evidence record;

  @BeforeEach
  void setUp() {
    evidence = mock(EvidenceRepository.class);
    inspections = mock(InspectionRepository.class);
    objectStore = mock(EvidenceObjectStore.class);
    userAccess = mock(UserAccess.class);
    service =
        new EvidenceService(
            inspections,
            evidence,
            mock(InspectionReportRepository.class),
            mock(FindingRepository.class),
            mock(FieldSessionRepository.class),
            Optional.of(objectStore),
            userAccess);

    UserAccess.ActiveUser manager =
        new UserAccess.ActiveUser(MANAGER_ID, Set.of(Roles.MANAGER), "Manager");
    Inspection inspection =
        new Inspection(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            Instant.parse("2027-01-01T08:00:00Z"),
            Instant.parse("2027-01-01T10:00:00Z"));
    ReflectionTestUtils.setField(inspection, "id", INSPECTION_ID);
    when(userAccess.findActiveUser(MANAGER_ID)).thenReturn(Optional.of(manager));
    when(inspections.findById(INSPECTION_ID)).thenReturn(Optional.of(inspection));

    record = mock(Evidence.class);
    when(record.isReady()).thenReturn(true);
    when(record.getObjectKey()).thenReturn(OBJECT_KEY);
    when(record.getContentType()).thenReturn("video/mp4");
    when(record.getFileName()).thenReturn("flight.mp4");
    when(record.getSizeBytes()).thenReturn(1000L);
    when(evidence.findByIdAndInspectionId(EVIDENCE_ID, INSPECTION_ID))
        .thenReturn(Optional.of(record));
  }

  @Test
  void resolvesPrimaryMediaUsingStoredMetadataSize() throws Exception {
    when(objectStore.stat(OBJECT_KEY))
        .thenReturn(new EvidenceObjectStore.ObjectMetadata(1200L, "video/mp4", "etag"));

    var descriptor =
        service.resolveMediaDescriptor(MANAGER_ID, INSPECTION_ID, EVIDENCE_ID, "primary", false);

    assertThat(descriptor.objectKey()).isEqualTo(OBJECT_KEY);
    assertThat(descriptor.totalBytes()).isEqualTo(1200L);
    assertThat(descriptor.fileName()).isEqualTo("flight.mp4");
    assertThat(descriptor.contentType()).isEqualTo("video/mp4");
  }

  @Test
  void fallsBackToKnownSizeWhenStatIsUnsupportedOrReportsZero() throws Exception {
    doThrow(new UnsupportedOperationException()).when(objectStore).stat(OBJECT_KEY);

    assertThat(
            service
                .resolveMediaDescriptor(MANAGER_ID, INSPECTION_ID, EVIDENCE_ID, "primary", false)
                .totalBytes())
        .isEqualTo(1000L);

    doReturn(new EvidenceObjectStore.ObjectMetadata(0L, "video/mp4", "etag"))
        .when(objectStore)
        .stat(OBJECT_KEY);
    assertThat(
            service
                .resolveMediaDescriptor(MANAGER_ID, INSPECTION_ID, EVIDENCE_ID, "primary", false)
                .totalBytes())
        .isEqualTo(1000L);
  }

  @Test
  void reportsStorageUnavailabilityWhenStatFailsOrSizeIsUnknown() throws Exception {
    doThrow(new IOException("minio down")).when(objectStore).stat(OBJECT_KEY);
    assertBusinessCode(
        () ->
            service.resolveMediaDescriptor(
                MANAGER_ID, INSPECTION_ID, EVIDENCE_ID, "primary", false),
        "EVIDENCE_STORAGE_UNAVAILABLE");

    when(record.getSizeBytes()).thenReturn(0L);
    doReturn(new EvidenceObjectStore.ObjectMetadata(0L, "video/mp4", "etag"))
        .when(objectStore)
        .stat(OBJECT_KEY);
    assertBusinessCode(
        () ->
            service.resolveMediaDescriptor(
                MANAGER_ID, INSPECTION_ID, EVIDENCE_ID, "primary", false),
        "EVIDENCE_STORAGE_UNAVAILABLE");
  }

  @Test
  void resolvesThumbnailsForStoredThumbnailAndImageFallback() throws Exception {
    when(record.getThumbnailObjectKey()).thenReturn("evidence/thumb.png");
    when(record.getThumbnailSizeBytes()).thenReturn(50L);
    when(objectStore.stat("evidence/thumb.png"))
        .thenReturn(new EvidenceObjectStore.ObjectMetadata(55L, "image/png", "etag"));

    var thumbnail =
        service.resolveMediaDescriptor(MANAGER_ID, INSPECTION_ID, EVIDENCE_ID, "thumb", false);
    assertThat(thumbnail.contentType()).isEqualTo("image/png");
    assertThat(thumbnail.totalBytes()).isEqualTo(55L);

    when(record.getThumbnailObjectKey()).thenReturn(null);
    when(record.isImage()).thenReturn(true);
    when(objectStore.stat(OBJECT_KEY))
        .thenReturn(new EvidenceObjectStore.ObjectMetadata(1000L, "video/mp4", "etag"));
    assertThat(
            service
                .resolveMediaDescriptor(MANAGER_ID, INSPECTION_ID, EVIDENCE_ID, "thumb", false)
                .objectKey())
        .isEqualTo(OBJECT_KEY);
  }

  @Test
  void rejectsMissingThumbnailAndRetiredOrUnreadyEvidence() {
    when(record.getThumbnailObjectKey()).thenReturn(null);
    when(record.isImage()).thenReturn(false);
    assertBusinessCode(
        () ->
            service.resolveMediaDescriptor(MANAGER_ID, INSPECTION_ID, EVIDENCE_ID, "thumb", false),
        "EVIDENCE_THUMBNAIL_UNAVAILABLE");

    when(record.isDeleted()).thenReturn(true);
    assertBusinessCode(
        () ->
            service.resolveMediaDescriptor(MANAGER_ID, INSPECTION_ID, EVIDENCE_ID, "primary", true),
        "EVIDENCE_RETIRED");

    when(record.isReady()).thenReturn(false);
    assertBusinessCode(
        () ->
            service.resolveMediaDescriptor(
                MANAGER_ID, INSPECTION_ID, EVIDENCE_ID, "primary", false),
        "EVIDENCE_NOT_READY");

    when(evidence.findByIdAndInspectionId(EVIDENCE_ID, INSPECTION_ID)).thenReturn(Optional.empty());
    assertBusinessCode(
        () ->
            service.resolveMediaDescriptor(
                MANAGER_ID, INSPECTION_ID, EVIDENCE_ID, "primary", false),
        "EVIDENCE_NOT_FOUND");
  }

  @Test
  void opensPrimaryStreamAndReportsStorageFailure() throws Exception {
    when(objectStore.stat(OBJECT_KEY))
        .thenReturn(new EvidenceObjectStore.ObjectMetadata(1000L, "video/mp4", "etag"));
    when(objectStore.open(OBJECT_KEY)).thenReturn(new ByteArrayInputStream(new byte[] {1, 2}));

    var content = service.open(MANAGER_ID, INSPECTION_ID, EVIDENCE_ID);

    assertThat(content.contentType()).isEqualTo("video/mp4");
    assertThat(content.stream().readAllBytes()).containsExactly(1, 2);

    when(objectStore.open(OBJECT_KEY)).thenThrow(new IOException("minio down"));
    assertBusinessCode(
        () -> service.open(MANAGER_ID, INSPECTION_ID, EVIDENCE_ID), "EVIDENCE_STORAGE_UNAVAILABLE");
  }

  @Test
  void opensRangedStreamOrFallsBackToBoundedPrimaryStream() throws Exception {
    when(objectStore.openRange(OBJECT_KEY, 1, 2))
        .thenReturn(new ByteArrayInputStream(new byte[] {2, 3}));
    assertThat(service.openRangeStream(OBJECT_KEY, 1, 2).readAllBytes()).containsExactly(2, 3);

    doThrow(new UnsupportedOperationException()).when(objectStore).openRange(OBJECT_KEY, 1, 2);
    when(objectStore.open(OBJECT_KEY))
        .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));
    InputStream fallback = service.openRangeStream(OBJECT_KEY, 1, 2);
    assertThat(fallback.readAllBytes()).containsExactly(2, 3);

    doThrow(new IOException("minio down")).when(objectStore).open(anyString());
    doThrow(new UnsupportedOperationException()).when(objectStore).openRange(OBJECT_KEY, 0, 1);
    assertBusinessCode(
        () -> service.openRangeStream(OBJECT_KEY, 0, 1), "EVIDENCE_STORAGE_UNAVAILABLE");
  }

  @Test
  void readsEvidenceForAnalysisAndRejectsRetiredOrUnreadyEvidence() throws Exception {
    when(objectStore.open(OBJECT_KEY)).thenReturn(new ByteArrayInputStream(new byte[] {5}));

    var content = service.readForAnalysis(INSPECTION_ID, EVIDENCE_ID);
    assertThat(content.stream().readAllBytes()).containsExactly(5);

    when(record.isRetired()).thenReturn(true);
    assertBusinessCode(
        () -> service.readForAnalysis(INSPECTION_ID, EVIDENCE_ID), "EVIDENCE_RETIRED");

    when(record.isRetired()).thenReturn(false);
    when(record.isReady()).thenReturn(false);
    assertBusinessCode(
        () -> service.readForAnalysis(INSPECTION_ID, EVIDENCE_ID), "EVIDENCE_NOT_READY");
  }

  @Test
  void listsEvidenceForInspectionAndFieldSession() {
    when(evidence.findByInspectionIdOrderByCreatedAtAsc(INSPECTION_ID))
        .thenReturn(java.util.List.of(record));
    when(evidence.findByInspectionIdAndFieldSessionIdAndIsDeletedFalseOrderByCreatedAtAsc(
            eq(INSPECTION_ID), any()))
        .thenReturn(java.util.List.of(record));

    assertThat(service.list(MANAGER_ID, INSPECTION_ID)).hasSize(1);
    assertThat(service.listForFieldSession(INSPECTION_ID, UUID.randomUUID())).hasSize(1);
  }

  private static void assertBusinessCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
    assertThatThrownBy(callable)
        .isInstanceOfSatisfying(
            BusinessException.class, exception -> assertThat(exception.code()).isEqualTo(code));
  }
}

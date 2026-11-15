package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.EvidenceUploadSession;
import com.smartdroneinspection.inspections.domain.enums.EvidenceProcessingStatus;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.EvidenceUploadSessionRepository;
import com.smartdroneinspection.inspections.spi.VideoTranscodePort;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class VideoProcessingWorkerTest {

  @TempDir Path tempDir;

  @Test
  void deletesRawOnlyAfterVerifiedOutputsAreStoredAndCommittedAsReady() throws Exception {
    EvidenceRepository evidenceRepo = mock(EvidenceRepository.class);
    EvidenceUploadSessionRepository sessionRepo = mock(EvidenceUploadSessionRepository.class);
    EvidenceObjectStore store = mock(EvidenceObjectStore.class);
    VideoTranscodePort transcoder = mock(VideoTranscodePort.class);

    UUID inspectionId = UUID.randomUUID();
    Evidence evidence =
        new Evidence(
            inspectionId,
            null,
            "flight.mp4",
            "video/mp4",
            1000L,
            "inspections/" + inspectionId + "/raw/flight.mp4",
            null,
            null,
            null,
            null,
            Instant.now());
    UUID evidenceId = UUID.randomUUID();
    setEvidenceId(evidence, evidenceId);
    evidence.markVideoPending("inspections/" + inspectionId + "/raw/flight.mp4", 1000L);

    EvidenceUploadSession session = mock(EvidenceUploadSession.class);
    when(evidenceRepo.claimForProcessing(
            eq(evidenceId),
            eq(EvidenceProcessingStatus.PENDING),
            eq(EvidenceProcessingStatus.PROCESSING),
            any(),
            any()))
        .thenReturn(1);
    when(evidenceRepo.findById(evidenceId)).thenReturn(Optional.of(evidence));
    when(sessionRepo.findByEvidenceId(evidenceId)).thenReturn(Optional.of(session));
    when(store.open(evidence.getRawObjectKey()))
        .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

    Path outVideo = tempDir.resolve("out.mp4");
    Path outPoster = tempDir.resolve("poster.jpg");
    Files.write(outVideo, new byte[] {4, 5, 6, 7});
    Files.write(outPoster, new byte[] {8, 9});
    when(transcoder.transcodeTo1080pFaststart(any(), any()))
        .thenReturn(
            new VideoTranscodePort.TranscodeResult(
                outVideo, "video/mp4", 4L, outPoster, "image/jpeg", 2L, 1920, 1080, 12.5));

    String processedKey = "inspections/" + inspectionId + "/evidence/" + evidenceId + "/video.mp4";
    String posterKey = "inspections/" + inspectionId + "/evidence/" + evidenceId + "/poster.jpg";
    when(store.stat(processedKey))
        .thenReturn(new EvidenceObjectStore.ObjectMetadata(4L, "video/mp4", "e1"));
    when(store.stat(posterKey))
        .thenReturn(new EvidenceObjectStore.ObjectMetadata(2L, "image/jpeg", "e2"));

    VideoProcessingWorker worker =
        new VideoProcessingWorker(
            evidenceRepo,
            sessionRepo,
            Optional.of(store),
            Optional.of(transcoder),
            noopTxManager(),
            1);

    worker.processSingle(evidenceId);

    InOrder order = inOrder(store, evidenceRepo, session);
    order.verify(store).put(eq(processedKey), eq("video/mp4"), eq(4L), any(InputStream.class));
    order.verify(store).put(eq(posterKey), eq("image/jpeg"), eq(2L), any(InputStream.class));
    order.verify(store).stat(processedKey);
    order.verify(store).stat(posterKey);
    order.verify(evidenceRepo).saveAndFlush(evidence);
    order.verify(session).markReady(any());
    order.verify(store).delete("inspections/" + inspectionId + "/raw/flight.mp4");
    order.verify(evidenceRepo).saveAndFlush(evidence);

    assertThat(evidence.getProcessingStatus()).isEqualTo(EvidenceProcessingStatus.READY);
    assertThat(evidence.getObjectKey()).isEqualTo(processedKey);
    assertThat(evidence.getThumbnailObjectKey()).isEqualTo(posterKey);
    assertThat(evidence.getRawObjectKey()).isNull();
    assertThat(evidence.getOriginalSizeBytes()).isEqualTo(1000L);
    assertThat(evidence.getSizeBytes()).isEqualTo(4L);
  }

  @Test
  void retainsRawSourceAndCleansPartialOutputsWhenTranscodingFails() throws Exception {
    EvidenceRepository evidenceRepo = mock(EvidenceRepository.class);
    EvidenceUploadSessionRepository sessionRepo = mock(EvidenceUploadSessionRepository.class);
    EvidenceObjectStore store = mock(EvidenceObjectStore.class);
    VideoTranscodePort transcoder = mock(VideoTranscodePort.class);

    UUID inspectionId = UUID.randomUUID();
    String rawKey = "inspections/" + inspectionId + "/raw/corrupt.mp4";
    Evidence evidence =
        new Evidence(
            inspectionId,
            null,
            "corrupt.mp4",
            "video/mp4",
            500L,
            rawKey,
            null,
            null,
            null,
            null,
            Instant.now());
    UUID evidenceId = UUID.randomUUID();
    setEvidenceId(evidence, evidenceId);
    evidence.markVideoPending(rawKey, 500L);

    EvidenceUploadSession session = mock(EvidenceUploadSession.class);
    when(evidenceRepo.claimForProcessing(
            eq(evidenceId),
            eq(EvidenceProcessingStatus.PENDING),
            eq(EvidenceProcessingStatus.PROCESSING),
            any(),
            any()))
        .thenReturn(1);
    when(evidenceRepo.findById(evidenceId)).thenReturn(Optional.of(evidence));
    when(sessionRepo.findByEvidenceId(evidenceId)).thenReturn(Optional.of(session));
    when(store.open(rawKey)).thenReturn(new ByteArrayInputStream(new byte[] {1, 2}));
    when(transcoder.transcodeTo1080pFaststart(any(), any()))
        .thenThrow(new IOException("ffmpeg decode failure"));

    VideoProcessingWorker worker =
        new VideoProcessingWorker(
            evidenceRepo,
            sessionRepo,
            Optional.of(store),
            Optional.of(transcoder),
            noopTxManager(),
            1);

    worker.processSingle(evidenceId);

    verify(store, never()).delete(rawKey);
    verify(store, never()).put(anyString(), anyString(), anyLong(), any(InputStream.class));
    assertThat(evidence.getProcessingStatus()).isEqualTo(EvidenceProcessingStatus.FAILED);
    assertThat(evidence.getProcessingError()).contains("ffmpeg decode failure");
    assertThat(evidence.getRawObjectKey()).isEqualTo(rawKey);
    verify(session).markFailed(anyString(), any());
  }

  private static PlatformTransactionManager noopTxManager() {
    return new PlatformTransactionManager() {
      @Override
      public TransactionStatus getTransaction(TransactionDefinition definition) {
        return new SimpleTransactionStatus();
      }

      @Override
      public void commit(TransactionStatus status) {}

      @Override
      public void rollback(TransactionStatus status) {}
    };
  }

  private static void setEvidenceId(Evidence entity, UUID id) throws Exception {
    var field = Evidence.class.getDeclaredField("id");
    field.setAccessible(true);
    field.set(entity, id);
  }
}

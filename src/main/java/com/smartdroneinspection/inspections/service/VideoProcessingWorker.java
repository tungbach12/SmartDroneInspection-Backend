package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.EvidenceUploadSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.EvidenceProcessingStatus;
import com.smartdroneinspection.inspections.events.VideoProcessingFinishedEvent;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.EvidenceUploadSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.VideoTranscodePort;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durable asynchronous video transcoding worker.
 *
 * <p>Pending work is persisted on {@link Evidence} rows so process restarts do not lose queued
 * videos. Raw source objects are retained until both processed 1080p MP4 and 480 px poster outputs
 * are durably stored, verified via {@link EvidenceObjectStore#stat(String)}, and committed to the
 * database.
 */
@Service
public class VideoProcessingWorker {

  private static final Logger LOG = LoggerFactory.getLogger(VideoProcessingWorker.class);
  private static final Duration STALE_LOCK_WINDOW = Duration.ofMinutes(20);

  private final EvidenceRepository evidenceRepository;
  private final EvidenceUploadSessionRepository sessionRepository;
  private final Optional<EvidenceObjectStore> objectStore;
  private final Optional<VideoTranscodePort> transcoder;
  private final TransactionTemplate requiresNewTx;
  private final Semaphore concurrencyLimit;
  private final ExecutorService executor;
  private final Set<UUID> activeIds = ConcurrentHashMap.newKeySet();
  private InspectionRepository inspectionRepository;
  private ApplicationEventPublisher eventPublisher;

  @Autowired(required = false)
  public void setNotificationDependencies(
      InspectionRepository inspectionRepository, ApplicationEventPublisher eventPublisher) {
    this.inspectionRepository = inspectionRepository;
    this.eventPublisher = eventPublisher;
  }

  public VideoProcessingWorker(
      EvidenceRepository evidenceRepository,
      EvidenceUploadSessionRepository sessionRepository,
      Optional<EvidenceObjectStore> objectStore,
      Optional<VideoTranscodePort> transcoder,
      PlatformTransactionManager transactionManager,
      @Value("${app.evidence.processing.max-concurrent-jobs:2}") int maxConcurrentJobs) {
    this.evidenceRepository = evidenceRepository;
    this.sessionRepository = sessionRepository;
    this.objectStore = objectStore;
    this.transcoder = transcoder;
    this.requiresNewTx = new TransactionTemplate(transactionManager);
    this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    int permits = Math.max(1, maxConcurrentJobs);
    this.concurrencyLimit = new Semaphore(permits);
    this.executor = Executors.newVirtualThreadPerTaskExecutor();
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }

  public void scheduleAfterCommit(UUID evidenceId) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              dispatchAsync(evidenceId);
            }
          });
    } else {
      dispatchAsync(evidenceId);
    }
  }

  @Scheduled(fixedDelayString = "${app.evidence.processing.poll-interval:15000}")
  public void pollPendingVideos() {
    if (objectStore.isEmpty() || transcoder.isEmpty()) {
      return;
    }
    Instant staleBefore = Instant.now().minus(STALE_LOCK_WINDOW);
    List<Evidence> due =
        evidenceRepository.findDueForProcessing(
            EvidenceProcessingStatus.PENDING,
            EvidenceProcessingStatus.PROCESSING,
            staleBefore,
            PageRequest.of(0, 10));
    for (Evidence item : due) {
      dispatchAsync(item.getId());
    }
  }

  void dispatchAsync(UUID evidenceId) {
    if (!activeIds.add(evidenceId)) {
      return;
    }
    executor.submit(
        () -> {
          try {
            concurrencyLimit.acquire();
            try {
              processSingle(evidenceId);
            } finally {
              concurrencyLimit.release();
            }
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          } finally {
            activeIds.remove(evidenceId);
          }
        });
  }

  /** Processes one evidence item synchronously (used by worker threads and tests). */
  public void processSingle(UUID evidenceId) {
    Instant now = Instant.now();
    Instant staleBefore = now.minus(STALE_LOCK_WINDOW);
    ClaimedJob job =
        requiresNewTx.execute(
            status -> {
              int claimed =
                  evidenceRepository.claimForProcessing(
                      evidenceId,
                      EvidenceProcessingStatus.PENDING,
                      EvidenceProcessingStatus.PROCESSING,
                      now,
                      staleBefore);
              if (claimed == 0) {
                return null;
              }
              return evidenceRepository
                  .findById(evidenceId)
                  .map(
                      ev ->
                          new ClaimedJob(
                              ev.getId(),
                              ev.getInspectionId(),
                              ev.getRawObjectKey() != null && !ev.getRawObjectKey().isBlank()
                                  ? ev.getRawObjectKey()
                                  : ev.getObjectKey()))
                  .orElse(null);
            });
    if (job == null) {
      return;
    }

    if (objectStore.isEmpty() || transcoder.isEmpty()) {
      markFailed(job.evidenceId(), "Video transcoding dependencies are not configured");
      return;
    }

    EvidenceObjectStore store = objectStore.get();
    VideoTranscodePort processor = transcoder.get();
    String processedKey =
        "inspections/" + job.inspectionId() + "/evidence/" + job.evidenceId() + "/video.mp4";
    String posterKey =
        "inspections/" + job.inspectionId() + "/evidence/" + job.evidenceId() + "/poster.jpg";

    Path workDir = null;
    boolean uploadedProcessed = false;
    boolean uploadedPoster = false;
    try {
      workDir = Files.createTempDirectory("sdi-video-" + job.evidenceId() + "-");
      Path rawFile = workDir.resolve("raw.mp4");
      try (InputStream rawStream = store.open(job.rawObjectKey())) {
        Files.copy(rawStream, rawFile, StandardCopyOption.REPLACE_EXISTING);
      }

      VideoTranscodePort.TranscodeResult result =
          processor.transcodeTo1080pFaststart(rawFile, workDir);

      try (InputStream videoIn = Files.newInputStream(result.processedVideoFile())) {
        store.put(
            processedKey, result.processedContentType(), result.processedSizeBytes(), videoIn);
        uploadedProcessed = true;
      }

      try (InputStream posterIn = Files.newInputStream(result.posterFile())) {
        store.put(posterKey, result.posterContentType(), result.posterSizeBytes(), posterIn);
        uploadedPoster = true;
      }

      EvidenceObjectStore.ObjectMetadata videoStat = store.stat(processedKey);
      EvidenceObjectStore.ObjectMetadata posterStat = store.stat(posterKey);
      if (videoStat.sizeBytes() <= 0 || posterStat.sizeBytes() <= 0) {
        throw new IOException("Stored video or poster rendition failed size verification");
      }

      Instant completedAt = Instant.now();
      requiresNewTx.executeWithoutResult(
          status -> {
            Evidence entity =
                evidenceRepository
                    .findById(job.evidenceId())
                    .orElseThrow(() -> new IllegalStateException("Evidence disappeared"));
            entity.markVideoReady(
                processedKey,
                videoStat.sizeBytes(),
                posterKey,
                posterStat.sizeBytes(),
                result.width(),
                result.height(),
                result.durationSeconds(),
                completedAt);
            evidenceRepository.saveAndFlush(entity);

            sessionRepository
                .findByEvidenceId(job.evidenceId())
                .ifPresent(
                    session -> {
                      session.markReady(completedAt);
                      sessionRepository.saveAndFlush(session);
                    });
            publishVideoFinishedEvent(
                entity.getId(),
                entity.getInspectionId(),
                entity.getFileName(),
                true,
                null,
                completedAt);
          });

      // Only delete raw after processed + poster are durably stored, verified, and DB committed.
      if (job.rawObjectKey() != null && !job.rawObjectKey().equals(processedKey)) {
        try {
          store.delete(job.rawObjectKey());
          requiresNewTx.executeWithoutResult(
              status ->
                  evidenceRepository
                      .findById(job.evidenceId())
                      .ifPresent(
                          entity -> {
                            entity.clearRawObjectKey();
                            evidenceRepository.saveAndFlush(entity);
                          }));
        } catch (Exception deleteEx) {
          LOG.warn(
              "Video {} is READY but raw cleanup failed for key {}",
              job.evidenceId(),
              job.rawObjectKey(),
              deleteEx);
        }
      }
    } catch (Exception ex) {
      LOG.warn("Video processing failed for evidence {}", job.evidenceId(), ex);
      if (uploadedProcessed) {
        deleteQuietly(store, processedKey);
      }
      if (uploadedPoster) {
        deleteQuietly(store, posterKey);
      }
      markFailed(job.evidenceId(), ex.getMessage());
    } finally {
      deleteDirectoryQuietly(workDir);
    }
  }

  private void markFailed(UUID evidenceId, String message) {
    String safeMessage =
        message == null || message.isBlank() ? "Video transcoding failed" : message.trim();
    if (safeMessage.length() > 1000) {
      safeMessage = safeMessage.substring(0, 1000);
    }
    String finalMessage = safeMessage;
    Instant now = Instant.now();
    requiresNewTx.executeWithoutResult(
        status -> {
          evidenceRepository
              .findById(evidenceId)
              .ifPresent(
                  entity -> {
                    entity.markProcessingFailed(finalMessage);
                    evidenceRepository.saveAndFlush(entity);
                    publishVideoFinishedEvent(
                        entity.getId(),
                        entity.getInspectionId(),
                        entity.getFileName(),
                        false,
                        finalMessage,
                        now);
                  });
          Optional<EvidenceUploadSession> session = sessionRepository.findByEvidenceId(evidenceId);
          session.ifPresent(
              s -> {
                s.markFailed(finalMessage, now);
                sessionRepository.saveAndFlush(s);
              });
        });
  }

  private void publishVideoFinishedEvent(
      UUID evidenceId,
      UUID inspectionId,
      String fileName,
      boolean success,
      String errorMessage,
      Instant finishedAt) {
    if (eventPublisher == null || inspectionId == null) {
      return;
    }
    UUID inspectorId =
        inspectionRepository != null
            ? inspectionRepository
                .findById(inspectionId)
                .map(Inspection::getInspectorId)
                .orElse(null)
            : null;
    if (inspectorId == null) {
      return;
    }
    eventPublisher.publishEvent(
        new VideoProcessingFinishedEvent(
            evidenceId, inspectionId, inspectorId, fileName, success, errorMessage, finishedAt));
  }

  private static void deleteQuietly(EvidenceObjectStore store, String key) {
    try {
      store.delete(key);
    } catch (Exception ignored) {
      // Best-effort cleanup of partial output on failure; raw source remains intact.
    }
  }

  private static void deleteDirectoryQuietly(Path dir) {
    if (dir == null || !Files.exists(dir)) {
      return;
    }
    try (var walk = Files.walk(dir)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
              });
    } catch (IOException ignored) {
    }
  }

  private record ClaimedJob(UUID evidenceId, UUID inspectionId, String rawObjectKey) {}
}

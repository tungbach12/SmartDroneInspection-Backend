package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.request.AnalyzeVideoRequest;
import com.smartdroneinspection.inspections.api.dto.request.FindingSourceFilter;
import com.smartdroneinspection.inspections.api.dto.request.FindingStatusFilter;
import com.smartdroneinspection.inspections.api.dto.request.ManualFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.PromoteVideoKeyframesRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReviewFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.UpdateFindingRequest;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceResponse;
import com.smartdroneinspection.inspections.api.dto.response.FindingPageResponse;
import com.smartdroneinspection.inspections.api.dto.response.FindingResponse;
import com.smartdroneinspection.inspections.api.dto.response.VideoAutoDetectResponse;
import com.smartdroneinspection.inspections.api.dto.response.VideoScanResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.domain.enums.FindingStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.AiInferencePort;
import com.smartdroneinspection.inspections.spi.VideoTranscodePort;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MF3-05/06/09: advisory detection and the Inspector's finding decisions.
 *
 * <p>A finding is one observation, whoever made it. The detector proposes, the Inspector records
 * what they saw, and the question the workflow asks of both is the same: has the Inspector
 * confirmed it. A finding that has not been confirmed is not official, so it does not enter a
 * report and it does not carry a corrective outcome.
 *
 * <p>Removing a finding hides it rather than erasing it, and it can be brought back. An inference
 * outage degrades the workflow rather than stopping it - the evidence stays usable and the manual
 * path stays open.
 */
@Service
public class FindingService {

  private final EvidenceRepository evidence;
  private final FindingRepository findings;
  private final EvidenceService evidenceService;
  private final InspectionReportRepository reportVersions;
  private final InspectionRepository inspections;
  private final Optional<AiInferencePort> inference;
  private final UserAccess userAccess;
  private final Optional<PlatformAiSettingsService> aiSettingsService;
  private final Optional<VideoTranscodePort> videoTranscoder;

  public FindingService(
      EvidenceRepository evidence,
      FindingRepository findings,
      EvidenceService evidenceService,
      InspectionReportRepository reportVersions,
      InspectionRepository inspections,
      Optional<AiInferencePort> inference,
      UserAccess userAccess) {
    this(
        evidence,
        findings,
        evidenceService,
        reportVersions,
        inspections,
        inference,
        userAccess,
        Optional.empty(),
        Optional.empty());
  }

  public FindingService(
      EvidenceRepository evidence,
      FindingRepository findings,
      EvidenceService evidenceService,
      InspectionReportRepository reportVersions,
      InspectionRepository inspections,
      Optional<AiInferencePort> inference,
      UserAccess userAccess,
      Optional<PlatformAiSettingsService> aiSettingsService) {
    this(
        evidence,
        findings,
        evidenceService,
        reportVersions,
        inspections,
        inference,
        userAccess,
        aiSettingsService,
        Optional.empty());
  }

  @org.springframework.beans.factory.annotation.Autowired
  public FindingService(
      EvidenceRepository evidence,
      FindingRepository findings,
      EvidenceService evidenceService,
      InspectionReportRepository reportVersions,
      InspectionRepository inspections,
      Optional<AiInferencePort> inference,
      UserAccess userAccess,
      Optional<PlatformAiSettingsService> aiSettingsService,
      Optional<VideoTranscodePort> videoTranscoder) {
    this.evidence = evidence;
    this.findings = findings;
    this.evidenceService = evidenceService;
    this.reportVersions = reportVersions;
    this.inspections = inspections;
    this.inference = inference;
    this.userAccess = userAccess;
    this.aiSettingsService = aiSettingsService;
    this.videoTranscoder = videoTranscoder;
  }

  /**
   * MF3-06: explicitly confirm the resolved findings set before entering or refreshing report
   * drafting.
   */
  @Transactional
  public void confirmFindings(UUID actorId, UUID inspectionId, boolean noFindingsAcknowledged) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    if (inspection.getStatus()
        != com.smartdroneinspection.inspections.domain.enums.InspectionStatus.FIELD_COMPLETED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INSPECTION_STATE_CONFLICT",
          "Findings can only be confirmed after field work is completed");
    }
    boolean hasActiveEvidence = evidence.existsByInspectionIdAndIsDeletedFalse(inspectionId);
    if (!hasActiveEvidence) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "ACTIVE_EVIDENCE_REQUIRED",
          "Upload at least one active evidence item before confirming the findings set");
    }
    // A detector suggestion is a proposal the Inspector has to accept or remove, so it blocks
    // until it is decided. A finding the Inspector recorded themselves is not a proposal: the
    // aggregate confirmation below is the only step it still needs.
    long undecidedSuggestions =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .filter(finding -> finding.getStatus() == FindingStatus.PENDING)
            .filter(finding -> finding.getModelName() != null)
            .count();
    if (undecidedSuggestions > 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PENDING_FINDINGS_EXIST",
          "Confirm or remove every suggested finding before confirming the set");
    }
    List<Finding> inspectorFindings = findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId);
    long findingCount = inspectorFindings.size();
    if (findingCount == 0 && !noFindingsAcknowledged) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "NO_FINDINGS_ACKNOWLEDGMENT_REQUIRED",
          "Confirm that no finding was observed within the inspected scope");
    }
    // The aggregate confirmation is what makes the set official, so a finding that has not been
    // confirmed yet is confirmed here.
    for (Finding finding : inspectorFindings) {
      finding.confirmByInspector();
    }
    if (!inspectorFindings.isEmpty()) {
      findings.saveAllAndFlush(inspectorFindings);
    }
    if (inspection.getStatus()
            == com.smartdroneinspection.inspections.domain.enums.InspectionStatus.FIELD_COMPLETED
        && inspection.hasFindingsConfirmation()) {
      return;
    }
    try {
      inspection.confirmFindings();
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INSPECTION_STATE_CONFLICT", ex.getMessage());
    }
    inspections.saveAndFlush(inspection);
  }

  @Transactional
  public void reopenFindings(UUID actorId, UUID inspectionId) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectFinalizedReportFindingEdits(inspectionId);
    if (inspection.getStatus()
            != com.smartdroneinspection.inspections.domain.enums.InspectionStatus.FIELD_COMPLETED
        || !inspection.hasFindingsConfirmation()) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INSPECTION_STATE_CONFLICT", "Findings are not currently confirmed");
    }
    if (inspection.hasFindingsConfirmation()) {
      findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
          .filter(Finding::isOfficial)
          .forEach(Finding::clearInspectorConfirmation);
      findings.flush();
    }
    inspection.reopenFindings();
    inspections.saveAndFlush(inspection);
  }

  /** MF3-05: run advisory detection. Refuses evidence the Inspector has not accepted. */
  @Transactional
  public List<FindingResponse> analyze(
      UUID actorId, UUID inspectionId, UUID evidenceId, boolean replace) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);
    if (inspection.getStatus() == InspectionStatus.SCHEDULED
        || inspection.getStatus() == InspectionStatus.IN_PROGRESS
        || inspection.getStatus() == InspectionStatus.CANCELLED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINDINGS_LOCKED",
          "Analysis is only available during defect review or report drafting");
    }

    Evidence record = requireEvidence(inspectionId, evidenceId);
    if (!record.isImage()) {
      throw new BusinessException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "AI_INFERENCE_UNAVAILABLE",
          "Only image evidence can be analyzed automatically");
    }
    AiInferencePort port =
        inference.orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_INFERENCE_UNAVAILABLE",
                    "Automated analysis is not configured"));

    // MF3-05 is invoked explicitly by the Inspector; re-running the same capture must not create a
    // second finding for the same detection. An explicit re-run replaces
    // the image's unreviewed suggestions instead, which is what a changed detector or threshold
    // needs; a finding the Inspector already reviewed carries that decision and survives.
    List<Finding> prior = findings.findByEvidenceIdOrderByCreatedAtAsc(evidenceId);
    if (!replace && !prior.isEmpty()) {
      return prior.stream().map(this::toFindingResponse).toList();
    }
    List<Finding> reviewed =
        replace
            ? prior.stream()
                .filter(finding -> finding.getStatus() != FindingStatus.PENDING)
                .toList()
            : List.of();

    byte[] bytes;
    try (InputStream stream = evidenceService.readForAnalysis(inspectionId, evidenceId).stream()) {
      bytes = stream.readAllBytes();
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Evidence storage is unavailable");
    }

    List<AiInferencePort.Detection> detections;
    try {
      detections =
          userAccess.isVisionFallbackEnabled(actorId)
              ? port.analyze(bytes, record.getContentType())
              : port.analyze(bytes, record.getContentType(), false);
    } catch (IOException ex) {
      // Evidence survives an inference outage, and the manual path stays available.
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "AI_INFERENCE_UNAVAILABLE",
          "Automated analysis is temporarily unavailable");
    }

    if (replace) {
      // Only after the new detections exist, so a failed inference leaves the previous suggestions
      // in place rather than emptying the image.
      List<Finding> replaced =
          prior.stream().filter(finding -> finding.getStatus() == FindingStatus.PENDING).toList();
      if (!replaced.isEmpty()) {
        findings.deleteAll(replaced);
        findings.flush();
      }
    }

    long nextSeq = findings.findMaxFindingCodeNumberByInspectionId(inspectionId) + 1;
    List<Finding> created = new java.util.ArrayList<>();
    PlatformAiSettingsService.AiThresholdConfig thresholdConfig =
        aiSettingsService.map(PlatformAiSettingsService::getThresholdConfig).orElse(null);
    for (AiInferencePort.Detection detection : detections) {
      if (isDuplicateConfirmedDetection(detection, reviewed)) {
        continue;
      }
      Finding createdFinding =
          new Finding(
              inspectionId,
              evidenceId,
              "F-" + (nextSeq++),
              detection.predictedLabel(),
              FindingSeverity.MEDIUM,
              detection.predictedLabel(),
              detection.boundingBox(),
              detection.modelName(),
              detection.confidence());
      if (aiSettingsService
          .map(s -> s.shouldAutoConfirm(detection.confidence(), thresholdConfig))
          .orElse(false)) {
        createdFinding.confirmByInspector();
      }
      created.add(createdFinding);
    }
    List<Finding> stored = findings.saveAllAndFlush(created);
    syncFindingsConfirmationAfterEdit(inspection);
    return Stream.concat(stored.stream(), reviewed.stream()).map(this::toFindingResponse).toList();
  }

  /**
   * Step A of Inspector-assisted video defect extraction: samples candidate frames from the stored
   * flight video using FFmpeg and runs YOLO11 detection without writing child Evidence or Findings
   * yet.
   */
  @Transactional(readOnly = true)
  public VideoScanResponse scanVideo(
      UUID actorId,
      UUID inspectionId,
      UUID videoEvidenceId,
      double intervalSeconds,
      int maxFrames) {
    Inspection inspection = requireVideoAnalysisEligible(actorId, inspectionId);
    Evidence videoRecord = requireVideoEvidence(inspectionId, videoEvidenceId);
    AiInferencePort port = requireInferencePort();
    VideoTranscodePort transcoder =
        videoTranscoder.orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "VIDEO_SCAN_UNAVAILABLE",
                    "Video frame extraction is not configured"));

    Path workDir = null;
    try {
      workDir = Files.createTempDirectory("sdi-vscan-" + videoEvidenceId + "-");
      Path videoFile = workDir.resolve("source.mp4");
      evidenceService.copyStoredObjectToFile(videoRecord, videoFile);

      List<VideoTranscodePort.SampledFrame> sampled =
          transcoder.sampleFrames(videoFile, workDir, intervalSeconds, maxFrames);
      List<VideoScanResponse.DetectedMarker> markers = new java.util.ArrayList<>();
      double lastAcceptedTimestamp = -999.0;
      String lastAcceptedPrimaryLabel = "";

      for (VideoTranscodePort.SampledFrame frame : sampled) {
        byte[] frameBytes = Files.readAllBytes(frame.imageFile());
        List<AiInferencePort.Detection> detections;
        try {
          detections = port.analyze(frameBytes, "image/jpeg", false);
        } catch (IOException ex) {
          continue;
        }
        if (detections == null || detections.isEmpty()) {
          continue;
        }
        String primaryLabel = detections.getFirst().predictedLabel();
        double ts = Math.max(0.0, frame.timestampSeconds());
        if (Math.abs(ts - lastAcceptedTimestamp) < 1.2
            && primaryLabel != null
            && primaryLabel.equalsIgnoreCase(lastAcceptedPrimaryLabel)) {
          continue;
        }
        lastAcceptedTimestamp = ts;
        lastAcceptedPrimaryLabel = primaryLabel != null ? primaryLabel : "";

        int width = videoRecord.getMediaWidth() != null ? videoRecord.getMediaWidth() : 0;
        int height = videoRecord.getMediaHeight() != null ? videoRecord.getMediaHeight() : 0;
        try (var bais = new java.io.ByteArrayInputStream(frameBytes)) {
          var decoded = ImageIO.read(bais);
          if (decoded != null) {
            width = decoded.getWidth();
            height = decoded.getHeight();
          }
        } catch (IOException ignored) {
        }

        BigDecimal maxConf =
            detections.stream()
                .map(AiInferencePort.Detection::confidence)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        List<VideoScanResponse.MarkerDetection> markerDetections =
            detections.stream()
                .map(
                    d ->
                        new VideoScanResponse.MarkerDetection(
                            d.modelName(),
                            d.modelVersion(),
                            d.predictedLabel(),
                            d.confidence(),
                            d.boundingBox()))
                .toList();

        String dataUri =
            "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(frameBytes);

        markers.add(
            new VideoScanResponse.DetectedMarker(
                ts, width, height, dataUri, primaryLabel, maxConf, markerDetections));
      }

      double duration =
          videoRecord.getDurationSeconds() != null ? videoRecord.getDurationSeconds() : 0.0;
      return new VideoScanResponse(videoRecord.getId(), duration, sampled.size(), markers);
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "VIDEO_SCAN_FAILED",
          "Could not scan video frames for automated defect detection");
    } finally {
      cleanupTempDirectory(workDir);
    }
  }

  /**
   * Step B: promotes Inspector-selected timeline timestamps/cropped frames into linked child Image
   * Evidence items (back-referencing the source video & second) and AI Findings.
   */
  @Transactional
  public VideoAutoDetectResponse promoteVideoKeyframes(
      UUID actorId, UUID inspectionId, UUID videoEvidenceId, PromoteVideoKeyframesRequest request) {
    Inspection inspection = requireVideoAnalysisEligible(actorId, inspectionId);
    Evidence videoRecord = requireVideoEvidence(inspectionId, videoEvidenceId);
    List<PromoteVideoKeyframesRequest.KeyframeSelection> selections =
        request != null && request.keyframes() != null ? request.keyframes() : List.of();
    if (selections.isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "VIDEO_FRAMES_REQUIRED",
          "Select at least one video keyframe to promote into evidence");
    }

    long[] nextSeq = new long[] {findings.findMaxFindingCodeNumberByInspectionId(inspectionId) + 1};
    List<VideoAutoDetectResponse.ExtractedDefectFrame> extractedKeyframes =
        new java.util.ArrayList<>();
    int[] totalCreatedFindings = new int[] {0};

    Path workDir = null;
    Path cachedVideoFile = null;
    try {
      for (PromoteVideoKeyframesRequest.KeyframeSelection selection : selections) {
        if (selection == null) {
          continue;
        }
        double ts = Math.max(0.0, selection.timestampSeconds());
        byte[] frameBytes = decodeBase64FrameOrNull(selection.imageBase64());
        if (frameBytes == null) {
          VideoTranscodePort transcoder =
              videoTranscoder.orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.SERVICE_UNAVAILABLE,
                          "VIDEO_SCAN_UNAVAILABLE",
                          "Video frame extraction is not configured"));
          if (workDir == null) {
            workDir = Files.createTempDirectory("sdi-vpromote-" + videoEvidenceId + "-");
            cachedVideoFile = workDir.resolve("source.mp4");
            evidenceService.copyStoredObjectToFile(videoRecord, cachedVideoFile);
          }
          frameBytes = transcoder.extractFrameAtTimestamp(cachedVideoFile, ts, workDir);
        }

        List<AiInferencePort.Detection> detections;
        if (selection.detections() != null && !selection.detections().isEmpty()) {
          detections =
              selection.detections().stream()
                  .filter(
                      d -> d != null && d.predictedLabel() != null && !d.predictedLabel().isBlank())
                  .map(
                      d ->
                          new AiInferencePort.Detection(
                              "YOLO11",
                              orDefault(d.modelName(), "yolo11s"),
                              null,
                              d.predictedLabel(),
                              d.confidence(),
                              d.boundingBox()))
                  .toList();
        } else {
          AiInferencePort port = requireInferencePort();
          detections = port.analyze(frameBytes, "image/jpeg", false);
        }

        if (detections == null) {
          detections = List.of();
        }
        extractedKeyframes.add(
            persistVideoKeyframeAndFindings(
                actorId,
                inspection,
                videoRecord,
                ts,
                selection.fileName(),
                selection.locationDescription(),
                frameBytes,
                detections,
                nextSeq,
                totalCreatedFindings));
      }
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "VIDEO_KEYFRAME_EXTRACTION_FAILED",
          "Could not extract selected keyframe from flight video");
    } finally {
      cleanupTempDirectory(workDir);
    }

    if (!extractedKeyframes.isEmpty()) {
      syncFindingsConfirmationAfterEdit(inspection);
    }
    return new VideoAutoDetectResponse(
        videoEvidenceId,
        selections.size(),
        extractedKeyframes.size(),
        totalCreatedFindings[0],
        extractedKeyframes);
  }

  /**
   * Automatically scans candidate frames extracted from a flight video using the configured YOLO11
   * detector, saves frames with detected defects as Image Evidence in MinIO, and creates pending
   * Findings with bounding boxes.
   */
  @Transactional
  public VideoAutoDetectResponse analyzeVideo(
      UUID actorId, UUID inspectionId, UUID videoEvidenceId, AnalyzeVideoRequest request) {
    Inspection inspection = requireVideoAnalysisEligible(actorId, inspectionId);
    Evidence videoRecord = requireVideoEvidence(inspectionId, videoEvidenceId);
    AiInferencePort port = requireInferencePort();

    List<AnalyzeVideoRequest.CandidateFrame> candidates =
        request != null && request.frames() != null ? request.frames() : List.of();
    if (candidates.isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "VIDEO_FRAMES_REQUIRED",
          "Provide sampled video frames for automated defect scanning");
    }

    long[] nextSeq = new long[] {findings.findMaxFindingCodeNumberByInspectionId(inspectionId) + 1};
    List<VideoAutoDetectResponse.ExtractedDefectFrame> extractedKeyframes =
        new java.util.ArrayList<>();
    int[] totalCreatedFindings = new int[] {0};
    double lastAcceptedTimestamp = -999.0;
    String lastAcceptedPrimaryLabel = "";

    for (AnalyzeVideoRequest.CandidateFrame candidate : candidates) {
      if (candidate == null) {
        continue;
      }
      byte[] frameBytes = decodeBase64FrameOrNull(candidate.imageBase64());
      if (frameBytes == null) {
        continue;
      }

      List<AiInferencePort.Detection> detections;
      try {
        detections = port.analyze(frameBytes, "image/jpeg", false);
      } catch (IOException ex) {
        continue;
      }

      if (detections == null || detections.isEmpty()) {
        continue;
      }

      String primaryLabel = detections.getFirst().predictedLabel();
      double ts = Math.max(0.0, candidate.timestampSeconds());
      if (Math.abs(ts - lastAcceptedTimestamp) < 1.5
          && primaryLabel != null
          && primaryLabel.equalsIgnoreCase(lastAcceptedPrimaryLabel)) {
        continue;
      }
      lastAcceptedTimestamp = ts;
      lastAcceptedPrimaryLabel = primaryLabel != null ? primaryLabel : "";

      extractedKeyframes.add(
          persistVideoKeyframeAndFindings(
              actorId,
              inspection,
              videoRecord,
              ts,
              candidate.fileName(),
              candidate.locationDescription(),
              frameBytes,
              detections,
              nextSeq,
              totalCreatedFindings));
    }

    if (!extractedKeyframes.isEmpty()) {
      syncFindingsConfirmationAfterEdit(inspection);
    }

    return new VideoAutoDetectResponse(
        videoEvidenceId,
        candidates.size(),
        extractedKeyframes.size(),
        totalCreatedFindings[0],
        extractedKeyframes);
  }

  private Inspection requireVideoAnalysisEligible(UUID actorId, UUID inspectionId) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);
    if (inspection.getStatus() == InspectionStatus.SCHEDULED
        || inspection.getStatus() == InspectionStatus.IN_PROGRESS
        || inspection.getStatus() == InspectionStatus.CANCELLED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINDINGS_LOCKED",
          "Video analysis is only available during defect review or report drafting");
    }
    return inspection;
  }

  private Evidence requireVideoEvidence(UUID inspectionId, UUID videoEvidenceId) {
    Evidence videoRecord = requireEvidence(inspectionId, videoEvidenceId);
    if (!videoRecord.isVideo()) {
      throw new BusinessException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "AI_INFERENCE_UNAVAILABLE",
          "The selected evidence item is not a video file");
    }
    return videoRecord;
  }

  private AiInferencePort requireInferencePort() {
    return inference.orElseThrow(
        () ->
            new BusinessException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_INFERENCE_UNAVAILABLE",
                "Automated analysis is not configured"));
  }

  private static byte[] decodeBase64FrameOrNull(String base64) {
    if (base64 == null || base64.isBlank()) {
      return null;
    }
    try {
      String rawB64 = base64.trim();
      int commaIdx = rawB64.indexOf(',');
      if (commaIdx >= 0) {
        rawB64 = rawB64.substring(commaIdx + 1);
      }
      byte[] decoded = java.util.Base64.getDecoder().decode(rawB64);
      return decoded.length > 0 ? decoded : null;
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private VideoAutoDetectResponse.ExtractedDefectFrame persistVideoKeyframeAndFindings(
      UUID actorId,
      Inspection inspection,
      Evidence videoRecord,
      double ts,
      String requestedFileName,
      String requestedLocation,
      byte[] frameBytes,
      List<AiInferencePort.Detection> detections,
      long[] nextSeq,
      int[] totalCreatedFindings) {
    String primaryLabel =
        detections.isEmpty()
            ? "manual_frame"
            : orDefault(detections.getFirst().predictedLabel(), "defect");
    int mins = (int) (ts / 60);
    int secs = (int) Math.round(ts % 60);
    String timeTag = String.format("%02dm%02ds", mins, secs);
    String cleanLabel =
        primaryLabel.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase(java.util.Locale.ROOT);
    String fileName =
        requestedFileName != null && !requestedFileName.isBlank()
            ? requestedFileName
            : String.format(
                "video_keyframe_%s_%s_%d.jpg",
                timeTag,
                cleanLabel,
                Math.abs(java.util.Objects.hash(videoRecord.getId(), ts)) % 1000);
    String locationDesc =
        requestedLocation != null && !requestedLocation.isBlank()
            ? requestedLocation
            : String.format(
                "Extracted from %s @ %02d:%02d (%s)",
                videoRecord.getFileName(), mins, secs, primaryLabel);

    EvidenceResponse savedFrameEvidence =
        evidenceService.storeCapturedMedia(
            actorId,
            inspection.getId(),
            videoRecord.getFieldSessionId(),
            fileName,
            "image/jpeg",
            frameBytes,
            videoRecord.getCaptureTime() != null
                ? videoRecord.getCaptureTime().plusSeconds(Math.round(ts))
                : java.time.Instant.now(),
            videoRecord.getLatitude(),
            videoRecord.getLongitude(),
            locationDesc,
            videoRecord.getId(),
            ts);

    List<Finding> priorForFrame =
        findings.findByEvidenceIdOrderByCreatedAtAsc(savedFrameEvidence.id());
    List<FindingResponse> frameFindingResponses;
    if (!priorForFrame.isEmpty()) {
      frameFindingResponses = priorForFrame.stream().map(this::toFindingResponse).toList();
    } else if (!detections.isEmpty()) {
      List<Finding> frameFindings = new java.util.ArrayList<>();
      PlatformAiSettingsService.AiThresholdConfig thresholdConfig =
          aiSettingsService.map(PlatformAiSettingsService::getThresholdConfig).orElse(null);
      for (AiInferencePort.Detection detection : detections) {
        Finding frameFinding =
            new Finding(
                inspection.getId(),
                savedFrameEvidence.id(),
                "F-" + (nextSeq[0]++),
                detection.predictedLabel(),
                FindingSeverity.MEDIUM,
                detection.predictedLabel(),
                detection.boundingBox(),
                detection.modelName(),
                detection.confidence());
        if (aiSettingsService
            .map(s -> s.shouldAutoConfirm(detection.confidence(), thresholdConfig))
            .orElse(false)) {
          frameFinding.confirmByInspector();
        }
        frameFindings.add(frameFinding);
      }
      List<Finding> storedFrameFindings = findings.saveAllAndFlush(frameFindings);
      totalCreatedFindings[0] += storedFrameFindings.size();
      frameFindingResponses = storedFrameFindings.stream().map(this::toFindingResponse).toList();
    } else {
      frameFindingResponses = List.of();
    }

    return new VideoAutoDetectResponse.ExtractedDefectFrame(
        ts, savedFrameEvidence, frameFindingResponses);
  }

  private static void cleanupTempDirectory(Path workDir) {
    if (workDir == null) {
      return;
    }
    try (var walk = Files.walk(workDir)) {
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

  /**
   * The findings of an inspection.
   *
   * <p>Removed findings are kept out of the working set. A caller that wants to see them asks for
   * them through {@link #listRemovedFindings}.
   */
  @Transactional(readOnly = true)
  public List<FindingResponse> listFindings(UUID actorId, UUID inspectionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    return findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
        .map(this::toFindingResponse)
        .toList();
  }

  /** Explicitly deleted Inspector findings, retained with actor/time/reason attribution. */
  @Transactional(readOnly = true)
  public List<FindingResponse> listRemovedFindings(UUID actorId, UUID inspectionId) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);
    return findings.findRemovedByInspectionId(inspectionId).stream()
        .map(this::toFindingResponse)
        .toList();
  }

  /** Filtered and paginated query of findings across active, removed, and candidate statuses. */
  @Transactional(readOnly = true)
  public FindingPageResponse query(
      UUID actorId,
      UUID inspectionId,
      FindingStatusFilter statusFilter,
      FindingSeverity severity,
      FindingSourceFilter sourceFilter,
      String defectQuery,
      String evidenceQuery,
      int page,
      int pageSize) {
    evidenceService.requireInspectionInScope(actorId, inspectionId);

    int validPage = Math.max(1, page);
    int validPageSize = Math.max(1, Math.min(pageSize, 200));

    List<Finding> allFindings = findings.findAllByInspectionIdIncludingRemoved(inspectionId);
    List<Evidence> allEvidence = evidence.findByInspectionIdOrderByCreatedAtAsc(inspectionId);
    java.util.Map<UUID, Evidence> evidenceMap =
        allEvidence.stream().collect(java.util.stream.Collectors.toMap(Evidence::getId, e -> e));

    long activeCount = allFindings.stream().filter(f -> !f.isDeleted()).count();
    long removedCount = allFindings.stream().filter(Finding::isDeleted).count();
    long pendingCount =
        allFindings.stream()
            .filter(f -> !f.isDeleted() && f.getStatus() == FindingStatus.PENDING)
            .count();
    long confirmedCount =
        allFindings.stream()
            .filter(f -> !f.isDeleted() && f.getStatus() == FindingStatus.CONFIRMED)
            .count();

    FindingStatusFilter effectiveStatus =
        statusFilter != null ? statusFilter : FindingStatusFilter.ACTIVE;
    FindingSourceFilter effectiveSource =
        sourceFilter != null ? sourceFilter : FindingSourceFilter.ALL;
    String cleanDefect = defectQuery != null ? defectQuery.trim().toLowerCase() : "";
    String cleanEvidence = evidenceQuery != null ? evidenceQuery.trim().toLowerCase() : "";

    List<Finding> filtered =
        allFindings.stream()
            .filter(
                f -> {
                  // Status filter
                  switch (effectiveStatus) {
                    case ACTIVE:
                      if (f.isDeleted()) return false;
                      break;
                    case PENDING:
                      if (f.isDeleted() || f.getStatus() != FindingStatus.PENDING) return false;
                      break;
                    case CONFIRMED:
                      if (f.isDeleted() || f.getStatus() != FindingStatus.CONFIRMED) return false;
                      break;
                    case REMOVED:
                      if (!f.isDeleted()) return false;
                      break;
                    case ALL:
                    default:
                      break;
                  }

                  // Severity filter
                  if (severity != null && f.getSeverity() != severity) {
                    return false;
                  }

                  // Source filter
                  if (effectiveSource == FindingSourceFilter.AI && f.getModelName() == null) {
                    return false;
                  }
                  if (effectiveSource == FindingSourceFilter.MANUAL && f.getModelName() != null) {
                    return false;
                  }

                  // Defect query
                  if (!cleanDefect.isEmpty()) {
                    boolean match =
                        (f.getDefectLabel() != null
                                && f.getDefectLabel().toLowerCase().contains(cleanDefect))
                            || (f.getFindingCode() != null
                                && f.getFindingCode().toLowerCase().contains(cleanDefect))
                            || (f.getTechnicalNotes() != null
                                && f.getTechnicalNotes().toLowerCase().contains(cleanDefect))
                            || (f.getModelName() != null
                                && f.getModelName().toLowerCase().contains(cleanDefect));
                    if (!match) return false;
                  }

                  // Evidence query
                  if (!cleanEvidence.isEmpty()) {
                    Evidence ev =
                        f.getEvidenceId() != null ? evidenceMap.get(f.getEvidenceId()) : null;
                    if (ev == null) return false;
                    boolean match =
                        (ev.getFileName() != null
                                && ev.getFileName().toLowerCase().contains(cleanEvidence))
                            || (ev.getLocationDescription() != null
                                && ev.getLocationDescription()
                                    .toLowerCase()
                                    .contains(cleanEvidence));
                    if (!match) return false;
                  }

                  return true;
                })
            .toList();

    long totalCount = filtered.size();
    int totalPages = (int) Math.ceil((double) totalCount / validPageSize);
    int fromIndex = (validPage - 1) * validPageSize;
    List<FindingResponse> items;
    if (fromIndex >= filtered.size()) {
      items = List.of();
    } else {
      int toIndex = Math.min(fromIndex + validPageSize, filtered.size());
      items = filtered.subList(fromIndex, toIndex).stream().map(this::toFindingResponse).toList();
    }

    return new FindingPageResponse(
        items,
        validPage,
        validPageSize,
        totalCount,
        totalPages,
        activeCount,
        removedCount,
        pendingCount,
        confirmedCount);
  }

  /**
   * MF3-06: the Inspector accepts a finding and states what it is.
   *
   * <p>The finding becomes official. Confirming one that was already confirmed is refused, because
   * the content would change underneath a decision that was already made.
   */
  @Transactional
  public FindingResponse confirmFinding(
      UUID actorId, UUID inspectionId, UUID findingId, ReviewFindingRequest request) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);
    Finding finding = requireFinding(inspectionId, findingId);
    if (finding.getStatus() == FindingStatus.CONFIRMED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINDING_ALREADY_CONFIRMED",
          "This finding has already been confirmed");
    }

    finding.revise(
        request.evidenceId() == null ? finding.getEvidenceId() : request.evidenceId(),
        orDefault(request.defectLabel(), finding.getDefectLabel()),
        request.severity() == null ? finding.getSeverity() : request.severity(),
        orDefault(request.technicalNotes(), finding.getTechnicalNotes()));
    if (request.boundingBox() != null) {
      finding.updateBoundingBox(normalizeOptionalBoundingBox(request.boundingBox()));
    }
    finding.confirmByInspector();
    Finding saved = findings.saveAndFlush(finding);
    syncFindingsConfirmationAfterEdit(inspection);
    return toFindingResponse(saved);
  }

  /** MF3-06: a manual finding is available whenever detection is unavailable or missed a defect. */
  @Transactional
  public FindingResponse createManualFinding(
      UUID actorId, UUID inspectionId, ManualFindingRequest request) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);

    Evidence record = requireEvidence(inspectionId, request.evidenceId());
    String boundingBox = normalizeOptionalBoundingBox(request.boundingBox());

    Finding finding =
        new Finding(
            inspectionId,
            record.getId(),
            nextFindingCode(inspectionId),
            request.defectLabel(),
            request.severity(),
            request.technicalNotes(),
            boundingBox,
            null,
            null);
    Finding saved = findings.saveAndFlush(finding);
    syncFindingsConfirmationAfterEdit(inspection);
    return toFindingResponse(saved);
  }

  /** MF3-06: correct an Inspector's finding while the current report is editable. */
  @Transactional
  public FindingResponse updateFinding(
      UUID actorId, UUID inspectionId, UUID findingId, UpdateFindingRequest request) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);
    Finding finding = requireFinding(inspectionId, findingId);
    Evidence record = requireEvidence(inspectionId, request.evidenceId());
    finding.revise(
        record.getId(), request.defectLabel(), request.severity(), request.technicalNotes());
    if (request.boundingBox() != null) {
      finding.updateBoundingBox(normalizeOptionalBoundingBox(request.boundingBox()));
    }
    Finding saved = findings.saveAndFlush(finding);
    syncFindingsConfirmationAfterEdit(inspection);
    return toFindingResponse(saved);
  }

  /**
   * MF3-06: remove a finding from the working set.
   *
   * <p>A pending AI suggestion is rejected and removed from retained finding records. Inspector-
   * authored findings and confirmed findings keep the attributable deletion tombstone.
   */
  @Transactional
  public void deleteFinding(UUID actorId, UUID inspectionId, UUID findingId) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);
    Finding finding = requireFinding(inspectionId, findingId);
    if (finding.isPendingAiSuggestion()) {
      aiSettingsService.ifPresent(PlatformAiSettingsService::recordRejectedPendingSuggestion);
      findings.deleteByIdAndInspectionId(findingId, inspectionId);
      findings.flush();
      syncFindingsConfirmationAfterEdit(inspection);
      return;
    }
    try {
      finding.delete();
    } catch (IllegalStateException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "FINDING_DELETE_INVALID", ex.getMessage());
    }
    findings.saveAndFlush(finding);
    syncFindingsConfirmationAfterEdit(inspection);
  }

  /** Puts a removed finding back into the working set. */
  @Transactional
  public FindingResponse restoreFinding(UUID actorId, UUID inspectionId, UUID findingId) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    rejectLockedFindingEdits(inspectionId);
    Finding finding =
        findings.findRemovedByInspectionId(inspectionId).stream()
            .filter(item -> item.getId().equals(findingId))
            .findFirst()
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "FINDING_NOT_FOUND", "Finding not found"));
    try {
      finding.restore();
    } catch (IllegalArgumentException | IllegalStateException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "FINDING_RESTORE_INVALID", ex.getMessage());
    }
    Finding saved = findings.saveAndFlush(finding);
    syncFindingsConfirmationAfterEdit(inspection);
    return toFindingResponse(saved);
  }

  private void syncFindingsConfirmationAfterEdit(Inspection inspection) {
    if (inspection.getStatus() == InspectionStatus.FIELD_COMPLETED
        && inspection.hasFindingsConfirmation()) {
      inspection.reopenFindings();
      inspections.saveAndFlush(inspection);
    }
  }

  private static final double DUPLICATE_IOU_THRESHOLD = 0.5;
  private static final tools.jackson.databind.ObjectMapper BBOX_MAPPER =
      new tools.jackson.databind.ObjectMapper();

  private static boolean isDuplicateConfirmedDetection(
      AiInferencePort.Detection detection, List<Finding> reviewed) {
    if (detection == null || reviewed == null || reviewed.isEmpty()) {
      return false;
    }
    double[] candidateBox = parseBox(detection.boundingBox());
    if (candidateBox == null) {
      return false;
    }
    String label = detection.predictedLabel();
    for (Finding existing : reviewed) {
      if (existing.getStatus() != FindingStatus.CONFIRMED) {
        continue;
      }
      if (label != null
          && existing.getDefectLabel() != null
          && !label.equalsIgnoreCase(existing.getDefectLabel())) {
        continue;
      }
      double[] existingBox = parseBox(existing.getBoundingBox());
      if (existingBox != null
          && intersectionOverUnion(candidateBox, existingBox) >= DUPLICATE_IOU_THRESHOLD) {
        return true;
      }
    }
    return false;
  }

  private static String normalizeOptionalBoundingBox(String rawJson) {
    if (rawJson == null || rawJson.isBlank()) {
      return null;
    }
    double[] coords = parseBox(rawJson);
    if (coords == null
        || !Double.isFinite(coords[0])
        || !Double.isFinite(coords[1])
        || !Double.isFinite(coords[2])
        || !Double.isFinite(coords[3])
        || coords[0] < 0.0
        || coords[1] < 0.0
        || coords[2] > 1.0
        || coords[3] > 1.0
        || coords[2] <= coords[0]
        || coords[3] <= coords[1]) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_BOUNDING_BOX",
          "Bounding box must be normalized coordinates within [0, 1] with xMax > xMin and yMax > yMin");
    }
    return String.format(
        java.util.Locale.ROOT,
        "{\"xMin\": %.4f, \"yMin\": %.4f, \"xMax\": %.4f, \"yMax\": %.4f}",
        coords[0],
        coords[1],
        coords[2],
        coords[3]);
  }

  /** Parses normalized {xMin,yMin,xMax,yMax} or {x,y,w,h} bounding box JSON; null if invalid. */
  private static double[] parseBox(String rawJson) {
    if (rawJson == null || rawJson.isBlank()) {
      return null;
    }
    try {
      tools.jackson.databind.JsonNode node = BBOX_MAPPER.readTree(rawJson);
      if (node == null || !node.isObject()) {
        return null;
      }
      if (node.hasNonNull("xMin")
          && node.hasNonNull("yMin")
          && node.hasNonNull("xMax")
          && node.hasNonNull("yMax")) {
        double xMin = node.get("xMin").asDouble();
        double yMin = node.get("yMin").asDouble();
        double xMax = node.get("xMax").asDouble();
        double yMax = node.get("yMax").asDouble();
        if (xMax > xMin && yMax > yMin) {
          return new double[] {xMin, yMin, xMax, yMax};
        }
      } else if (node.hasNonNull("x")
          && node.hasNonNull("y")
          && node.hasNonNull("w")
          && node.hasNonNull("h")) {
        double x = node.get("x").asDouble();
        double y = node.get("y").asDouble();
        double w = node.get("w").asDouble();
        double h = node.get("h").asDouble();
        if (w > 0 && h > 0) {
          return new double[] {x, y, x + w, y + h};
        }
      }
      return null;
    } catch (Exception ex) {
      return null;
    }
  }

  private static double intersectionOverUnion(double[] a, double[] b) {
    double interXMin = Math.max(a[0], b[0]);
    double interYMin = Math.max(a[1], b[1]);
    double interXMax = Math.min(a[2], b[2]);
    double interYMax = Math.min(a[3], b[3]);
    double interW = Math.max(0.0, interXMax - interXMin);
    double interH = Math.max(0.0, interYMax - interYMin);
    double interArea = interW * interH;
    if (interArea <= 0.0) {
      return 0.0;
    }
    double areaA = Math.max(0.0, a[2] - a[0]) * Math.max(0.0, a[3] - a[1]);
    double areaB = Math.max(0.0, b[2] - b[0]) * Math.max(0.0, b[3] - b[1]);
    double union = areaA + areaB - interArea;
    return union <= 0.0 ? 0.0 : interArea / union;
  }

  private void rejectFinalizedReportFindingEdits(UUID inspectionId) {
    var current =
        reportVersions.findFirstByInspectionIdOrderByVersionNoDesc(inspectionId).orElse(null);
    if (current != null
        && (current.getStatus() == ReportStatus.SUBMITTED
            || current.getStatus() == ReportStatus.PUBLISHED
            || current.getStatus() == ReportStatus.SUPERSEDED)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINDINGS_LOCKED",
          "Findings are locked while a report is submitted or after publication");
    }
  }

  private void rejectLockedFindingEdits(UUID inspectionId) {
    Inspection inspection = inspections.findById(inspectionId).orElse(null);
    if (inspection != null
        && inspection.getStatus()
            == com.smartdroneinspection.inspections.domain.enums.InspectionStatus.COMPLETED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FINDINGS_LOCKED",
          "Findings are immutable after report publication");
    }
    rejectFinalizedReportFindingEdits(inspectionId);
  }

  private Finding requireFinding(UUID inspectionId, UUID findingId) {
    return findings
        .findByIdAndInspectionId(findingId, inspectionId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "FINDING_NOT_FOUND", "Finding not found"));
  }

  private Evidence requireEvidence(UUID inspectionId, UUID evidenceId) {
    Evidence record =
        evidence
            .findByIdAndInspectionId(evidenceId, inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "Evidence not found"));
    if (record.isRetired()) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "EVIDENCE_RETIRED", "Retired evidence cannot be used for a finding");
    }
    return record;
  }

  private static String orDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String nextFindingCode(UUID inspectionId) {
    return "F-" + (findings.findMaxFindingCodeNumberByInspectionId(inspectionId) + 1);
  }

  private FindingResponse toFindingResponse(Finding finding) {
    return new FindingResponse(
        finding.getId(),
        finding.getInspectionId(),
        finding.getEvidenceId(),
        finding.getFindingCode(),
        finding.getDefectLabel(),
        finding.getSeverity(),
        finding.getBoundingBox(),
        finding.getTechnicalNotes(),
        finding.getStatus(),
        finding.getModelName(),
        finding.getConfidence(),
        finding.isDeleted(),
        finding.getCreatedAt());
  }
}

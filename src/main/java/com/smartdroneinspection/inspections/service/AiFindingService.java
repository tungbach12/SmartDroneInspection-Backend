package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspectionrequests.domain.InspectionAssignment;
import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionAssignmentStatus;
import com.smartdroneinspection.inspectionrequests.repository.InspectionAssignmentRepository;
import com.smartdroneinspection.inspections.api.dto.request.CreateManualFindingRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReviewFindingCandidateRequest;
import com.smartdroneinspection.inspections.api.dto.request.ReviewFindingCandidateRequest.Decision;
import com.smartdroneinspection.inspections.api.dto.response.AiFindingCandidateResponse;
import com.smartdroneinspection.inspections.api.dto.response.VerifiedFindingResponse;
import com.smartdroneinspection.inspections.domain.AiFindingCandidate;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.VerifiedFinding;
import com.smartdroneinspection.inspections.domain.enums.AiFindingCandidateStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.UploadStatus;
import com.smartdroneinspection.inspections.domain.enums.VerifiedFindingSource;
import com.smartdroneinspection.inspections.repository.AiFindingCandidateRepository;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.VerifiedFindingRepository;
import com.smartdroneinspection.inspections.spi.AiInferencePort;
import com.smartdroneinspection.inspections.spi.EvidenceObjectStore;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiFindingService {

  private static final int MAX_ANALYSIS_BYTES = 10 * 1024 * 1024;

  private final InspectionRepository inspections;
  private final InspectionAssignmentRepository assignments;
  private final EvidenceRepository evidence;
  private final AiFindingCandidateRepository candidates;
  private final VerifiedFindingRepository findings;
  private final UserAccess users;
  private final Optional<EvidenceObjectStore> objectStore;
  private final Optional<AiInferencePort> inference;
  private final ObjectMapper objectMapper;

  public AiFindingService(
      InspectionRepository inspections,
      InspectionAssignmentRepository assignments,
      EvidenceRepository evidence,
      AiFindingCandidateRepository candidates,
      VerifiedFindingRepository findings,
      UserAccess users,
      Optional<EvidenceObjectStore> objectStore,
      Optional<AiInferencePort> inference,
      ObjectMapper objectMapper) {
    this.inspections = inspections;
    this.assignments = assignments;
    this.evidence = evidence;
    this.candidates = candidates;
    this.findings = findings;
    this.users = users;
    this.objectStore = objectStore;
    this.inference = inference;
    this.objectMapper = objectMapper;
  }

  public List<AiFindingCandidateResponse> analyze(
      UUID actorId, UUID inspectionId, UUID evidenceId) {
    requireAssignedInspection(actorId, inspectionId, false);
    Evidence record = requireEvidence(inspectionId, evidenceId);
    if (!record.getContentType().equals("image/png")
        && !record.getContentType().equals("image/jpeg")) {
      throw invalidFinding("Only PNG and JPEG evidence can be analyzed.");
    }
    EvidenceObjectStore store =
        objectStore.orElseThrow(() -> unavailableAi("The AI analysis service is not configured."));
    AiInferencePort ai =
        inference.orElseThrow(() -> unavailableAi("The AI analysis service is not configured."));

    byte[] image;
    try (InputStream input = store.open(record.getObjectKey())) {
      image = readBounded(input);
    } catch (IOException exception) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Evidence content is temporarily unavailable.");
    }

    List<AiInferencePort.Detection> detections;
    try {
      detections = ai.analyze(image, record.getContentType());
      if (detections == null) {
        throw new IOException("AI service returned no result payload");
      }
      detections.forEach(this::validateDetection);
    } catch (IOException | IllegalArgumentException exception) {
      throw unavailableAi("The AI analysis service could not analyze this evidence.");
    }

    List<AiFindingCandidate> persisted =
        candidates.saveAllAndFlush(
            detections.stream()
                .map(
                    detection ->
                        new AiFindingCandidate(
                            evidenceId,
                            detection.modelName().trim(),
                            detection.modelVersion().trim(),
                            detection.predictedLabel().trim(),
                            detection.confidence(),
                            detection.boundingBox()))
                .toList());
    return persisted.stream().map(this::toCandidateResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<AiFindingCandidateResponse> listCandidates(UUID actorId, UUID inspectionId) {
    requireAssignedInspection(actorId, inspectionId, false);
    List<UUID> evidenceIds =
        evidence
            .findByInspectionIdAndUploadStatusOrderByCreatedAtDesc(
                inspectionId, UploadStatus.AVAILABLE)
            .stream()
            .map(Evidence::getId)
            .toList();
    if (evidenceIds.isEmpty()) {
      return List.of();
    }
    return candidates.findByEvidenceIdInOrderByCreatedAtDesc(evidenceIds).stream()
        .map(this::toCandidateResponse)
        .toList();
  }

  @Transactional
  public AiFindingCandidateResponse reviewCandidate(
      UUID actorId, UUID inspectionId, UUID candidateId, ReviewFindingCandidateRequest request) {
    Inspection inspection = requireAssignedInspection(actorId, inspectionId, true);
    validateReviewRequest(request);
    AiFindingCandidate candidate =
        candidates
            .findForUpdateById(candidateId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "AI_CANDIDATE_NOT_FOUND",
                        "AI finding candidate was not found."));
    requireEvidence(inspectionId, candidate.getEvidenceId());
    if (candidate.getStatus() != AiFindingCandidateStatus.PENDING) {
      throw stateConflict("This candidate has already been reviewed.");
    }

    if (request.decision() == Decision.REJECT) {
      candidate.review(
          AiFindingCandidateStatus.REJECTED, actorId, request.rejectionReason().trim());
      return toCandidateResponse(candidates.saveAndFlush(candidate));
    }

    VerifiedFindingSource source =
        request.decision() == Decision.CONFIRM
            ? VerifiedFindingSource.AI_CONFIRMED
            : VerifiedFindingSource.AI_MODIFIED;
    String defectLabel =
        request.decision() == Decision.CONFIRM
            ? candidate.getPredictedLabel()
            : request.defectLabel().trim();
    String boundingBox = request.decision() == Decision.CONFIRM ? candidate.getBoundingBox() : null;
    candidate.review(
        request.decision() == Decision.CONFIRM
            ? AiFindingCandidateStatus.CONFIRMED
            : AiFindingCandidateStatus.MODIFIED,
        actorId,
        null);
    findings.saveAndFlush(
        new VerifiedFinding(
            inspection.getId(),
            candidate.getEvidenceId(),
            candidate.getId(),
            actorId,
            source,
            nextFindingCode(),
            defectLabel,
            request.severity(),
            request.locationDescription().trim(),
            request.technicalNotes().trim(),
            trimToNull(request.recommendedAction()),
            boundingBox));
    return toCandidateResponse(candidates.saveAndFlush(candidate));
  }

  @Transactional
  public VerifiedFindingResponse createManualFinding(
      UUID actorId, UUID inspectionId, CreateManualFindingRequest request) {
    Inspection inspection = requireAssignedInspection(actorId, inspectionId, true);
    Evidence record = requireEvidence(inspectionId, request.evidenceId());
    VerifiedFinding finding =
        findings.saveAndFlush(
            new VerifiedFinding(
                inspection.getId(),
                record.getId(),
                null,
                actorId,
                VerifiedFindingSource.MANUAL,
                nextFindingCode(),
                request.defectLabel().trim(),
                request.severity(),
                request.locationDescription().trim(),
                request.technicalNotes().trim(),
                trimToNull(request.recommendedAction()),
                null));
    return toFindingResponse(finding);
  }

  private Inspection requireAssignedInspection(UUID actorId, UUID inspectionId, boolean lock) {
    UserAccess.ActiveUser actor =
        users
            .findActiveUser(actorId)
            .filter(user -> user.hasRole(Roles.INSPECTOR))
            .orElseThrow(this::scopeDenied);
    Inspection inspection =
        (lock
                ? inspections.findForUpdateByIdAndAuthorUserId(inspectionId, actorId)
                : inspections.findByIdAndAuthorUserId(inspectionId, actorId))
            .orElseThrow(this::scopeDenied);
    InspectionAssignment assignment =
        assignments
            .findByIdAndInspectorUserId(inspection.getAcceptedAssignmentId(), actor.id())
            .filter(value -> value.getStatus() == InspectionAssignmentStatus.ACCEPTED)
            .orElseThrow(this::scopeDenied);
    if (!inspection.getAuthorUserId().equals(actor.id())
        || !assignment.getInspectorUserId().equals(actor.id())) {
      throw scopeDenied();
    }
    if (inspection.getStatus() != InspectionStatus.IN_PROGRESS) {
      throw stateConflict("Findings can only be recorded while the inspection is in progress.");
    }
    return inspection;
  }

  private Evidence requireEvidence(UUID inspectionId, UUID evidenceId) {
    return evidence
        .findByIdAndInspectionIdAndUploadStatus(evidenceId, inspectionId, UploadStatus.AVAILABLE)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "EVIDENCE_NOT_FOUND",
                    "Available evidence was not found for this inspection."));
  }

  private byte[] readBounded(InputStream input) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int total = 0;
    int count;
    while ((count = input.read(buffer)) != -1) {
      total += count;
      if (total > MAX_ANALYSIS_BYTES) {
        throw new IOException("Evidence is larger than the analysis limit");
      }
      output.write(buffer, 0, count);
    }
    if (total == 0) {
      throw new IOException("Evidence is empty");
    }
    return output.toByteArray();
  }

  private void validateDetection(AiInferencePort.Detection detection) {
    if (detection == null
        || blank(detection.modelName())
        || blank(detection.modelVersion())
        || blank(detection.predictedLabel())
        || detection.modelName().length() > 160
        || detection.modelVersion().length() > 80
        || detection.predictedLabel().length() > 160
        || detection.confidence() == null
        || detection.confidence().compareTo(BigDecimal.ZERO) < 0
        || detection.confidence().compareTo(BigDecimal.ONE) > 0
        || blank(detection.boundingBox())) {
      throw new IllegalArgumentException("AI detection payload is invalid");
    }
    try {
      if (objectMapper.readTree(detection.boundingBox()) == null
          || !objectMapper.readTree(detection.boundingBox()).isObject()) {
        throw new IllegalArgumentException("AI bounding box must be a JSON object");
      }
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("AI bounding box is not valid JSON", exception);
    }
  }

  private void validateReviewRequest(ReviewFindingCandidateRequest request) {
    if (request == null || request.decision() == null) {
      throw invalidFinding("A candidate review decision is required.");
    }
    if (request.decision() == Decision.REJECT) {
      if (blank(request.rejectionReason())) {
        throw invalidFinding("A rejection reason is required.");
      }
      return;
    }
    if (request.severity() == null
        || blank(request.locationDescription())
        || blank(request.technicalNotes())) {
      throw invalidFinding("Severity, location, and technical notes are required.");
    }
    if (request.decision() == Decision.MODIFY && blank(request.defectLabel())) {
      throw invalidFinding("A verified defect label is required for a modified candidate.");
    }
  }

  private AiFindingCandidateResponse toCandidateResponse(AiFindingCandidate candidate) {
    return new AiFindingCandidateResponse(
        candidate.getId(),
        candidate.getEvidenceId(),
        candidate.getModelName(),
        candidate.getModelVersion(),
        candidate.getPredictedLabel(),
        candidate.getConfidence(),
        candidate.getBoundingBox(),
        candidate.getStatus(),
        candidate.getCreatedAt());
  }

  private VerifiedFindingResponse toFindingResponse(VerifiedFinding finding) {
    return new VerifiedFindingResponse(
        finding.getId(),
        finding.getEvidenceId(),
        finding.getAiCandidateId(),
        finding.getFindingCode(),
        finding.getSource(),
        finding.getDefectLabel(),
        finding.getSeverity(),
        finding.getLocationDescription(),
        finding.getTechnicalNotes(),
        finding.getRecommendedAction());
  }

  private String nextFindingCode() {
    return "F-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private BusinessException scopeDenied() {
    return new BusinessException(
        HttpStatus.FORBIDDEN,
        "INSPECTION_SCOPE_DENIED",
        "Inspection is not available to this user.");
  }

  private BusinessException stateConflict(String message) {
    return new BusinessException(HttpStatus.CONFLICT, "INSPECTION_STATE_CONFLICT", message);
  }

  private BusinessException invalidFinding(String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, "FINDING_INVALID", message);
  }

  private BusinessException unavailableAi(String message) {
    return new BusinessException(
        HttpStatus.SERVICE_UNAVAILABLE, "AI_INFERENCE_UNAVAILABLE", message);
  }
}

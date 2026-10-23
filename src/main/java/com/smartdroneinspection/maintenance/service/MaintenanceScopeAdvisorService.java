package com.smartdroneinspection.maintenance.service;

import com.smartdroneinspection.inspections.spi.RepairCandidate;
import com.smartdroneinspection.inspections.spi.RepairCandidateAccess;
import com.smartdroneinspection.maintenance.api.dto.request.SuggestScopeRequest;
import com.smartdroneinspection.maintenance.api.dto.response.SuggestScopeResponse;
import com.smartdroneinspection.maintenance.api.dto.response.SuggestScopeResponse.SuggestedTaskDto;
import com.smartdroneinspection.maintenance.domain.RepairKnowledgeChunk;
import com.smartdroneinspection.maintenance.repository.RepairKnowledgeChunkRepository;
import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort;
import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort.KnowledgeChunkSummary;
import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort.ScopeAdvisorContext;
import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort.SuggestedScope;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that orchestrates RAG knowledge retrieval and LLM prompt generation to advise on
 * corrective scope and acceptance criteria for maintenance work orders.
 */
@Service
public class MaintenanceScopeAdvisorService {

  private static final Logger log = LoggerFactory.getLogger(MaintenanceScopeAdvisorService.class);

  private final Optional<MaintenanceScopeAdvisorPort> advisorPort;
  private final RepairKnowledgeChunkRepository knowledgeRepository;
  private final RepairCandidateAccess repairCandidateAccess;
  private final DeterministicScopeFallback deterministicFallback;
  private final UserAccess userAccess;

  public MaintenanceScopeAdvisorService(
      Optional<MaintenanceScopeAdvisorPort> advisorPort,
      RepairKnowledgeChunkRepository knowledgeRepository,
      RepairCandidateAccess repairCandidateAccess,
      DeterministicScopeFallback deterministicFallback,
      UserAccess userAccess) {
    this.advisorPort = advisorPort;
    this.knowledgeRepository = knowledgeRepository;
    this.repairCandidateAccess = repairCandidateAccess;
    this.deterministicFallback = deterministicFallback;
    this.userAccess = userAccess;
  }

  @Transactional(readOnly = true)
  public SuggestScopeResponse suggestScope(UUID actorId, SuggestScopeRequest request) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.UNAUTHORIZED, "USER_INACTIVE", "Active user account required"));

    // 1. Resolve finding details either from verified report candidate or request payload
    String findingCode = request.findingCode();
    String defectLabel = request.defectLabel();
    String severity = request.severity();
    String description = request.description();
    String recommendedAction = request.recommendedAction();
    String component = request.component();
    String locationDescription = request.locationDescription();
    String measurement = request.measurement();

    if (request.sourceReportVersionId() != null && request.sourceFindingId() != null) {
      Optional<RepairCandidate> candidateOpt =
          repairCandidateAccess.findRepairCandidate(
              actor.organizationId(),
              request.sourceReportVersionId(),
              request.sourceFindingId());

      if (candidateOpt.isPresent()) {
        RepairCandidate candidate = candidateOpt.get();
        findingCode = candidate.findingCode();
        defectLabel = candidate.defectLabel();
        severity = candidate.severity();
        description = candidate.description();
        recommendedAction = candidate.recommendedAction();
        component = candidate.component();
        locationDescription = candidate.locationDescription();
        measurement = candidate.measurement();
      }
    }

    if (defectLabel == null || defectLabel.isBlank()) {
      defectLabel = "Khuyết tật kết cấu";
    }

    // 2. Retrieve relevant technical standard knowledge chunks from pgvector/database
    String category = mapCategory(defectLabel, description);
    String keyword = extractKeyword(defectLabel, description);
    List<RepairKnowledgeChunk> relevantChunks =
        knowledgeRepository.findRelevantChunks(category, keyword, 3);

    List<KnowledgeChunkSummary> chunkSummaries =
        relevantChunks.stream()
            .map(
                c ->
                    new KnowledgeChunkSummary(
                        c.getStandardCode(), c.getTitle(), c.getCategory(), c.getContent()))
            .toList();

    ScopeAdvisorContext context =
        new ScopeAdvisorContext(
            findingCode,
            defectLabel,
            severity,
            component,
            locationDescription,
            description,
            recommendedAction,
            measurement,
            chunkSummaries);

    // 3. Invoke AI Inference if configured; gracefully fall back to deterministic rules on failure
    if (advisorPort.isPresent()) {
      try {
        MaintenanceScopeAdvisorPort port = advisorPort.get();
        SuggestedScope aiScope = port.suggestScope(context);
        if (aiScope != null
            && aiScope.correctiveScope() != null
            && !aiScope.correctiveScope().isBlank()) {
          return toResponse(aiScope, port.modelName(), false);
        }
      } catch (IOException | RuntimeException e) {
        log.warn("AI scope advisor failed; activating deterministic engineering fallback: {}", e.getMessage());
      }
    }

    // 4. Deterministic fallback based on engineering standards
    SuggestedScope fallbackScope =
        deterministicFallback.fallback(defectLabel, description, component, measurement);
    return toResponse(fallbackScope, "engineering-standards-fallback", true);
  }

  private String mapCategory(String defectLabel, String description) {
    String text =
        ((defectLabel != null ? defectLabel : "") + " " + (description != null ? description : ""))
            .toLowerCase(Locale.ROOT);
    if (text.contains("crack") || text.contains("nứt") || text.contains("nut")) {
      return "CONCRETE_CRACK";
    }
    if (text.contains("spall")
        || text.contains("bong tróc")
        || text.contains("vỡ")
        || text.contains("rỗ")) {
      return "SPALLING";
    }
    if (text.contains("corrosion")
        || text.contains("gỉ")
        || text.contains("rỉ")
        || text.contains("ăn mòn")) {
      return "STEEL_CORROSION";
    }
    if (text.contains("leak")
        || text.contains("thấm")
        || text.contains("rò rỉ")
        || text.contains("ẩm")) {
      return "WATER_LEAKAGE";
    }
    return "STRUCTURAL_DEFORMATION";
  }

  private String extractKeyword(String defectLabel, String description) {
    if (defectLabel != null && !defectLabel.isBlank()) {
      return defectLabel.split("\\s+")[0].toLowerCase(Locale.ROOT);
    }
    return "concrete";
  }

  private SuggestScopeResponse toResponse(
      SuggestedScope scope, String modelName, boolean fallbackApplied) {
    List<SuggestedTaskDto> taskDtos = new ArrayList<>();
    if (scope.suggestedTasks() != null) {
      for (var t : scope.suggestedTasks()) {
        taskDtos.add(new SuggestedTaskDto(t.taskNumber(), t.name(), t.method(), t.acceptanceCriteria()));
      }
    }

    return new SuggestScopeResponse(
        scope.correctiveScope(),
        scope.acceptanceCriteria(),
        scope.referenceStandards() != null ? scope.referenceStandards() : List.of(),
        taskDtos,
        modelName,
        fallbackApplied);
  }
}

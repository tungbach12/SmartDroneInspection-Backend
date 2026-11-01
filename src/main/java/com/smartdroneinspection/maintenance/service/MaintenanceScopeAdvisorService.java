package com.smartdroneinspection.maintenance.service;

import com.smartdroneinspection.inspections.spi.RepairCandidate;
import com.smartdroneinspection.inspections.spi.RepairCandidateAccess;
import com.smartdroneinspection.maintenance.api.dto.request.SuggestScopeRequest;
import com.smartdroneinspection.maintenance.api.dto.response.SuggestEstimateResponse;
import com.smartdroneinspection.maintenance.api.dto.response.SuggestScopeResponse;
import com.smartdroneinspection.maintenance.api.dto.response.SuggestScopeResponse.SuggestedTaskDto;
import com.smartdroneinspection.maintenance.domain.MaintenanceTask;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkOrder;
import com.smartdroneinspection.maintenance.domain.RepairKnowledgeChunk;
import com.smartdroneinspection.maintenance.domain.enums.CostLineKind;
import com.smartdroneinspection.maintenance.repository.MaintenanceTaskRepository;
import com.smartdroneinspection.maintenance.repository.RepairKnowledgeChunkRepository;
import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort;
import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort.KnowledgeChunkSummary;
import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort.ScopeAdvisorContext;
import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort.SuggestedScope;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final WorkOrderService workOrderService;
  private final MaintenanceTaskRepository tasks;

  @Autowired
  public MaintenanceScopeAdvisorService(
      Optional<MaintenanceScopeAdvisorPort> advisorPort,
      RepairKnowledgeChunkRepository knowledgeRepository,
      RepairCandidateAccess repairCandidateAccess,
      DeterministicScopeFallback deterministicFallback,
      UserAccess userAccess,
      WorkOrderService workOrderService,
      MaintenanceTaskRepository tasks) {
    this.advisorPort = advisorPort;
    this.knowledgeRepository = knowledgeRepository;
    this.repairCandidateAccess = repairCandidateAccess;
    this.deterministicFallback = deterministicFallback;
    this.userAccess = userAccess;
    this.workOrderService = workOrderService;
    this.tasks = tasks;
  }

  public MaintenanceScopeAdvisorService(
      Optional<MaintenanceScopeAdvisorPort> advisorPort,
      RepairKnowledgeChunkRepository knowledgeRepository,
      RepairCandidateAccess repairCandidateAccess,
      DeterministicScopeFallback deterministicFallback,
      UserAccess userAccess) {
    this(
        advisorPort,
        knowledgeRepository,
        repairCandidateAccess,
        deterministicFallback,
        userAccess,
        null,
        null);
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
              actor.organizationId(), request.sourceReportVersionId(), request.sourceFindingId());

      if (candidateOpt.isPresent()) {
        RepairCandidate candidate = candidateOpt.get();
        findingCode = candidate.findingCode();
        defectLabel = candidate.defectLabel();
        severity = candidate.severity();
        description = candidate.technicalNotes();
        recommendedAction = candidate.recommendedAction();
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
        log.warn(
            "AI scope advisor failed; activating deterministic engineering fallback: {}",
            e.getMessage());
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

  @Transactional(readOnly = true)
  public SuggestEstimateResponse suggestEstimate(UUID actorId, UUID workOrderId) {
    if (workOrderService == null || tasks == null) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNCONFIGURED", "WorkOrderService is required");
    }
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    List<MaintenanceTask> taskList = tasks.findByWorkOrderIdOrderByTaskNumberAsc(order.getId());

    String category = mapCategory(order.getCorrectiveScope(), order.getAcceptanceCriteria());
    List<SuggestEstimateResponse.SuggestedLineDto> lines = new ArrayList<>();

    UUID firstTaskId = taskList.isEmpty() ? null : taskList.get(0).getId();
    UUID secondTaskId = taskList.size() > 1 ? taskList.get(1).getId() : firstTaskId;
    UUID thirdTaskId = taskList.size() > 2 ? taskList.get(2).getId() : secondTaskId;

    switch (category) {
      case "CONCRETE_CRACK" -> {
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                firstTaskId,
                CostLineKind.LABOR,
                "Nhân công kỹ thuật bơm keo epoxy (Thợ bậc 4/7 theo Định mức BXD)",
                BigDecimal.valueOf(8),
                "giờ",
                BigDecimal.valueOf(150000),
                BigDecimal.valueOf(1200000)));
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                firstTaskId,
                CostLineKind.MATERIAL,
                "Keo trám khe nứt epoxy Sikadur-731 (Bộ 2 thành phần)",
                BigDecimal.valueOf(2),
                "bộ",
                BigDecimal.valueOf(480000),
                BigDecimal.valueOf(960000)));
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                secondTaskId,
                CostLineKind.MATERIAL,
                "Keo epoxy độ nhớt thấp Sikadur-752 bơm chèn vết nứt",
                BigDecimal.valueOf(3),
                "lít",
                BigDecimal.valueOf(650000),
                BigDecimal.valueOf(1950000)));
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                thirdTaskId,
                CostLineKind.EQUIPMENT,
                "Máy bơm keo áp lực thấp & Bát bơm chuyên dụng",
                BigDecimal.valueOf(1),
                "ca",
                BigDecimal.valueOf(500000),
                BigDecimal.valueOf(500000)));
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                null,
                CostLineKind.CONTINGENCY,
                "Dự phòng hao hụt vật tư kỹ thuật công trường (5%)",
                BigDecimal.valueOf(1),
                "gói",
                BigDecimal.valueOf(230000),
                BigDecimal.valueOf(230000)));
      }
      case "SPALLING" -> {
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                firstTaskId,
                CostLineKind.LABOR,
                "Nhân công đục tẩy bê tông rỗ mục và tẩy gỉ cốt thép",
                BigDecimal.valueOf(12),
                "giờ",
                BigDecimal.valueOf(120000),
                BigDecimal.valueOf(1440000)));
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                firstTaskId,
                CostLineKind.MATERIAL,
                "Chất quét bảo vệ cốt thép gốc kẽm Sika Monotop-910S",
                BigDecimal.valueOf(1),
                "bộ",
                BigDecimal.valueOf(550000),
                BigDecimal.valueOf(550000)));
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                secondTaskId,
                CostLineKind.MATERIAL,
                "Vữa sửa chữa polyme cường độ cao Sika Monotop R",
                BigDecimal.valueOf(4),
                "bao",
                BigDecimal.valueOf(380000),
                BigDecimal.valueOf(1520000)));
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                thirdTaskId,
                CostLineKind.EQUIPMENT,
                "Máy cắt bê tông & Máy phun nước áp lực cao",
                BigDecimal.valueOf(2),
                "ca",
                BigDecimal.valueOf(400000),
                BigDecimal.valueOf(800000)));
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                null,
                CostLineKind.CONTINGENCY,
                "Dự phòng hao hụt và xử lý phát sinh (5%)",
                BigDecimal.valueOf(1),
                "gói",
                BigDecimal.valueOf(215000),
                BigDecimal.valueOf(215000)));
      }
      case "STEEL_CORROSION" -> {
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                firstTaskId,
                CostLineKind.LABOR,
                "Nhân công phun hạt mài Sa 2.5 và sơn bảo vệ kết cấu",
                BigDecimal.valueOf(16),
                "giờ",
                BigDecimal.valueOf(130000),
                BigDecimal.valueOf(2080000)));
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                firstTaskId,
                CostLineKind.MATERIAL,
                "Hạt mài xỉ đồng làm sạch bề mặt đạt chuẩn ISO 8501-1",
                BigDecimal.valueOf(80),
                "kg",
                BigDecimal.valueOf(18000),
                BigDecimal.valueOf(1440000)));
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                secondTaskId,
                CostLineKind.MATERIAL,
                "Hệ sơn epoxy-polyurethane 3 lớp chuẩn ISO 12944 (DFT >= 225um)",
                BigDecimal.valueOf(1),
                "bộ",
                BigDecimal.valueOf(2800000),
                BigDecimal.valueOf(2800000)));
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                thirdTaskId,
                CostLineKind.EQUIPMENT,
                "Máy nén khí & Cối phun hạt mài công nghiệp",
                BigDecimal.valueOf(2),
                "ca",
                BigDecimal.valueOf(600000),
                BigDecimal.valueOf(1200000)));
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                null,
                CostLineKind.CONTINGENCY,
                "Dự phòng vật tư che chắn môi trường",
                BigDecimal.valueOf(1),
                "gói",
                BigDecimal.valueOf(375000),
                BigDecimal.valueOf(375000)));
      }
      default -> {
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                firstTaskId,
                CostLineKind.LABOR,
                "Nhân công thi công sửa chữa chuyên dụng",
                BigDecimal.valueOf(10),
                "giờ",
                BigDecimal.valueOf(120000),
                BigDecimal.valueOf(1200000)));
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                secondTaskId,
                CostLineKind.MATERIAL,
                "Vật liệu kỹ thuật phục hồi kết cấu công trình",
                BigDecimal.valueOf(1),
                "gói",
                BigDecimal.valueOf(2000000),
                BigDecimal.valueOf(2000000)));
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                thirdTaskId,
                CostLineKind.EQUIPMENT,
                "Trang thiết bị và dụng cụ thi công an toàn",
                BigDecimal.valueOf(1),
                "ca",
                BigDecimal.valueOf(500000),
                BigDecimal.valueOf(500000)));
        lines.add(
            new SuggestEstimateResponse.SuggestedLineDto(
                null,
                CostLineKind.CONTINGENCY,
                "Dự phòng phát sinh công trường (5%)",
                BigDecimal.valueOf(1),
                "gói",
                BigDecimal.valueOf(185000),
                BigDecimal.valueOf(185000)));
      }
    }

    BigDecimal total =
        lines.stream()
            .map(SuggestEstimateResponse.SuggestedLineDto::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    String assumptions =
        String.format(
            "Định mức dự toán chi phí kỹ thuật lập theo tiêu chuẩn %s và định mức xây dựng hiện hành",
            category);
    return new SuggestEstimateResponse("VND", assumptions, total, lines);
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
        taskDtos.add(
            new SuggestedTaskDto(t.taskNumber(), t.name(), t.method(), t.acceptanceCriteria()));
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

package com.smartdroneinspection.maintenance.service;

import com.smartdroneinspection.inspections.spi.ReinspectionRequestedEvent;
import com.smartdroneinspection.maintenance.api.dto.request.CreateChangeOrderRequest;
import com.smartdroneinspection.maintenance.api.dto.request.CreateReportVersionRequest;
import com.smartdroneinspection.maintenance.api.dto.request.ReconcileCostsRequest;
import com.smartdroneinspection.maintenance.api.dto.request.RecordAcceptanceRequest;
import com.smartdroneinspection.maintenance.api.dto.request.RecordActualCostLineRequest;
import com.smartdroneinspection.maintenance.api.dto.request.RecordWorkLogRequest;
import com.smartdroneinspection.maintenance.api.dto.response.AcceptanceDecisionResponse;
import com.smartdroneinspection.maintenance.api.dto.response.ChangeOrderResponse;
import com.smartdroneinspection.maintenance.api.dto.response.CostReconciliationResponse;
import com.smartdroneinspection.maintenance.api.dto.response.ReportVersionResponse;
import com.smartdroneinspection.maintenance.api.dto.response.WorkLogResponse;
import com.smartdroneinspection.maintenance.api.dto.response.WorkOrderResponse;
import com.smartdroneinspection.maintenance.domain.MaintenanceAcceptanceDecision;
import com.smartdroneinspection.maintenance.domain.MaintenanceChangeOrder;
import com.smartdroneinspection.maintenance.domain.MaintenanceCostLine;
import com.smartdroneinspection.maintenance.domain.MaintenanceEstimateVersion;
import com.smartdroneinspection.maintenance.domain.MaintenanceReportVersion;
import com.smartdroneinspection.maintenance.domain.MaintenanceTask;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkLog;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkOrder;
import com.smartdroneinspection.maintenance.domain.enums.ChangeOrderStatus;
import com.smartdroneinspection.maintenance.domain.enums.CostLineState;
import com.smartdroneinspection.maintenance.domain.enums.EstimateStatus;
import com.smartdroneinspection.maintenance.domain.enums.ReportVersionStatus;
import com.smartdroneinspection.maintenance.domain.enums.WorkLogStatus;
import com.smartdroneinspection.maintenance.domain.enums.WorkOrderStatus;
import com.smartdroneinspection.maintenance.events.AcceptanceRecordedEvent;
import com.smartdroneinspection.maintenance.events.ChangeOrderApprovedEvent;
import com.smartdroneinspection.maintenance.events.WorkOrderClosedEvent;
import com.smartdroneinspection.maintenance.render.MaintenanceReportRenderContext;
import com.smartdroneinspection.maintenance.render.MaintenanceReportRenderer;
import com.smartdroneinspection.maintenance.repository.MaintenanceAcceptanceDecisionRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceChangeOrderRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceCostLineRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceEstimateVersionRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceReportVersionRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTaskRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTeamMemberRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceWorkLogRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceWorkOrderRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MF4-09 to MF4-21: execution, change control, completion reporting, independent acceptance and
 * cost reconciliation.
 *
 * <p>Completion is never acceptance. The team declares its work done, but only the designated
 * independent reviewer can accept it, and closure additionally requires reconciliation.
 */
@Service
public class MaintenanceExecutionService {

  private static final Logger log = LoggerFactory.getLogger(MaintenanceExecutionService.class);

  private final MaintenanceWorkOrderRepository workOrders;
  private final MaintenanceTeamMemberRepository teamMembers;
  private final MaintenanceTaskRepository tasks;
  private final MaintenanceWorkLogRepository workLogs;
  private final MaintenanceChangeOrderRepository changeOrders;
  private final MaintenanceEstimateVersionRepository estimates;
  private final MaintenanceCostLineRepository costLines;
  private final MaintenanceReportVersionRepository reportVersions;
  private final MaintenanceAcceptanceDecisionRepository acceptanceDecisions;
  private final WorkOrderService workOrderService;
  private final UserAccess userAccess;
  private final ApplicationEventPublisher eventPublisher;
  private final Optional<EvidenceObjectStore> objectStore;
  private final MaintenanceReportRenderer reportRenderer;

  public MaintenanceExecutionService(
      MaintenanceWorkOrderRepository workOrders,
      MaintenanceTeamMemberRepository teamMembers,
      MaintenanceTaskRepository tasks,
      MaintenanceWorkLogRepository workLogs,
      MaintenanceChangeOrderRepository changeOrders,
      MaintenanceEstimateVersionRepository estimates,
      MaintenanceCostLineRepository costLines,
      MaintenanceReportVersionRepository reportVersions,
      MaintenanceAcceptanceDecisionRepository acceptanceDecisions,
      WorkOrderService workOrderService,
      UserAccess userAccess,
      ApplicationEventPublisher eventPublisher,
      Optional<EvidenceObjectStore> objectStore,
      MaintenanceReportRenderer reportRenderer) {
    this.workOrders = workOrders;
    this.teamMembers = teamMembers;
    this.tasks = tasks;
    this.workLogs = workLogs;
    this.changeOrders = changeOrders;
    this.estimates = estimates;
    this.costLines = costLines;
    this.reportVersions = reportVersions;
    this.acceptanceDecisions = acceptanceDecisions;
    this.workOrderService = workOrderService;
    this.userAccess = userAccess;
    this.eventPublisher = eventPublisher;
    this.objectStore = objectStore;
    this.reportRenderer = reportRenderer;
  }

  /** MF4-09: an approved work order is released to the team. */
  @Transactional
  public WorkOrderResponse markReady(UUID actorId, UUID workOrderId) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    requireTeamLead(order, actorId);
    transition(() -> order.markReady());
    return save(order);
  }

  /** MF4-18: after reviewer-returned rework is complete, the team lead resumes execution. */
  @Transactional
  public WorkOrderResponse resumeAfterRework(UUID actorId, UUID workOrderId) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    requireTeamLead(order, actorId);
    transition(order::resumeExecutionAfterRework);
    return save(order);
  }

  /** MF4-10: the lead confirms the team has started. */
  @Transactional
  public WorkOrderResponse markInProgress(UUID actorId, UUID workOrderId) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    requireTeamLead(order, actorId);
    transition(() -> order.markInProgress());
    return save(order);
  }

  /**
   * MF4-14: the team declares the physical work complete. This is explicitly not acceptance and the
   * declaration alone never closes anything.
   */
  @Transactional
  public WorkOrderResponse markWorkCompleted(UUID actorId, UUID workOrderId) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    requireTeamLead(order, actorId);
    long submitted = workLogs.countByWorkOrderId(order.getId());
    long verified = workLogs.countByWorkOrderIdAndStatus(order.getId(), WorkLogStatus.VERIFIED);
    transition(() -> order.markWorkCompleted(submitted, verified));
    return save(order);
  }

  /** MF4-10/11: the assigned engineer records time on a task. */
  @Transactional
  public WorkLogResponse recordWork(UUID actorId, UUID workOrderId, RecordWorkLogRequest request) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    requireTeamMember(order, actorId);
    if (order.getStatus() == WorkOrderStatus.CLOSED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "WORK_ORDER_CLOSED", "Cannot record work on a closed work order");
    }
    tasks
        .findByIdAndWorkOrderId(request.taskId(), order.getId())
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found on this work order"));

    MaintenanceWorkLog log =
        new MaintenanceWorkLog(
            order.getId(), request.taskId(), actorId, request.startedAt(), request.hours());
    log.recordProgress(
        request.startedAt(),
        request.endedAt(),
        request.hours(),
        request.actualCostReferences(),
        request.asLeftCondition(),
        request.testReadings());
    workLogs.saveAndFlush(log);
    return WorkLogResponse.from(log);
  }

  @Transactional
  public WorkLogResponse submitWorkLog(UUID actorId, UUID workOrderId, UUID workLogId) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    MaintenanceWorkLog log = requireWorkLog(order, workLogId);
    if (!actorId.equals(log.getEngineerUserId())) {
      throw denied("Only the engineer who recorded this work may submit it");
    }
    transition(log::submit);
    workLogs.saveAndFlush(log);
    return WorkLogResponse.from(log);
  }

  /** MF4-11: only the lead can verify a submitted work log. */
  @Transactional
  public WorkLogResponse verifyWorkLog(UUID actorId, UUID workOrderId, UUID workLogId) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    requireTeamLead(order, actorId);
    MaintenanceWorkLog log = requireWorkLog(order, workLogId);
    transition(log::verify);
    workLogs.saveAndFlush(log);
    return WorkLogResponse.from(log);
  }

  @Transactional(readOnly = true)
  public List<WorkLogResponse> listWorkLogs(UUID actorId, UUID workOrderId) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    return workLogs.findByWorkOrderIdOrderByStartedAtAsc(order.getId()).stream()
        .map(WorkLogResponse::from)
        .toList();
  }

  /** MF4-12: a change proposal. It authorizes nothing until a decision is recorded. */
  @Transactional
  public ChangeOrderResponse proposeChange(
      UUID actorId, UUID workOrderId, CreateChangeOrderRequest request) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    requireTeamMember(order, actorId);
    if (order.getStatus() == WorkOrderStatus.CLOSED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "WORK_ORDER_CLOSED", "Cannot propose changes on a closed work order");
    }

    int nextNumber =
        changeOrders
            .findFirstByWorkOrderIdOrderByChangeNumberDesc(order.getId())
            .map(change -> change.getChangeNumber() + 1)
            .orElse(1);
    MaintenanceChangeOrder change =
        new MaintenanceChangeOrder(
            order.getId(),
            nextNumber,
            request.reason(),
            request.affectedTasks(),
            request.proposedDelta(),
            request.supportingEvidence(),
            request.proposedStartAt(),
            request.proposedEndAt(),
            actorId);
    change.submit();
    changeOrders.saveAndFlush(change);
    return ChangeOrderResponse.from(change);
  }

  @Transactional(readOnly = true)
  public List<ChangeOrderResponse> listChanges(UUID actorId, UUID workOrderId) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    return changeOrders.findByWorkOrderIdOrderByChangeNumberDesc(order.getId()).stream()
        .map(ChangeOrderResponse::from)
        .toList();
  }

  /** MF4-13: only the designated budget approver decides a change. */
  @Transactional
  public ChangeOrderResponse decideChange(
      UUID actorId,
      UUID workOrderId,
      int changeNumber,
      com.smartdroneinspection.maintenance.api.dto.request.ChangeDecisionRequest request,
      boolean approve,
      boolean returnForClarification) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    if (order.getStatus() == WorkOrderStatus.CLOSED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "WORK_ORDER_CLOSED", "Cannot decide changes on a closed work order");
    }
    if (!order.getBudgetApproverUserId().equals(actorId)) {
      throw denied("Only the designated budget approver may decide a change");
    }
    MaintenanceChangeOrder change =
        changeOrders
            .findByWorkOrderIdAndChangeNumber(order.getId(), changeNumber)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "CHANGE_ORDER_NOT_FOUND", "Change order not found"));
    try {
      if (approve) {
        change.approve(actorId, request == null ? null : request.reason());
      } else if (returnForClarification) {
        change.returnForClarification(actorId, request == null ? null : request.reason());
      } else {
        change.reject(actorId, request == null ? null : request.reason());
      }
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw conflict(ex);
    }
    changeOrders.saveAndFlush(change);
    if (approve && eventPublisher != null) {
      eventPublisher.publishEvent(
          new ChangeOrderApprovedEvent(
              order.getId(),
              change.getChangeNumber(),
              order.getOrganizationId(),
              actorId,
              change.getRequestedByUserId(),
              order.getTeamLeadUserId(),
              change.getProposedDelta(),
              Instant.now()));
    }
    return ChangeOrderResponse.from(change);
  }

  /** MF4-14/16: the report author opens a completion report version. */
  @Transactional
  public ReportVersionResponse createReportVersion(
      UUID actorId, UUID workOrderId, CreateReportVersionRequest request) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    if (!actorId.equals(order.getReportAuthorUserId())) {
      throw denied("Only the designated report author may open a completion report");
    }
    int nextVersion =
        reportVersions
            .findFirstByWorkOrderIdOrderByVersionNoDesc(order.getId())
            .map(report -> report.getVersionNo() + 1)
            .orElse(1);
    MaintenanceReportVersion report =
        new MaintenanceReportVersion(
            order.getId(),
            nextVersion,
            actorId,
            request.contentSnapshot(),
            request.approvedScopeHash(),
            request.changeSnapshotHash(),
            request.workLogSnapshotHash(),
            request.actualCostSnapshotHash(),
            request.llmProvider(),
            request.llmModel(),
            request.promptVersion());
    reportVersions.saveAndFlush(report);
    return ReportVersionResponse.from(report);
  }

  /** MF4-16: only the author verifies the report against the team's records. */
  @Transactional
  public ReportVersionResponse verifyReport(UUID actorId, UUID workOrderId, int versionNo) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    MaintenanceReportVersion report = requireReport(order, versionNo);
    try {
      report.verifyAsAuthor(actorId);
    } catch (IllegalStateException ex) {
      throw conflict(ex);
    }
    reportVersions.saveAndFlush(report);
    return ReportVersionResponse.from(report);
  }

  /** MF4-17: the author submits the report; the work order moves to acceptance review. */
  @Transactional
  public ReportVersionResponse submitReport(UUID actorId, UUID workOrderId, int versionNo) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    MaintenanceReportVersion report = requireReport(order, versionNo);
    if (!actorId.equals(order.getReportAuthorUserId())) {
      throw denied("Only the designated report author may submit this report");
    }
    try {
      report.submit();
      order.markSubmittedForAcceptance();
    } catch (IllegalStateException ex) {
      throw conflict(ex);
    }
    reportVersions.saveAndFlush(report);
    workOrders.saveAndFlush(order);
    return ReportVersionResponse.from(report);
  }

  @Transactional(readOnly = true)
  public List<ReportVersionResponse> listReportVersions(UUID actorId, UUID workOrderId) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    return reportVersions.findByWorkOrderIdOrderByVersionNoDesc(order.getId()).stream()
        .map(ReportVersionResponse::from)
        .toList();
  }

  /**
   * MF4-18: the independent reviewer decides on a submitted completion report.
   *
   * <p>REINSPECTION_REQUIRED records the decision and leaves the order resumable. Dispatching the
   * linked MF1 inspection is MF1's responsibility and is not performed here.
   */
  @Transactional
  public AcceptanceDecisionResponse recordAcceptance(
      UUID actorId, UUID workOrderId, RecordAcceptanceRequest request) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    MaintenanceReportVersion report =
        reportVersions
            .findFirstByWorkOrderIdAndStatusOrderByVersionNoDesc(
                order.getId(), ReportVersionStatus.SUBMITTED)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "REPORT_VERSION_NOT_FOUND",
                        "No submitted completion report to review"));

    MaintenanceAcceptanceDecision decision;
    try {
      switch (request.decision()) {
        case ACCEPTED -> {
          order.accept(actorId);
          report.approveReport();
          storePublishedArtefact(order, report);
        }
        case REWORK_REQUIRED -> {
          order.requireReworkAfterAcceptance(actorId, request.technicalComments());
          report.returnToAuthor(request.technicalComments());
        }
        case REINSPECTION_REQUIRED -> {
          order.requireReinspection(actorId, request.technicalComments());
          report.returnToAuthor(request.technicalComments());
          if (eventPublisher != null) {
            eventPublisher.publishEvent(
                new ReinspectionRequestedEvent(
                    order.getId(),
                    report.getId(),
                    order.getOrganizationId(),
                    order.getAssetId(),
                    order.getSourceReportVersionId(),
                    order.getSourceFindingId(),
                    actorId,
                    request.technicalComments(),
                    Instant.now()));
          }
        }
        case REJECTED ->
            throw new BusinessException(
                HttpStatus.BAD_REQUEST,
                "ACCEPTANCE_DECISION_UNSUPPORTED",
                "A terminal rejection is not part of MF4; record rework or re-inspection instead");
      }
      decision =
          new MaintenanceAcceptanceDecision(
              order.getId(),
              report.getId(),
              actorId,
              request.decision(),
              request.technicalComments(),
              request.acceptanceChecklist(),
              request.testResult(),
              request.signatureReference());
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw conflict(ex);
    }
    acceptanceDecisions.saveAndFlush(decision);
    workOrders.saveAndFlush(order);
    reportVersions.saveAndFlush(report);
    if (eventPublisher != null) {
      eventPublisher.publishEvent(
          new AcceptanceRecordedEvent(
              order.getId(),
              report.getId(),
              order.getOrganizationId(),
              order.getAssetId(),
              actorId,
              decision.getDecision().name(),
              decision.getTechnicalComments(),
              order.getTeamLeadUserId(),
              order.getReportAuthorUserId(),
              decision.getDecidedAt()));
    }
    return AcceptanceDecisionResponse.from(decision);
  }

  @Transactional(readOnly = true)
  public List<AcceptanceDecisionResponse> listAcceptanceDecisions(UUID actorId, UUID workOrderId) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    return acceptanceDecisions.findByWorkOrderIdOrderByDecidedAtDesc(order.getId()).stream()
        .map(AcceptanceDecisionResponse::from)
        .toList();
  }

  /**
   * MF4-19: cost reconciliation.
   *
   * <p>The SYSTEM derives every figure: the authorized amount is the approved baseline plus the sum
   * of approved change deltas, the actual total is the sum of the supplied actual lines, and the
   * variance follows. A percentage against a zero authorized amount is reported as null because it
   * has no meaning.
   */
  @Transactional
  public CostReconciliationResponse reconcile(
      UUID actorId, UUID workOrderId, ReconcileCostsRequest request) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    requireIndependentReviewer(order, actorId);

    MaintenanceEstimateVersion approved =
        estimates
            .findByWorkOrderIdAndStatus(order.getId(), EstimateStatus.APPROVED)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT,
                        "ESTIMATE_NOT_APPROVED",
                        "Cost reconciliation requires an approved estimate baseline"));

    BigDecimal baseline = approved.getBaselineTotal();
    BigDecimal changeDelta =
        changeOrders.sumDeltaByWorkOrderIdAndStatus(order.getId(), ChangeOrderStatus.APPROVED);
    BigDecimal authorized =
        baseline
            .add(changeDelta == null ? BigDecimal.ZERO : changeDelta)
            .setScale(2, RoundingMode.HALF_UP);

    BigDecimal actualTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    for (RecordActualCostLineRequest line : request.actualLines()) {
      if (!approved.getCurrency().equals(line.currency())) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "COST_LINE_CURRENCY_MISMATCH",
            "Actual cost lines must use the approved estimate currency");
      }
      tasks
          .findByIdAndWorkOrderId(line.taskId(), order.getId())
          .orElseThrow(
              () ->
                  new BusinessException(
                      HttpStatus.BAD_REQUEST,
                      "COST_LINE_TASK_UNKNOWN",
                      "An actual cost line references an unknown task"));
      MaintenanceCostLine actual;
      try {
        actual =
            new MaintenanceCostLine(
                order.getId(),
                null,
                line.taskId(),
                line.lineKind(),
                CostLineState.ACTUAL,
                line.description(),
                line.quantity(),
                line.unit(),
                line.unitRate(),
                line.currency(),
                line.taxTreatment(),
                line.evidenceReference(),
                actorId);
      } catch (IllegalArgumentException ex) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "COST_LINE_INVALID", ex.getMessage());
      }
      costLines.saveAndFlush(actual);
      actualTotal = actualTotal.add(actual.getAmount());
    }

    BigDecimal variance = actualTotal.subtract(authorized).setScale(2, RoundingMode.HALF_UP);
    BigDecimal variancePercent =
        authorized.signum() == 0
            ? null
            : variance
                .multiply(BigDecimal.valueOf(100))
                .divide(authorized, 2, RoundingMode.HALF_UP);

    transition(order::markCostsReconciled);
    workOrders.saveAndFlush(order);

    return new CostReconciliationResponse(
        baseline,
        changeDelta,
        authorized,
        actualTotal,
        variance,
        variancePercent,
        approved.getCurrency());
  }

  /** MF4-21: closure requires independent acceptance and reconciled costs. */
  @Transactional
  public WorkOrderResponse close(UUID actorId, UUID workOrderId) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    requireIndependentReviewer(order, actorId);
    transition(order::close);
    workOrders.saveAndFlush(order);
    if (eventPublisher != null) {
      eventPublisher.publishEvent(
          new WorkOrderClosedEvent(
              order.getId(),
              order.getOrganizationId(),
              order.getAssetId(),
              actorId,
              Instant.now()));
    }
    return WorkOrderResponse.from(order);
  }

  @Transactional
  public ResponseEntity<byte[]> getReportPdf(
      UUID actorId, UUID workOrderId, int versionNo, boolean preview) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    MaintenanceReportVersion report = requireReport(order, versionNo);

    if (preview) {
      if (!actorId.equals(order.getReportAuthorUserId())
          && !actorId.equals(order.getTeamLeadUserId())) {
        throw denied("Only the report author or team lead may preview this draft");
      }
      MaintenanceReportRenderContext context = buildRenderContext(order, report);
      try {
        byte[] pdf = reportRenderer.render(context, true);
        return pdfResponse(pdf, "inline", documentReference(order, report) + "-preview.pdf");
      } catch (IOException ex) {
        throw new BusinessException(
            HttpStatus.SERVICE_UNAVAILABLE, "REPORT_RENDER_UNAVAILABLE", ex.getMessage());
      }
    }

    String objectKey = report.getRenderedObjectKey();
    if (objectKey == null || objectKey.isBlank()) {
      MaintenanceReportRenderContext context = buildRenderContext(order, report);
      try {
        byte[] pdf = reportRenderer.render(context, false);
        objectKey = maintenanceReportObjectKey(report.getId());
        if (objectStore != null && objectStore.isPresent()) {
          try (ByteArrayInputStream in = new ByteArrayInputStream(pdf)) {
            objectStore.get().put(objectKey, "application/pdf", pdf.length, in);
          }
        }
        if (report.getStatus() == ReportVersionStatus.APPROVED) {
          report.recordArtefact(objectKey, sha256Hex(pdf));
          reportVersions.saveAndFlush(report);
        }
        return pdfResponse(pdf, "attachment", documentReference(order, report) + ".pdf");
      } catch (IOException ex) {
        throw new BusinessException(
            HttpStatus.SERVICE_UNAVAILABLE, "REPORT_RENDER_UNAVAILABLE", ex.getMessage());
      }
    }

    try {
      byte[] content =
          objectStore
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.SERVICE_UNAVAILABLE,
                          "STORAGE_UNAVAILABLE",
                          "Object store is unavailable"))
              .open(objectKey)
              .readAllBytes();
      return pdfResponse(content, "attachment", documentReference(order, report) + ".pdf");
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE, "REPORT_STORAGE_READ_FAILED", ex.getMessage());
    }
  }

  private static String maintenanceReportObjectKey(UUID reportVersionId) {
    return "maintenance-reports/" + reportVersionId + ".pdf";
  }

  private static String documentReference(
      MaintenanceWorkOrder order, MaintenanceReportVersion report) {
    return "SDI-MNT-WO-"
        + order.getId().toString().substring(0, 8).toUpperCase()
        + "-V"
        + report.getVersionNo();
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private void storePublishedArtefact(MaintenanceWorkOrder order, MaintenanceReportVersion report) {
    if (reportRenderer == null) {
      return;
    }
    try {
      MaintenanceReportRenderContext context = buildRenderContext(order, report);
      byte[] pdf = reportRenderer.render(context, false);
      String objectKey = maintenanceReportObjectKey(report.getId());
      if (objectStore != null && objectStore.isPresent()) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(pdf)) {
          objectStore.get().put(objectKey, "application/pdf", pdf.length, in);
        }
      }
      report.recordArtefact(objectKey, sha256Hex(pdf));
    } catch (IOException ex) {
      log.warn("Failed to render or store completion report PDF artifact: {}", ex.getMessage());
    }
  }

  private static ResponseEntity<byte[]> pdfResponse(
      byte[] content, String disposition, String filename) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.builder(disposition).filename(filename).build().toString())
        .body(content);
  }

  private MaintenanceReportRenderContext buildRenderContext(
      MaintenanceWorkOrder order, MaintenanceReportVersion report) {
    String docRef = documentReference(order, report);

    String authorName =
        userAccess
            .findActiveUser(report.getAuthorUserId())
            .map(UserAccess.ActiveUser::fullName)
            .orElse("Author");
    String leadName =
        userAccess
            .findActiveUser(order.getTeamLeadUserId())
            .map(UserAccess.ActiveUser::fullName)
            .orElse("Lead");
    String reviewerName =
        userAccess
            .findActiveUser(order.getAcceptingReviewerUserId())
            .map(UserAccess.ActiveUser::fullName)
            .orElse("Reviewer");
    String budgetApproverName =
        userAccess
            .findActiveUser(order.getBudgetApproverUserId())
            .map(UserAccess.ActiveUser::fullName)
            .orElse("Approver");

    List<MaintenanceReportRenderContext.TeamMemberView> memberViews =
        teamMembers.findByWorkOrderIdAndActiveTrueOrderByCreatedAtAsc(order.getId()).stream()
            .map(
                m ->
                    new MaintenanceReportRenderContext.TeamMemberView(
                        userAccess
                            .findActiveUser(m.getEngineerUserId())
                            .map(UserAccess.ActiveUser::fullName)
                            .orElse("Member"),
                        m.getMemberRole().name(),
                        m.getReason()))
            .toList();

    List<MaintenanceTask> taskList = tasks.findByWorkOrderIdOrderByTaskNumberAsc(order.getId());
    Map<UUID, Integer> taskNumberMap =
        taskList.stream()
            .collect(Collectors.toMap(MaintenanceTask::getId, MaintenanceTask::getTaskNumber));

    List<MaintenanceReportRenderContext.TaskView> taskViews =
        taskList.stream()
            .map(
                t ->
                    new MaintenanceReportRenderContext.TaskView(
                        t.getTaskNumber(),
                        t.getName(),
                        t.getMethod(),
                        t.getStatus().name(),
                        t.getAssignedEngineerUserId() != null
                            ? userAccess
                                .findActiveUser(t.getAssignedEngineerUserId())
                                .map(UserAccess.ActiveUser::fullName)
                                .orElse("Unassigned")
                            : "Unassigned"))
            .toList();

    List<MaintenanceReportRenderContext.WorkLogView> logViews =
        workLogs.findByWorkOrderIdOrderByStartedAtAsc(order.getId()).stream()
            .map(
                wl ->
                    new MaintenanceReportRenderContext.WorkLogView(
                        taskNumberMap.getOrDefault(wl.getTaskId(), 1),
                        userAccess
                            .findActiveUser(wl.getEngineerUserId())
                            .map(UserAccess.ActiveUser::fullName)
                            .orElse("Engineer"),
                        wl.getStartedAt() != null ? wl.getStartedAt().toString() : "N/A",
                        wl.getEndedAt() != null ? wl.getEndedAt().toString() : "N/A",
                        wl.getHours() != null ? wl.getHours().toString() : "0",
                        wl.getAsLeftCondition(),
                        wl.getTestReadings()))
            .toList();

    List<MaintenanceReportRenderContext.ChangeOrderView> changeViews =
        changeOrders.findByWorkOrderIdOrderByChangeNumberDesc(order.getId()).stream()
            .map(
                co ->
                    new MaintenanceReportRenderContext.ChangeOrderView(
                        co.getChangeNumber(),
                        co.getReason(),
                        co.getProposedDelta() != null ? co.getProposedDelta().toString() : "0",
                        co.getStatus().name(),
                        co.getDecidedAt() != null ? co.getDecidedAt().toString() : "Pending"))
            .toList();

    MaintenanceReportRenderContext.CostStatementView costStatement = null;
    Optional<MaintenanceEstimateVersion> approvedEstimate =
        estimates.findByWorkOrderIdAndStatus(order.getId(), EstimateStatus.APPROVED);
    if (approvedEstimate.isPresent()) {
      MaintenanceEstimateVersion est = approvedEstimate.get();
      BigDecimal baseline = est.getBaselineTotal();
      BigDecimal changeDelta =
          changeOrders.sumDeltaByWorkOrderIdAndStatus(order.getId(), ChangeOrderStatus.APPROVED);
      BigDecimal effective = changeDelta == null ? BigDecimal.ZERO : changeDelta;
      BigDecimal authorized = baseline.add(effective).setScale(2, RoundingMode.HALF_UP);
      BigDecimal actual =
          costLines.sumAmountByWorkOrderIdAndState(order.getId(), CostLineState.ACTUAL);
      BigDecimal actualTotal =
          actual == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : actual;
      BigDecimal variance = actualTotal.subtract(authorized).setScale(2, RoundingMode.HALF_UP);
      BigDecimal variancePercent =
          authorized.signum() == 0
              ? null
              : variance
                  .multiply(BigDecimal.valueOf(100))
                  .divide(authorized, 2, RoundingMode.HALF_UP);

      costStatement =
          new MaintenanceReportRenderContext.CostStatementView(
              est.getCurrency(),
              baseline.toString(),
              effective.toString(),
              authorized.toString(),
              actualTotal.toString(),
              variance.toString(),
              variancePercent != null ? variancePercent.toString() + "%" : "N/A");
    }

    MaintenanceReportRenderContext.AcceptanceView acceptanceView = null;
    Optional<MaintenanceAcceptanceDecision> decisionOpt =
        acceptanceDecisions.findFirstByWorkOrderIdOrderByDecidedAtDesc(order.getId());
    if (decisionOpt.isPresent()) {
      MaintenanceAcceptanceDecision dec = decisionOpt.get();
      acceptanceView =
          new MaintenanceReportRenderContext.AcceptanceView(
              dec.getDecision().name(),
              userAccess
                  .findActiveUser(dec.getReviewerUserId())
                  .map(UserAccess.ActiveUser::fullName)
                  .orElse("Reviewer"),
              dec.getDecidedAt() != null ? dec.getDecidedAt().toString() : "N/A",
              dec.getTechnicalComments(),
              dec.getSignatureReference());
    }

    return new MaintenanceReportRenderContext(
        docRef,
        report.getVersionNo(),
        report.getStatus().name(),
        order.getOrganizationId().toString(),
        order.getAssetId().toString(),
        order.getSourceFindingId().toString(),
        "Corrective maintenance for finding " + order.getSourceFindingId(),
        "Approved corrective defect repair",
        "Technical acceptance by independent reviewer",
        authorName,
        report.getAuthorVerifiedAt() != null
            ? report.getAuthorVerifiedAt().toString()
            : "Not verified",
        leadName,
        reviewerName,
        budgetApproverName,
        memberViews,
        taskViews,
        logViews,
        changeViews,
        costStatement,
        report.getContentSnapshot(),
        acceptanceView);
  }

  @Transactional(readOnly = true)
  public CostReconciliationResponse reconciliationSummary(UUID actorId, UUID workOrderId) {
    MaintenanceWorkOrder order = workOrderService.requireWorkOrderInScope(actorId, workOrderId);
    return estimates
        .findByWorkOrderIdAndStatus(order.getId(), EstimateStatus.APPROVED)
        .map(
            approved -> {
              BigDecimal baseline = approved.getBaselineTotal();
              BigDecimal changeDelta =
                  changeOrders.sumDeltaByWorkOrderIdAndStatus(
                      order.getId(), ChangeOrderStatus.APPROVED);
              BigDecimal effective = changeDelta == null ? BigDecimal.ZERO : changeDelta;
              BigDecimal authorized = baseline.add(effective).setScale(2, RoundingMode.HALF_UP);
              BigDecimal actual =
                  costLines.sumAmountByWorkOrderIdAndState(order.getId(), CostLineState.ACTUAL);
              BigDecimal reconciled =
                  actual == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : actual;
              BigDecimal variance =
                  reconciled.subtract(authorized).setScale(2, RoundingMode.HALF_UP);
              BigDecimal percent =
                  authorized.signum() == 0
                      ? null
                      : variance
                          .multiply(BigDecimal.valueOf(100))
                          .divide(authorized, 2, RoundingMode.HALF_UP);
              return new CostReconciliationResponse(
                  baseline,
                  effective,
                  authorized,
                  reconciled,
                  variance,
                  percent,
                  approved.getCurrency());
            })
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "ESTIMATE_VERSION_NOT_FOUND",
                    "No approved estimate baseline"));
  }

  private WorkOrderResponse save(MaintenanceWorkOrder order) {
    workOrders.saveAndFlush(order);
    return WorkOrderResponse.from(order);
  }

  private MaintenanceWorkLog requireWorkLog(MaintenanceWorkOrder order, UUID workLogId) {
    return workLogs
        .findById(workLogId)
        .filter(log -> log.getWorkOrderId().equals(order.getId()))
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "WORK_LOG_NOT_FOUND",
                    "Work log not found on this work order"));
  }

  private MaintenanceReportVersion requireReport(MaintenanceWorkOrder order, int versionNo) {
    return reportVersions
        .findByWorkOrderIdAndVersionNo(order.getId(), versionNo)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "REPORT_VERSION_NOT_FOUND",
                    "Completion report version not found"));
  }

  private void requireTeamLead(MaintenanceWorkOrder order, UUID actorId) {
    if (!actorId.equals(order.getTeamLeadUserId())) {
      throw denied("Only the designated team lead may perform this action");
    }
  }

  private void requireTeamMember(MaintenanceWorkOrder order, UUID actorId) {
    if (!teamMembers.existsByWorkOrderIdAndEngineerUserIdAndActiveTrue(order.getId(), actorId)) {
      throw denied("Only an active member of the designated team may record work");
    }
  }

  /** MF4-18/20: only the independent reviewer outside the executing team may act. */
  private void requireIndependentReviewer(MaintenanceWorkOrder order, UUID actorId) {
    if (!actorId.equals(order.getAcceptingReviewerUserId())) {
      throw denied("Only the designated independent reviewer may perform this action");
    }
    if (actorId.equals(order.getTeamLeadUserId())
        || actorId.equals(order.getReportAuthorUserId())) {
      throw denied("The executing team cannot review its own work");
    }
  }

  private static void transition(Runnable action) {
    try {
      action.run();
    } catch (IllegalStateException ex) {
      throw conflict(ex);
    }
  }

  private static BusinessException denied(String message) {
    return new BusinessException(HttpStatus.FORBIDDEN, "WORK_ORDER_SCOPE_DENIED", message);
  }

  private static BusinessException conflict(RuntimeException cause) {
    return new BusinessException(
        HttpStatus.CONFLICT, "WORK_ORDER_STATE_CONFLICT", cause.getMessage());
  }
}

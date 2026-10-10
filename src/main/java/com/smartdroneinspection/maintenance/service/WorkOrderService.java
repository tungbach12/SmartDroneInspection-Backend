package com.smartdroneinspection.maintenance.service;

import com.smartdroneinspection.inspections.spi.RepairCandidate;
import com.smartdroneinspection.inspections.spi.RepairCandidateAccess;
import com.smartdroneinspection.maintenance.api.dto.request.AssignTeamRequest;
import com.smartdroneinspection.maintenance.api.dto.request.CreateEstimateVersionRequest;
import com.smartdroneinspection.maintenance.api.dto.request.CreateTaskRequest;
import com.smartdroneinspection.maintenance.api.dto.request.CreateWorkOrderRequest;
import com.smartdroneinspection.maintenance.api.dto.response.EstimateVersionResponse;
import com.smartdroneinspection.maintenance.api.dto.response.RepairCandidateResponse;
import com.smartdroneinspection.maintenance.api.dto.response.TaskResponse;
import com.smartdroneinspection.maintenance.api.dto.response.TeamMemberResponse;
import com.smartdroneinspection.maintenance.api.dto.response.WorkOrderPageResponse;
import com.smartdroneinspection.maintenance.api.dto.response.WorkOrderResponse;
import com.smartdroneinspection.maintenance.domain.MaintenanceCostLine;
import com.smartdroneinspection.maintenance.domain.MaintenanceEstimateVersion;
import com.smartdroneinspection.maintenance.domain.MaintenanceTask;
import com.smartdroneinspection.maintenance.domain.MaintenanceTeamMember;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkOrder;
import com.smartdroneinspection.maintenance.domain.enums.CostLineState;
import com.smartdroneinspection.maintenance.domain.enums.TeamMemberRole;
import com.smartdroneinspection.maintenance.domain.enums.WorkOrderStatus;
import com.smartdroneinspection.maintenance.repository.MaintenanceCostLineRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceEstimateVersionRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTaskRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTeamMemberRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceWorkOrderRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.workforce.spi.WorkforceAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MF4-01 to MF4-08: raising a work order, designating its team, planning tasks and approving a cost
 * baseline.
 *
 * <p>Authorization follows Report 3's four layers: role, organization, assignment and separation of
 * duties. A caller outside the record's organization receives not-found rather than forbidden, so
 * one tenant cannot probe for another tenant's work orders.
 */
@Service
public class WorkOrderService {

  /**
   * MF4-01: statuses that represent live corrective work. A finished or abandoned order does not
   * block a new one for the same finding.
   */
  private static final List<WorkOrderStatus> ACTIVE_STATUSES =
      List.of(
          WorkOrderStatus.DRAFT,
          WorkOrderStatus.AWAITING_APPROVAL,
          WorkOrderStatus.APPROVED,
          WorkOrderStatus.READY,
          WorkOrderStatus.IN_PROGRESS,
          WorkOrderStatus.WORK_COMPLETED,
          WorkOrderStatus.SUBMITTED_FOR_ACCEPTANCE,
          WorkOrderStatus.REWORK_REQUIRED,
          WorkOrderStatus.REINSPECTION_REQUIRED);

  private final MaintenanceWorkOrderRepository workOrders;
  private final MaintenanceTeamMemberRepository teamMembers;
  private final MaintenanceTaskRepository tasks;
  private final MaintenanceEstimateVersionRepository estimates;
  private final MaintenanceCostLineRepository costLines;
  private final WorkforceAccess workforceAccess;
  private final RepairCandidateAccess repairCandidateAccess;
  private final UserAccess userAccess;

  public WorkOrderService(
      MaintenanceWorkOrderRepository workOrders,
      MaintenanceTeamMemberRepository teamMembers,
      MaintenanceTaskRepository tasks,
      MaintenanceEstimateVersionRepository estimates,
      MaintenanceCostLineRepository costLines,
      WorkforceAccess workforceAccess,
      RepairCandidateAccess repairCandidateAccess,
      UserAccess userAccess) {
    this.workOrders = workOrders;
    this.teamMembers = teamMembers;
    this.tasks = tasks;
    this.estimates = estimates;
    this.costLines = costLines;
    this.workforceAccess = workforceAccess;
    this.repairCandidateAccess = repairCandidateAccess;
    this.userAccess = userAccess;
  }

  @Transactional(readOnly = true)
  public List<RepairCandidateResponse> repairCandidates(UUID actorId) {
    UserAccess.ActiveUser actor = requireOrganizationAdmin(actorId);
    return repairCandidateAccess.findRepairCandidates(actor.organizationId()).stream()
        .filter(
            candidate ->
                !workOrders.existsBySourceFindingIdAndStatusIn(
                    candidate.findingId(), ACTIVE_STATUSES))
        .map(WorkOrderService::toRepairCandidateResponse)
        .toList();
  }

  private static RepairCandidateResponse toRepairCandidateResponse(RepairCandidate candidate) {
    return new RepairCandidateResponse(
        candidate.assetId(),
        candidate.inspectionId(),
        candidate.reportId(),
        candidate.reportVersionId(),
        candidate.findingId(),
        candidate.findingCode(),
        candidate.defectLabel(),
        candidate.description(),
        candidate.severity(),
        candidate.recommendedAction(),
        candidate.reportPublishedAt());
  }

  @Transactional
  public WorkOrderResponse create(UUID actorId, CreateWorkOrderRequest request) {
    UserAccess.ActiveUser actor = requireOrganizationAdmin(actorId);

    // MF4-01: a finding may not have two live work orders without an explicit reason.
    if (workOrders.existsBySourceFindingIdAndStatusIn(request.sourceFindingId(), ACTIVE_STATUSES)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "WORK_ORDER_DUPLICATE_SCOPE",
          "An active work order already covers this finding");
    }

    RepairCandidate source =
        repairCandidateAccess
            .findRepairCandidate(
                actor.organizationId(), request.sourceReportVersionId(), request.sourceFindingId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "WORK_ORDER_SOURCE_INVALID",
                        "The source report version and finding must be published, repair-required, and in this organization"));
    if (!source.assetId().equals(request.assetId())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "WORK_ORDER_SOURCE_INVALID",
          "The work order asset must match the source finding's asset");
    }

    MaintenanceWorkOrder order =
        new MaintenanceWorkOrder(
            actor.organizationId(),
            source.assetId(),
            source.reportVersionId(),
            source.findingId(),
            actor.id(),
            request.budgetApproverUserId());

    try {
      order.triage(
          request.priority(),
          request.dueAt(),
          request.correctiveScope(),
          request.acceptanceCriteria());
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw conflict(ex);
    }
    return WorkOrderResponse.from(workOrders.saveAndFlush(order));
  }

  @Transactional(readOnly = true)
  public WorkOrderResponse get(UUID actorId, UUID workOrderId) {
    return WorkOrderResponse.from(requireWorkOrderInScope(actorId, workOrderId));
  }

  @Transactional(readOnly = true)
  public WorkOrderPageResponse list(UUID actorId, int page, int size) {
    UserAccess.ActiveUser actor = requireActiveUser(actorId);
    if (actor.organizationId() == null) {
      throw forbidden("Maintenance work orders belong to a customer organization");
    }
    Page<MaintenanceWorkOrder> result =
        workOrders.findByOrganizationId(
            actor.organizationId(),
            PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "createdAt")));
    return new WorkOrderPageResponse(
        result.getContent().stream().map(WorkOrderResponse::from).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements());
  }

  /**
   * MF4-03/04: names the lead, report author and independent reviewer, and records the team.
   *
   * <p>Credential checking is conditional. An engineer with a credential record that is not ACTIVE
   * or has expired is refused; an engineer with no record is allowed, because the workforce
   * verification workflow has never run and no record exists yet. Report 3 states this rule as
   * absolute, so the gap is recorded rather than silently widened here.
   */
  @Transactional
  public WorkOrderResponse assignTeam(UUID actorId, UUID workOrderId, AssignTeamRequest request) {
    UserAccess.ActiveUser actor = requireOrganizationAdmin(actorId);
    MaintenanceWorkOrder order = requireWorkOrderInScope(actor.id(), workOrderId);

    requireEligibleEngineer(actor.organizationId(), request.leadUserId(), "team lead");
    requireEligibleEngineer(actor.organizationId(), request.reportAuthorUserId(), "report author");
    UserAccess.ActiveUser reviewer =
        userAccess
            .findActiveUser(request.acceptingReviewerUserId())
            .orElseThrow(() -> forbidden("The accepting reviewer is not an active user"));
    if (!reviewer.hasRole("ORG_ADMIN")
        || !actor.organizationId().equals(reviewer.organizationId())
        || reviewer.id().equals(request.leadUserId())
        || reviewer.id().equals(request.reportAuthorUserId())
        || (request.memberUserIds() != null && request.memberUserIds().contains(reviewer.id()))) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "TEAM_REVIEWER_INVALID",
          "The accepting reviewer must be an active ORG_ADMIN in this organization");
    }

    try {
      order.assignTeam(
          request.leadUserId(), request.reportAuthorUserId(), request.acceptingReviewerUserId());
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw conflict(ex);
    }
    workOrders.saveAndFlush(order);

    recordAssignment(
        order.getId(), request.leadUserId(), TeamMemberRole.LEAD, actor.id(), request.reason());
    recordAssignment(
        order.getId(),
        request.reportAuthorUserId(),
        TeamMemberRole.REPORT_AUTHOR,
        actor.id(),
        request.reason());
    for (UUID memberId :
        request.memberUserIds() == null ? List.<UUID>of() : request.memberUserIds()) {
      requireEligibleEngineer(actor.organizationId(), memberId, "team member");
      if (!memberId.equals(request.leadUserId())
          && !memberId.equals(request.reportAuthorUserId())) {
        recordAssignment(
            order.getId(), memberId, TeamMemberRole.MEMBER, actor.id(), request.reason());
      }
    }
    return WorkOrderResponse.from(order);
  }

  @Transactional(readOnly = true)
  public List<TeamMemberResponse> team(UUID actorId, UUID workOrderId) {
    MaintenanceWorkOrder order = requireWorkOrderInScope(actorId, workOrderId);
    return teamMembers.findByWorkOrderIdAndActiveTrueOrderByCreatedAtAsc(order.getId()).stream()
        .map(TeamMemberResponse::from)
        .toList();
  }

  /** MF4-05: the lead divides approved scope into tasks, numbered by the server. */
  @Transactional
  public TaskResponse createTask(UUID actorId, UUID workOrderId, CreateTaskRequest request) {
    MaintenanceWorkOrder order = requireWorkOrderInScope(actorId, workOrderId);
    requireTeamLead(order, actorId);

    int nextNumber =
        tasks
            .findFirstByWorkOrderIdOrderByTaskNumberDesc(order.getId())
            .map(task -> task.getTaskNumber() + 1)
            .orElse(1);
    if (request.assignedEngineerUserId() != null) {
      if (!teamMembers.existsByWorkOrderIdAndEngineerUserIdAndActiveTrue(
          order.getId(), request.assignedEngineerUserId())) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "TASK_ASSIGNEE_NOT_IN_TEAM",
            "A task may only be assigned to an active member of this work order's team");
      }
    }
    int displayOrder = tasks.findByWorkOrderIdOrderByTaskNumberAsc(order.getId()).size();

    MaintenanceTask task =
        new MaintenanceTask(
            order.getId(),
            nextNumber,
            request.name(),
            request.method(),
            request.assignedEngineerUserId(),
            request.plannedStartAt(),
            request.plannedEndAt(),
            request.acceptanceCriteria(),
            displayOrder);
    try {
      tasks.saveAndFlush(task);
    } catch (RuntimeException ex) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "TASK_NUMBER_CONFLICT", "A task with this number already exists");
    }
    return TaskResponse.from(task);
  }

  @Transactional(readOnly = true)
  public List<TaskResponse> listTasks(UUID actorId, UUID workOrderId) {
    MaintenanceWorkOrder order = requireWorkOrderInScope(actorId, workOrderId);
    return tasks.findByWorkOrderIdOrderByTaskNumberAsc(order.getId()).stream()
        .map(TaskResponse::from)
        .toList();
  }

  /**
   * MF4-06/07: a new estimate version with its priced lines.
   *
   * <p>The SYSTEM derives each line amount and the version total. A line without a unit rate is
   * rejected: an absent price is unknown, not zero.
   */
  @Transactional
  public EstimateVersionResponse createEstimateVersion(
      UUID actorId, UUID workOrderId, CreateEstimateVersionRequest request) {
    MaintenanceWorkOrder order = requireWorkOrderInScope(actorId, workOrderId);
    requireTeamLead(order, actorId);

    int nextVersion =
        estimates
            .findFirstByWorkOrderIdOrderByVersionNoDesc(order.getId())
            .map(version -> version.getVersionNo() + 1)
            .orElse(1);

    Map<UUID, Integer> taskNumbers = taskNumbersById(order.getId());
    List<MaintenanceCostLine> lines = new java.util.ArrayList<>();
    for (CreateEstimateVersionRequest.EstimateLineRequest line : request.lines()) {
      if (line.taskId() != null && !taskNumbers.containsKey(line.taskId())) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "COST_LINE_TASK_UNKNOWN",
            "A cost line references an unknown task");
      }
      try {
        lines.add(
            new MaintenanceCostLine(
                order.getId(),
                null,
                line.taskId(),
                line.lineKind(),
                CostLineState.ESTIMATE,
                line.description(),
                line.quantity(),
                line.unit(),
                line.unitRate(),
                request.currency(),
                request.taxBasis(),
                line.evidenceReference(),
                actorId));
      } catch (IllegalArgumentException ex) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "COST_LINE_INVALID", ex.getMessage());
      }
    }

    BigDecimal total = MaintenanceEstimateVersion.totalOf(lines);
    MaintenanceEstimateVersion version =
        new MaintenanceEstimateVersion(
            order.getId(),
            nextVersion,
            actorId,
            request.currency(),
            request.taxBasis(),
            request.assumptions(),
            request.snapshot(),
            total);
    estimates.saveAndFlush(version);

    for (MaintenanceCostLine line : lines) {
      line.attachToEstimate(version.getId());
      costLines.saveAndFlush(line);
    }
    return EstimateVersionResponse.from(version);
  }

  @Transactional
  public EstimateVersionResponse submitEstimate(UUID actorId, UUID workOrderId, int versionNo) {
    MaintenanceWorkOrder order = requireWorkOrderInScope(actorId, workOrderId);
    MaintenanceEstimateVersion version = requireEstimate(order, versionNo);
    requirePreparer(version, actorId);
    try {
      version.submit();
    } catch (IllegalStateException ex) {
      throw conflict(ex);
    }
    estimates.saveAndFlush(version);
    requestWorkOrderApproval(order);
    return EstimateVersionResponse.from(version);
  }

  /** MF4-08: only the designated budget approver may approve, and never a team member. */
  @Transactional
  public EstimateVersionResponse approveEstimate(UUID actorId, UUID workOrderId, int versionNo) {
    UserAccess.ActiveUser actor = requireOrganizationAdmin(actorId);
    MaintenanceWorkOrder order = requireWorkOrderInScope(actor.id(), workOrderId);
    MaintenanceEstimateVersion version = requireEstimate(order, versionNo);

    if (!order.getBudgetApproverUserId().equals(actor.id())) {
      throw forbidden("Only the designated budget approver may approve this estimate");
    }
    try {
      version.approve(actor.id());
      order.approveEstimate(actor.id());
    } catch (IllegalStateException ex) {
      throw conflict(ex);
    }
    estimates.saveAndFlush(version);
    workOrders.saveAndFlush(order);
    return EstimateVersionResponse.from(version);
  }

  @Transactional
  public EstimateVersionResponse rejectEstimate(
      UUID actorId, UUID workOrderId, int versionNo, String reason) {
    UserAccess.ActiveUser actor = requireOrganizationAdmin(actorId);
    MaintenanceWorkOrder order = requireWorkOrderInScope(actor.id(), workOrderId);
    MaintenanceEstimateVersion version = requireEstimate(order, versionNo);
    if (!order.getBudgetApproverUserId().equals(actor.id())) {
      throw forbidden("Only the designated budget approver may decide this estimate");
    }
    try {
      version.reject(actor.id(), reason);
      order.returnEstimateForRework(actor.id(), reason);
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw conflict(ex);
    }
    estimates.saveAndFlush(version);
    workOrders.saveAndFlush(order);
    return EstimateVersionResponse.from(version);
  }

  @Transactional(readOnly = true)
  public List<EstimateVersionResponse> listEstimateVersions(UUID actorId, UUID workOrderId) {
    MaintenanceWorkOrder order = requireWorkOrderInScope(actorId, workOrderId);
    return estimates.findByWorkOrderIdOrderByVersionNoDesc(order.getId()).stream()
        .map(EstimateVersionResponse::from)
        .toList();
  }

  private void requestWorkOrderApproval(MaintenanceWorkOrder order) {
    try {
      order.submitForApproval();
      workOrders.saveAndFlush(order);
    } catch (IllegalStateException ex) {
      throw conflict(ex);
    }
  }

  private Map<UUID, Integer> taskNumbersById(UUID workOrderId) {
    Map<UUID, Integer> numbers = new java.util.HashMap<>();
    for (MaintenanceTask task : tasks.findByWorkOrderIdOrderByTaskNumberAsc(workOrderId)) {
      numbers.put(task.getId(), task.getTaskNumber());
    }
    return numbers;
  }

  private void recordAssignment(
      UUID workOrderId, UUID engineerUserId, TeamMemberRole role, UUID actorId, String reason) {
    if (role == TeamMemberRole.MEMBER) {
      return;
    }
    teamMembers.saveAndFlush(
        new MaintenanceTeamMember(
            workOrderId, engineerUserId, role, Instant.now(), actorId, reason));
  }

  /**
   * MF4-04: an engineer must be active in the same organization. A credential record that exists
   * but does not currently qualify blocks the assignment; no record at all does not, because
   * credential verification has never run.
   */
  private void requireEligibleEngineer(UUID organizationId, UUID userId, String role) {
    UserAccess.ActiveUser engineer =
        userAccess
            .findActiveUser(userId)
            .orElseThrow(() -> forbidden("The assigned " + role + " is not an active user"));
    if (engineer.organizationId() == null || !engineer.organizationId().equals(organizationId)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "TEAM_MEMBER_OUT_OF_SCOPE",
          "Team members must be active users of the same organization");
    }
    if (!workforceAccess.currentlyQualifies(organizationId, userId)) {
      throw forbidden("The assigned " + role + " holds a credential that is not currently valid");
    }
  }

  private void requireTeamLead(MaintenanceWorkOrder order, UUID actorId) {
    if (!actorId.equals(order.getTeamLeadUserId())) {
      throw forbidden("Only the designated team lead may perform this action");
    }
  }

  private void requirePreparer(MaintenanceEstimateVersion version, UUID actorId) {
    if (!actorId.equals(version.getPreparedByUserId())) {
      throw forbidden("Only the engineer who prepared this estimate may submit it");
    }
  }

  private MaintenanceEstimateVersion requireEstimate(MaintenanceWorkOrder order, int versionNo) {
    return estimates
        .findByWorkOrderIdAndVersionNo(order.getId(), versionNo)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "ESTIMATE_VERSION_NOT_FOUND",
                    "Estimate version not found"));
  }

  /**
   * Organization-scoped read. A work order in another tenant is reported as not found, never as
   * forbidden, so existence cannot be probed across tenants.
   */
  MaintenanceWorkOrder requireWorkOrderInScope(UUID actorId, UUID workOrderId) {
    UserAccess.ActiveUser actor = requireActiveUser(actorId);
    if (actor.organizationId() == null) {
      throw notFound();
    }
    return workOrders
        .findByIdAndOrganizationId(workOrderId, actor.organizationId())
        .orElseThrow(WorkOrderService::notFound);
  }

  private UserAccess.ActiveUser requireActiveUser(UUID actorId) {
    return userAccess.findActiveUser(actorId).orElseThrow(() -> forbidden("User is not active"));
  }

  private UserAccess.ActiveUser requireOrganizationAdmin(UUID actorId) {
    UserAccess.ActiveUser actor = requireActiveUser(actorId);
    if (!actor.hasRole("ORG_ADMIN") || actor.organizationId() == null) {
      throw forbidden("Only an ORG_ADMIN of this organization may perform this action");
    }
    return actor;
  }

  private static BusinessException notFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "WORK_ORDER_NOT_FOUND", "Work order not found");
  }

  private static BusinessException forbidden(String message) {
    return new BusinessException(HttpStatus.FORBIDDEN, "WORK_ORDER_SCOPE_DENIED", message);
  }

  private static BusinessException conflict(RuntimeException cause) {
    return new BusinessException(
        HttpStatus.CONFLICT, "WORK_ORDER_STATE_CONFLICT", cause.getMessage());
  }
}

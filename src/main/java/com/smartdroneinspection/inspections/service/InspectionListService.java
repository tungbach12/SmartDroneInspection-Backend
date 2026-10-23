package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.response.InspectionListItemResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionPageResponse;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionPreparation;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.enums.InspectionPreparationStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.ReadinessDecisionType;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionPreparationRepository;
import com.smartdroneinspection.inspections.repository.InspectionReadinessDecisionRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inspection list is the entry point to MF3, so it answers two questions for every row: what
 * may the caller see, and in which tenant.
 *
 * <p>Scope is decided from the caller, never from a query parameter. An Inspector sees only the
 * inspections assigned to them, an ORG_ADMIN sees their own organization, and a platform ADMIN sees
 * every organization read-only. Cross-tenant administrative reads stay a separate, explicitly
 * authorized capability, so ADMIN is given no write path on this resource.
 */
@Service
public class InspectionListService {

  private static final Logger log = LoggerFactory.getLogger(InspectionListService.class);

  private static final int MAX_PAGE_SIZE = 100;

  /** createdAt is not unique, so id breaks ties and stops a row appearing on two pages. */
  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

  private final InspectionRepository inspections;
  private final InspectionReportRepository reports;
  private final InspectionPreparationRepository preparations;
  private final InspectionReadinessDecisionRepository readinessDecisions;
  private final FindingRepository findings;
  private final UserAccess userAccess;

  public InspectionListService(
      InspectionRepository inspections,
      InspectionReportRepository reports,
      InspectionPreparationRepository preparations,
      InspectionReadinessDecisionRepository readinessDecisions,
      FindingRepository findings,
      UserAccess userAccess) {
    this.inspections = inspections;
    this.reports = reports;
    this.preparations = preparations;
    this.readinessDecisions = readinessDecisions;
    this.findings = findings;
    this.userAccess = userAccess;
  }

  @Transactional(readOnly = true)
  public InspectionPageResponse list(UUID actorId, int page, int pageSize) {
    Scope scope = requireScope(actorId);
    Page<Inspection> result = scope.query(pageRequest(page, pageSize));
    logCrossTenantRead(scope, actorId, result, "inspection");
    return toPage(result, page, pageSize);
  }

  /**
   * The report review list is the same rows restricted to inspections that carry a report, so a
   * reviewer never pages through field work that cannot be reviewed.
   */
  @Transactional(readOnly = true)
  public InspectionPageResponse listWithReports(UUID actorId, int page, int pageSize) {
    Scope scope = requireScope(actorId);
    Page<Inspection> result = scope.queryWithReport(pageRequest(page, pageSize));
    logCrossTenantRead(scope, actorId, result, "report");
    return toPage(result, page, pageSize);
  }

  private InspectionPageResponse toPage(Page<Inspection> result, int page, int pageSize) {
    int safePage = Math.max(page, 1);
    int safePageSize = clampPageSize(pageSize);
    Map<UUID, InspectionReport> reportByInspection = reportsFor(result.getContent());
    return new InspectionPageResponse(
        result.getContent().stream()
            .map(
                inspection ->
                    toItem(
                        inspection,
                        reportByInspection.get(inspection.getId()),
                        pendingAction(inspection, reportByInspection.get(inspection.getId()))))
            .toList(),
        safePage,
        safePageSize,
        result.getTotalElements(),
        result.getTotalPages());
  }

  /**
   * The one decision the organization administrator is being asked for on this inspection.
   *
   * <p>MF3-09/10 keeps the reviewer's decision at report level: the administrator does not accept
   * each evidence item or each finding. So the queue names a phase rather than a per-item task, and
   * the phases are strictly ordered - readiness, then the report, then the post-publication
   * corrective disposition (MF3-12).
   *
   * <p>Each phase is decided by what the server already records, never by the inspection status
   * alone: {@code PREPARING} covers an open draft and a submitted preparation, and only the second
   * one is waiting for a reviewer.
   */
  private String pendingAction(Inspection inspection, InspectionReport report) {
    if (readinessReviewPending(inspection)) {
      return "READINESS_REVIEW";
    }
    if (report != null) {
      String reportStatus = report.getStatus();
      if (ReportStatus.SUBMITTED.name().equals(reportStatus)) {
        return "REPORT_REVIEW";
      }
      if (ReportStatus.PUBLISHED.name().equals(reportStatus)
          && correctivePending(inspection.getId())) {
        return "CORRECTIVE_DISPOSITION";
      }
    }
    return "NONE";
  }

  /**
   * A submitted preparation is waiting for a readiness decision.
   *
   * <p>A returned preparation is not: it is with the Inspector again, who reopens the draft and
   * submits a new version. The reviewer is asked only for what is actually in front of them.
   */
  private boolean readinessReviewPending(Inspection inspection) {
    if (inspection.getStatus() != InspectionStatus.PREPARING) {
      return false;
    }
    InspectionPreparation latest =
        preparations
            .findFirstByInspectionIdOrderByPreparationVersionDesc(inspection.getId())
            .orElse(null);
    if (latest == null || latest.getStatus() != InspectionPreparationStatus.SUBMITTED) {
      return false;
    }
    // A readiness decision already taken on this very version is not pending work any more.
    return readinessDecisions
        .findFirstByInspectionIdOrderByDecidedAtDesc(inspection.getId())
        .filter(decision -> decision.getPreparationId() != null)
        .filter(decision -> decision.getPreparationId().equals(latest.getId()))
        .filter(decision -> decision.getDecision() != ReadinessDecisionType.INVALIDATED)
        .isEmpty();
  }

  /**
   * A published report still needs every official finding forwarded to Maintenance (MF3-13).
   *
   * <p>An inspection without official findings is completed when its report is published.
   */
  private boolean correctivePending(UUID inspectionId) {
    List<Finding> official =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .filter(Finding::isOfficial)
            .toList();
    if (official.isEmpty()) {
      return false;
    }
    return official.stream().anyMatch(finding -> !finding.isForwardedToMaintenance());
  }

  /**
   * Report rows for one page of inspections only. Reading every report to decorate one page would
   * grow with the whole table instead of the page.
   */
  private Map<UUID, InspectionReport> reportsFor(List<Inspection> rows) {
    if (rows.isEmpty()) {
      return Map.of();
    }
    Map<UUID, InspectionReport> byInspection = new HashMap<>();
    reports
        .findByInspectionIdIn(rows.stream().map(Inspection::getId).toList())
        .forEach(report -> byInspection.put(report.getInspectionId(), report));
    return byInspection;
  }

  /**
   * A platform administrator reading across tenants is an authorized administrative read, not a
   * tenant-bound one, so it is recorded in the application log rather than treated as a violation.
   */
  private void logCrossTenantRead(Scope scope, UUID actorId, Page<Inspection> result, String view) {
    if (scope.platformAdmin()) {
      log.info(
          "Cross-tenant {} list read by platform administrator actorId={} rows={} total={}",
          view,
          actorId,
          result.getNumberOfElements(),
          result.getTotalElements());
    }
  }

  private static InspectionListItemResponse toItem(
      Inspection inspection, InspectionReport report, String pendingAction) {
    return new InspectionListItemResponse(
        inspection.getId(),
        inspection.getOrganizationId(),
        inspection.getAssetId(),
        inspection.getInspectorId(),
        inspection.getObjective(),
        inspection.getStatus().name(),
        inspection.getPlannedStartAt(),
        inspection.getPlannedEndAt(),
        inspection.getCreatedAt(),
        inspection.getUpdatedAt(),
        report == null ? null : report.getId(),
        report == null ? null : report.getStatus(),
        report == null ? null : report.getCurrentVersionNumber(),
        pendingAction);
  }

  private static PageRequest pageRequest(int page, int pageSize) {
    return PageRequest.of(Math.max(page, 1) - 1, clampPageSize(pageSize), NEWEST_FIRST);
  }

  private static int clampPageSize(int pageSize) {
    return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
  }

  private Scope requireScope(UUID actorId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));

    // Platform authority is checked before organization scope because a platform user has no
    // organization by rule; ordering it the other way would deny the very role meant to oversee.
    if (actor.hasRole("ADMIN")) {
      return Scope.platformWide(inspections);
    }
    if (actor.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "No organization scope for the inspection list");
    }
    if (actor.hasRole("ORG_ADMIN")) {
      return Scope.forOrganization(inspections, actor.organizationId());
    }
    if (actor.hasRole("INSPECTOR")) {
      return Scope.forAssignedInspector(inspections, actor.organizationId(), actorId);
    }
    throw new BusinessException(
        HttpStatus.FORBIDDEN, "FORBIDDEN", "This role cannot browse inspections");
  }

  /** The resolved read scope for one caller. */
  private record Scope(
      InspectionRepository inspections,
      UUID organizationId,
      UUID inspectorId,
      boolean platformAdmin) {

    static Scope platformWide(InspectionRepository inspections) {
      return new Scope(inspections, null, null, true);
    }

    static Scope forOrganization(InspectionRepository inspections, UUID organizationId) {
      return new Scope(inspections, organizationId, null, false);
    }

    static Scope forAssignedInspector(
        InspectionRepository inspections, UUID organizationId, UUID inspectorId) {
      return new Scope(inspections, organizationId, inspectorId, false);
    }

    Page<Inspection> query(Pageable pageRequest) {
      if (platformAdmin) {
        return inspections.findAll(pageRequest);
      }
      if (inspectorId != null) {
        return inspections.findByOrganizationIdAndInspectorId(
            organizationId, inspectorId, pageRequest);
      }
      return inspections.findByOrganizationId(organizationId, pageRequest);
    }

    Page<Inspection> queryWithReport(Pageable pageRequest) {
      if (platformAdmin) {
        return inspections.findWithReport(pageRequest);
      }
      if (inspectorId != null) {
        return inspections.findWithReportByOrganizationIdAndInspectorId(
            organizationId, inspectorId, pageRequest);
      }
      return inspections.findWithReportByOrganizationId(organizationId, pageRequest);
    }
  }
}

package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.response.InspectionListItemResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionPageResponse;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
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
 * inspections assigned to them, an ORG_MANAGER sees their own organization, and a platform ADMIN
 * sees every organization read-only. Cross-tenant administrative reads stay a separate, explicitly
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
  private final FindingRepository findings;
  private final UserAccess userAccess;

  public InspectionListService(
      InspectionRepository inspections,
      InspectionReportRepository reports,
      FindingRepository findings,
      UserAccess userAccess) {
    this.inspections = inspections;
    this.reports = reports;
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

  /** The one decision the organization administrator is being asked for on this inspection. */
  private String pendingAction(Inspection inspection, InspectionReport report) {
    if (report != null) {
      ReportStatus reportStatus = report.getStatus();
      if (reportStatus == ReportStatus.SUBMITTED) {
        return "REPORT_REVIEW";
      }
      if (reportStatus == ReportStatus.PUBLISHED && correctivePending(inspection.getId())) {
        return "CORRECTIVE_DISPOSITION";
      }
    }
    return "NONE";
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
   * turn a bounded query into an N-plus-1 problem; this finds the latest version for each id.
   */
  private Map<UUID, InspectionReport> reportsFor(List<Inspection> pageRows) {
    if (pageRows.isEmpty()) {
      return Map.of();
    }
    List<UUID> inspectionIds = pageRows.stream().map(Inspection::getId).toList();
    List<InspectionReport> allVersions =
        reports.findByInspectionIdInOrderByVersionNoDesc(inspectionIds);
    Map<UUID, InspectionReport> latest = new HashMap<>();
    for (InspectionReport version : allVersions) {
      latest.merge(
          version.getInspectionId(),
          version,
          (existing, incoming) ->
              incoming.getVersionNo() > existing.getVersionNo() ? incoming : existing);
    }
    return latest;
  }

  private InspectionListItemResponse toItem(
      Inspection inspection, InspectionReport report, String pendingAction) {
    return new InspectionListItemResponse(
        inspection.getId(),
        inspection.getOrganizationId(),
        inspection.getAssetId(),
        inspection.getInspectorId(),
        inspection.getStatus().name(),
        inspection.getPlannedStartAt(),
        inspection.getPlannedEndAt(),
        inspection.getCreatedAt(),
        inspection.getUpdatedAt(),
        report == null ? null : report.getId(),
        report == null || report.getStatus() == null ? null : report.getStatus().name(),
        report == null ? null : report.getVersionNo(),
        pendingAction,
        inspection.getInspectionType() != null ? inspection.getInspectionType().name() : "INITIAL",
        inspection.getWorkOrderId());
  }

  private Scope requireScope(UUID actorId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        "ACCOUNT_INACTIVE",
                        "Authentication is required."));

    if (actor.hasRole(Roles.ADMIN) || actor.hasRole(Roles.MANAGER)) {
      return new Scope.PlatformAdmin(inspections);
    }
    if (actor.isInspectorPartner()) {
      return new Scope.InspectorPartner(actor.id(), inspections);
    }
    UUID organizationId = actor.organizationId();
    if (organizationId == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "The user does not belong to an organization.");
    }
    if (actor.hasRole("CUSTOMER")) {
      return new Scope.OrganizationManager(organizationId, inspections);
    }
    if (actor.hasRole("INSPECTOR")) {
      return new Scope.Inspector(organizationId, actor.id(), inspections);
    }
    throw new BusinessException(
        HttpStatus.FORBIDDEN, "FORBIDDEN", "The user has no permission to view inspections.");
  }

  private static Pageable pageRequest(int page, int pageSize) {
    int safePage = Math.max(page, 1);
    return PageRequest.of(safePage - 1, clampPageSize(pageSize), NEWEST_FIRST);
  }

  private static int clampPageSize(int pageSize) {
    return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
  }

  private void logCrossTenantRead(
      Scope scope, UUID actorId, Page<Inspection> result, String subject) {
    if (scope instanceof Scope.PlatformAdmin) {
      log.info(
          "Platform admin {} read page {} of cross-tenant {} list ({} rows)",
          actorId,
          result.getNumber() + 1,
          subject,
          result.getNumberOfElements());
    }
  }

  private sealed interface Scope {

    Page<Inspection> query(Pageable pageable);

    Page<Inspection> queryWithReport(Pageable pageable);

    record PlatformAdmin(InspectionRepository repository) implements Scope {
      @Override
      public Page<Inspection> query(Pageable pageable) {
        return repository.findAll(pageable);
      }

      @Override
      public Page<Inspection> queryWithReport(Pageable pageable) {
        return repository.findWithReport(pageable);
      }
    }

    record OrganizationManager(UUID organizationId, InspectionRepository repository)
        implements Scope {
      @Override
      public Page<Inspection> query(Pageable pageable) {
        return repository.findByOrganizationId(organizationId, pageable);
      }

      @Override
      public Page<Inspection> queryWithReport(Pageable pageable) {
        return repository.findWithReportByOrganizationId(organizationId, pageable);
      }
    }

    record Inspector(UUID organizationId, UUID inspectorId, InspectionRepository repository)
        implements Scope {
      @Override
      public Page<Inspection> query(Pageable pageable) {
        return repository.findByOrganizationIdAndInspectorId(organizationId, inspectorId, pageable);
      }

      @Override
      public Page<Inspection> queryWithReport(Pageable pageable) {
        return repository.findWithReportByOrganizationIdAndInspectorId(
            organizationId, inspectorId, pageable);
      }
    }

    record InspectorPartner(UUID inspectorId, InspectionRepository repository) implements Scope {
      @Override
      public Page<Inspection> query(Pageable pageable) {
        return repository.findByInspectorIdAndPartnerAssignmentTrue(inspectorId, pageable);
      }

      @Override
      public Page<Inspection> queryWithReport(Pageable pageable) {
        return repository.findWithReportByInspectorIdAndPartnerAssignmentTrue(
            inspectorId, pageable);
      }
    }
  }
}

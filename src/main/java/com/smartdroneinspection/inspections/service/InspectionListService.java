package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.api.dto.response.InspectionListItemResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionPageResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.api.InspectionReportAcceptancePort;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inspection list is the entry point to MF3, so it answers two questions for every row: what
 * may the caller see, and for which customer/inspector scope.
 *
 * <p>Scope is decided from the caller, never from a query parameter. An INSPECTOR sees only the
 * inspections assigned to them, a CUSTOMER sees their own inspections, and ADMIN/MANAGER see across
 * customers. ADMIN has read-only cross-customer visibility with no write path here.
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
  private final UserAccess userAccess;
  private final AssetRepository assets;
  private final DroneRepository drones;
  private final Optional<InspectionReportAcceptancePort> reportAcceptancePort;

  public InspectionListService(
      InspectionRepository inspections, InspectionReportRepository reports, UserAccess userAccess) {
    this(inspections, reports, userAccess, null, Optional.empty(), null);
  }

  public InspectionListService(
      InspectionRepository inspections,
      InspectionReportRepository reports,
      UserAccess userAccess,
      AssetRepository assets,
      Optional<InspectionReportAcceptancePort> reportAcceptancePort) {
    this(inspections, reports, userAccess, assets, reportAcceptancePort, null);
  }

  @Autowired
  public InspectionListService(
      InspectionRepository inspections,
      InspectionReportRepository reports,
      UserAccess userAccess,
      AssetRepository assets,
      Optional<InspectionReportAcceptancePort> reportAcceptancePort,
      DroneRepository drones) {
    this.inspections = inspections;
    this.reports = reports;
    this.userAccess = userAccess;
    this.assets = assets;
    this.drones = drones;
    this.reportAcceptancePort =
        reportAcceptancePort != null ? reportAcceptancePort : Optional.empty();
  }

  @Transactional(readOnly = true)
  public InspectionPageResponse list(UUID actorId, int page, int pageSize) {
    Scope scope = requireScope(actorId);
    Page<Inspection> result = scope.query(pageRequest(page, pageSize));
    logCrossTenantRead(scope, actorId, result, "inspection");
    return toPage(result, page, pageSize, scope);
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
    return toPage(result, page, pageSize, scope);
  }

  private InspectionPageResponse toPage(
      Page<Inspection> result, int page, int pageSize, Scope scope) {
    int safePage = Math.max(page, 1);
    int safePageSize = clampPageSize(pageSize);
    List<Inspection> content = result.getContent();
    Map<UUID, InspectionReport> reportByInspection = reportsFor(content);
    Map<UUID, Asset> assetById = assetsFor(content);
    Map<UUID, Drone> droneById = dronesFor(content);
    Map<UUID, InspectionReportAcceptancePort.PaymentStatusDetails> paymentByInspection =
        paymentsFor(content);
    Map<UUID, String> userNames = new HashMap<>();
    String defaultManagerName =
        content.isEmpty()
            ? null
            : userAccess.listActiveManagers().stream()
                .findFirst()
                .map(UserAccess.ActiveUser::fullName)
                .orElse(null);

    return new InspectionPageResponse(
        content.stream()
            .map(
                inspection ->
                    toItem(
                        inspection,
                        reportByInspection.get(inspection.getId()),
                        pendingAction(inspection, reportByInspection.get(inspection.getId())),
                        assetById.get(inspection.getAssetId()),
                        inspection.getDroneId() == null
                            ? null
                            : droneById.get(inspection.getDroneId()),
                        paymentByInspection.get(inspection.getId()),
                        resolveUserName(userNames, inspection.getOwnerCustomerId()),
                        resolveUserName(userNames, inspection.getInspectorId()),
                        defaultManagerName))
            .toList(),
        safePage,
        safePageSize,
        result.getTotalElements(),
        result.getTotalPages());
  }

  private String resolveUserName(Map<UUID, String> cache, UUID userId) {
    if (userId == null) {
      return null;
    }
    return cache.computeIfAbsent(
        userId, id -> userAccess.findUser(id).map(UserAccess.ActiveUser::fullName).orElse(null));
  }

  private Map<UUID, Drone> dronesFor(List<Inspection> pageRows) {
    if (pageRows.isEmpty() || drones == null) {
      return Map.of();
    }
    List<UUID> droneIds =
        pageRows.stream().map(Inspection::getDroneId).filter(Objects::nonNull).distinct().toList();
    if (droneIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, Drone> byId = new HashMap<>();
    for (Drone drone : drones.findAllById(droneIds)) {
      byId.put(drone.getId(), drone);
    }
    return byId;
  }

  private Map<UUID, Asset> assetsFor(List<Inspection> pageRows) {
    if (pageRows.isEmpty() || assets == null) {
      return Map.of();
    }
    List<UUID> assetIds =
        pageRows.stream().map(Inspection::getAssetId).filter(Objects::nonNull).distinct().toList();
    if (assetIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, Asset> byId = new HashMap<>();
    for (Asset asset : assets.findAllById(assetIds)) {
      byId.put(asset.getId(), asset);
    }
    return byId;
  }

  private Map<UUID, InspectionReportAcceptancePort.PaymentStatusDetails> paymentsFor(
      List<Inspection> pageRows) {
    if (pageRows.isEmpty() || reportAcceptancePort.isEmpty()) {
      return Map.of();
    }
    List<UUID> inspectionIds =
        pageRows.stream().map(Inspection::getId).filter(Objects::nonNull).distinct().toList();
    return reportAcceptancePort.get().getPaymentStatuses(inspectionIds);
  }

  /** The one decision the organization administrator is being asked for on this inspection. */
  private String pendingAction(Inspection inspection, InspectionReport report) {
    if (report != null) {
      ReportStatus reportStatus = report.getStatus();
      if (reportStatus == ReportStatus.SUBMITTED) {
        return "REPORT_REVIEW";
      }
    }
    return "NONE";
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
      Inspection inspection,
      InspectionReport report,
      String pendingAction,
      Asset asset,
      Drone drone,
      InspectionReportAcceptancePort.PaymentStatusDetails payment,
      String ownerCustomerName,
      String inspectorName,
      String managerName) {
    String objective =
        payment != null && payment.requestDescription() != null
            ? payment.requestDescription()
            : null;
    return new InspectionListItemResponse(
        inspection.getId(),
        inspection.getOwnerCustomerId(),
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
        objective,
        asset == null ? null : asset.getName(),
        asset == null ? null : asset.getCode(),
        asset == null ? null : asset.getLocationText(),
        payment == null ? null : payment.reportAccepted(),
        payment == null ? null : payment.paymentStatus(),
        payment == null ? null : payment.amount(),
        payment == null ? null : payment.commissionRate(),
        payment == null ? null : payment.commissionAmount(),
        payment == null ? null : payment.inspectorNetPayout(),
        payment == null ? null : payment.payoutStatus(),
        payment == null ? null : payment.paymentMethod(),
        inspection.isReassignmentRequired(),
        inspection.getOverdueNotifiedAt(),
        inspection.getAssignmentStatus() != null
            ? inspection.getAssignmentStatus().name()
            : "ACCEPTED",
        inspection.getAssignmentAcceptedAt(),
        inspection.getFlightPermitVerificationStatus().name(),
        inspection.getSourceInspectionId(),
        ownerCustomerName,
        inspectorName,
        managerName,
        drone == null ? null : drone.getModel(),
        drone == null ? null : drone.getSerialNumber());
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
    UUID ownerCustomerId = actor.ownerCustomerId();
    if (actor.hasRole("CUSTOMER")) {
      if (ownerCustomerId == null) {
        throw new BusinessException(
            HttpStatus.FORBIDDEN, "FORBIDDEN", "The user has no customer ownership scope.");
      }
      return new Scope.CustomerScope(ownerCustomerId, inspections);
    }
    if (actor.hasRole("INSPECTOR")) {
      return new Scope.Inspector(ownerCustomerId, actor.id(), inspections);
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

    record CustomerScope(UUID ownerCustomerId, InspectionRepository repository) implements Scope {
      @Override
      public Page<Inspection> query(Pageable pageable) {
        return repository.findByOwnerCustomerId(ownerCustomerId, pageable);
      }

      @Override
      public Page<Inspection> queryWithReport(Pageable pageable) {
        return repository.findWithReportByOwnerCustomerId(ownerCustomerId, pageable);
      }
    }

    record Inspector(UUID ownerCustomerId, UUID inspectorId, InspectionRepository repository)
        implements Scope {
      @Override
      public Page<Inspection> query(Pageable pageable) {
        return repository.findByInspectorId(inspectorId, pageable);
      }

      @Override
      public Page<Inspection> queryWithReport(Pageable pageable) {
        return repository.findWithReportByInspectorId(inspectorId, pageable);
      }
    }
  }
}

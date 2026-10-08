package com.smartdroneinspection.maintenance.service;

import com.smartdroneinspection.inspections.InspectionAccess;
import com.smartdroneinspection.maintenance.api.dto.request.AssignMaintenanceEngineerRequest;
import com.smartdroneinspection.maintenance.api.dto.request.SubmitWorkLogRequest;
import com.smartdroneinspection.maintenance.api.dto.response.MaintenanceWorkLogResponse;
import com.smartdroneinspection.maintenance.domain.MaintenanceAssignment;
import com.smartdroneinspection.maintenance.domain.MaintenanceOrder;
import com.smartdroneinspection.maintenance.domain.MaintenanceTicket;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkLog;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceAssignmentStatus;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceOrderStatus;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceTicketStatus;
import com.smartdroneinspection.maintenance.domain.enums.WorkLogStatus;
import com.smartdroneinspection.maintenance.repository.MaintenanceAssignmentRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceOrderRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTicketRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceWorkLogRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MaintenanceExecutionService {

  private final MaintenanceAssignmentRepository assignments;
  private final MaintenanceWorkLogRepository workLogs;
  private final MaintenanceOrderRepository orders;
  private final MaintenanceTicketRepository tickets;
  private final InspectionAccess inspectionAccess;
  private final Optional<EvidenceObjectStore> objectStore;
  private final UserAccess users;

  public MaintenanceExecutionService(
      MaintenanceAssignmentRepository assignments,
      MaintenanceWorkLogRepository workLogs,
      MaintenanceOrderRepository orders,
      MaintenanceTicketRepository tickets,
      InspectionAccess inspectionAccess,
      Optional<EvidenceObjectStore> objectStore,
      UserAccess users) {
    this.assignments = assignments;
    this.workLogs = workLogs;
    this.orders = orders;
    this.tickets = tickets;
    this.inspectionAccess = inspectionAccess;
    this.objectStore = objectStore;
    this.users = users;
  }

  @Transactional
  public UUID assignEngineer(UUID userId, UUID orderId, AssignMaintenanceEngineerRequest request) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    if (!user.hasRole(Roles.PROVIDER_MANAGER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "MAINTENANCE_SCOPE_DENIED",
          "Only provider managers can assign maintenance engineers.");
    }

    MaintenanceOrder order =
        orders
            .findById(orderId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "MAINTENANCE_ORDER_NOT_FOUND",
                        "Maintenance order was not found."));

    UserAccess.ActiveUser engineer =
        users
            .findActiveUser(request.engineerUserId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "ENGINEER_NOT_FOUND",
                        "Maintenance engineer was not found."));

    if (!engineer.hasRole(Roles.MAINTENANCE_ENGINEER)) {
      throw new BusinessException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "INVALID_ROLE",
          "Assigned user does not hold the MAINTENANCE_ENGINEER role.");
    }

    MaintenanceTicket ticket =
        tickets
            .findById(order.getMaintenanceTicketId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "MAINTENANCE_TICKET_NOT_FOUND",
                        "Maintenance ticket was not found."));

    MaintenanceAssignment assignment =
        new MaintenanceAssignment(
            ticket.getId(),
            order.getId(),
            request.engineerUserId(),
            userId,
            request.assignmentType(),
            request.deadline());

    MaintenanceAssignment saved = assignments.save(assignment);

    if (order.getStatus() == MaintenanceOrderStatus.CONFIRMED) {
      order.markInProgress();
    }
    ticket.transitionTo(MaintenanceTicketStatus.IN_PROGRESS);

    return saved.getId();
  }

  @Transactional(readOnly = true)
  public List<MaintenanceAssignment> listMyAssignments(UUID userId) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    if (!user.hasRole(Roles.MAINTENANCE_ENGINEER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "MAINTENANCE_SCOPE_DENIED",
          "Only maintenance engineers can view their task inbox.");
    }
    return assignments.findByEngineerUserIdAndStatusOrderByDeadlineAsc(
        userId, MaintenanceAssignmentStatus.PENDING);
  }

  @Transactional
  public UUID uploadMaintenanceEvidence(
      UUID userId, UUID orderId, String kind, MultipartFile file) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    if (!user.hasRole(Roles.MAINTENANCE_ENGINEER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "MAINTENANCE_SCOPE_DENIED",
          "Only maintenance engineers can upload maintenance evidence.");
    }

    if (!"BEFORE_MAINTENANCE".equals(kind) && !"AFTER_MAINTENANCE".equals(kind)) {
      throw new BusinessException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "INVALID_EVIDENCE_KIND",
          "Evidence kind must be BEFORE_MAINTENANCE or AFTER_MAINTENANCE.");
    }

    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "UPLOAD_FAILED", "Could not read uploaded file.");
    }

    String checksum = sha256(bytes);
    String objectKey =
        "maintenance/" + orderId + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();

    if (objectStore.isPresent()) {
      try (InputStream in = new ByteArrayInputStream(bytes)) {
        objectStore.get().put(objectKey, file.getContentType(), bytes.length, in);
      } catch (IOException e) {
        throw new BusinessException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "STORAGE_FAILED",
            "Could not persist evidence to object store.");
      }
    }

    return inspectionAccess.createMaintenanceEvidence(
        userId,
        kind,
        file.getOriginalFilename(),
        file.getContentType(),
        bytes.length,
        checksum,
        objectKey);
  }

  @Transactional
  public UUID submitWorkLog(UUID userId, UUID orderId, SubmitWorkLogRequest request) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    if (!user.hasRole(Roles.MAINTENANCE_ENGINEER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "MAINTENANCE_SCOPE_DENIED",
          "Only maintenance engineers can submit work logs.");
    }

    MaintenanceAssignment assignment =
        assignments
            .findById(request.executionAssignmentId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "ASSIGNMENT_NOT_FOUND",
                        "Maintenance assignment was not found."));

    if (!assignment.getEngineerUserId().equals(userId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "MAINTENANCE_SCOPE_DENIED",
          "You are not assigned to this maintenance order.");
    }

    if (request.beforeEvidenceId() == null || request.afterEvidenceId() == null) {
      throw new BusinessException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "EVIDENCE_PAIR_INVALID",
          "Both before and after evidence are strictly required.");
    }

    if (request.beforeEvidenceId().equals(request.afterEvidenceId())) {
      throw new BusinessException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "EVIDENCE_PAIR_INVALID",
          "Before and after evidence must be distinct.");
    }

    if (!inspectionAccess.existsEvidence(request.beforeEvidenceId())) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "Before evidence was not found.");
    }

    if (!inspectionAccess.existsEvidence(request.afterEvidenceId())) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "After evidence was not found.");
    }

    MaintenanceWorkLog log =
        new MaintenanceWorkLog(
            assignment.getMaintenanceTicketId(),
            assignment.getId(),
            userId,
            request.startedAt(),
            request.progressPercent(),
            request.workSummary(),
            request.materialsUsed(),
            request.laborHours(),
            WorkLogStatus.SUBMITTED);

    log.setEvidencePair(request.beforeEvidenceId(), request.afterEvidenceId());
    log.submit(Instant.now());

    MaintenanceWorkLog savedLog = workLogs.save(log);

    inspectionAccess.linkEvidenceToWorkLog(request.beforeEvidenceId(), savedLog.getId());
    inspectionAccess.linkEvidenceToWorkLog(request.afterEvidenceId(), savedLog.getId());

    assignment.complete();

    return savedLog.getId();
  }

  @Transactional
  public void verifyWorkLog(UUID userId, UUID workLogId) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    if (!user.hasRole(Roles.PROVIDER_MANAGER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "MAINTENANCE_SCOPE_DENIED",
          "Only provider managers can verify work logs.");
    }

    MaintenanceWorkLog log =
        workLogs
            .findById(workLogId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "WORK_LOG_NOT_FOUND", "Work log was not found."));

    log.verify(userId, Instant.now());

    tickets
        .findById(log.getMaintenanceTicketId())
        .ifPresent(ticket -> ticket.transitionTo(MaintenanceTicketStatus.RELEASED));
  }

  @Transactional(readOnly = true)
  public List<MaintenanceWorkLogResponse> listWorkLogsForTicket(UUID userId, UUID ticketId) {
    requireActiveUser(userId);
    return workLogs.findByMaintenanceTicketIdOrderByStartedAtDesc(ticketId).stream()
        .map(this::toResponse)
        .toList();
  }

  private MaintenanceWorkLogResponse toResponse(MaintenanceWorkLog l) {
    return new MaintenanceWorkLogResponse(
        l.getId(),
        l.getMaintenanceTicketId(),
        l.getExecutionAssignmentId(),
        l.getEngineerUserId(),
        l.getStartedAt(),
        l.getEndedAt(),
        l.getProgressPercent(),
        l.getWorkSummary(),
        l.getMaterialsUsed(),
        l.getLaborHours(),
        l.getActualCost(),
        l.getCurrency(),
        l.getStatus(),
        l.getBeforeEvidenceId(),
        l.getAfterEvidenceId(),
        l.getSubmittedAt(),
        l.getVerifiedByUserId(),
        l.getVerifiedAt(),
        l.getCreatedAt());
  }

  private String sha256(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private UserAccess.ActiveUser requireActiveUser(UUID userId) {
    return users
        .findActiveUser(userId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "User is not authenticated or not active."));
  }
}

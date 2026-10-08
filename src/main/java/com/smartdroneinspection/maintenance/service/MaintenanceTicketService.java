package com.smartdroneinspection.maintenance.service;

import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.inspections.InspectionAccess;
import com.smartdroneinspection.maintenance.api.dto.request.CreateMaintenanceTicketRequest;
import com.smartdroneinspection.maintenance.api.dto.response.MaintenanceTicketDetailResponse;
import com.smartdroneinspection.maintenance.api.dto.response.MaintenanceTicketSummaryResponse;
import com.smartdroneinspection.maintenance.domain.MaintenanceTicket;
import com.smartdroneinspection.maintenance.domain.MaintenanceTicketFinding;
import com.smartdroneinspection.maintenance.domain.MaintenanceTicketFindingId;
import com.smartdroneinspection.maintenance.repository.MaintenanceTicketFindingRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTicketRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceTicketService {

  private final MaintenanceTicketRepository tickets;
  private final MaintenanceTicketFindingRepository ticketFindings;
  private final AssetRepository assets;
  private final InspectionAccess inspectionAccess;
  private final UserAccess users;

  public MaintenanceTicketService(
      MaintenanceTicketRepository tickets,
      MaintenanceTicketFindingRepository ticketFindings,
      AssetRepository assets,
      InspectionAccess inspectionAccess,
      UserAccess users) {
    this.tickets = tickets;
    this.ticketFindings = ticketFindings;
    this.assets = assets;
    this.inspectionAccess = inspectionAccess;
    this.users = users;
  }

  @Transactional
  public UUID createTicket(UUID userId, CreateMaintenanceTicketRequest request) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    if (!user.hasRole(Roles.CLIENT)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REQUEST_SCOPE_DENIED",
          "Only clients can create maintenance tickets.");
    }
    UUID organizationId = user.organizationId();

    if (request.findingIds() == null || request.findingIds().isEmpty()) {
      throw new BusinessException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "MAINTENANCE_FINDINGS_EMPTY",
          "At least one verified finding is required to create a maintenance ticket.");
    }

    assets
        .findByIdAndOrganizationId(request.assetId(), organizationId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "REQUEST_SCOPE_DENIED",
                    "Asset does not belong to your organization."));

    InspectionAccess.AcceptedReportSnapshot reportSnapshot =
        inspectionAccess
            .findAcceptedReportVersion(request.acceptedReportVersionId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT,
                        "REPORT_NOT_ACCEPTED",
                        "Maintenance tickets can only be created from accepted inspection reports."));

    if (!reportSnapshot.assetId().equals(request.assetId())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "ASSET_MISMATCH",
          "Selected report version does not belong to the specified asset.");
    }

    for (UUID findingId : request.findingIds()) {
      if (!reportSnapshot.containsFinding(findingId)) {
        throw new BusinessException(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "INVALID_FINDING",
            "Finding " + findingId + " does not belong to the accepted report.");
      }
    }

    MaintenanceTicket ticket =
        new MaintenanceTicket(
            organizationId,
            request.assetId(),
            request.acceptedReportVersionId(),
            userId,
            request.priority(),
            request.preferredDeadline(),
            request.instructions());

    MaintenanceTicket savedTicket = tickets.save(ticket);
    UUID ticketId = savedTicket.getId();

    List<MaintenanceTicketFinding> findings =
        request.findingIds().stream()
            .map(
                findingId ->
                    new MaintenanceTicketFinding(
                        new MaintenanceTicketFindingId(ticketId, findingId)))
            .toList();
    ticketFindings.saveAll(findings);

    return ticketId;
  }

  @Transactional(readOnly = true)
  public MaintenanceTicketDetailResponse getTicketDetail(UUID userId, UUID ticketId) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    MaintenanceTicket ticket =
        tickets
            .findById(ticketId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "MAINTENANCE_TICKET_NOT_FOUND",
                        "Maintenance ticket was not found."));

    if (user.hasRole(Roles.CLIENT) && !ticket.getOrganizationId().equals(user.organizationId())) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REQUEST_SCOPE_DENIED",
          "Maintenance ticket does not belong to your organization.");
    }

    List<UUID> findingIds =
        ticketFindings.findAll().stream()
            .filter(tf -> tf.getId().getMaintenanceTicketId().equals(ticketId))
            .map(tf -> tf.getId().getVerifiedFindingId())
            .toList();

    return new MaintenanceTicketDetailResponse(
        ticket.getId(),
        ticket.getOrganizationId(),
        ticket.getAssetId(),
        ticket.getAcceptedReportVersionId(),
        ticket.getCreatedByUserId(),
        ticket.getPriority(),
        ticket.getPreferredDeadline(),
        ticket.getInstructions(),
        ticket.getStatus(),
        ticket.getResolutionDecision(),
        ticket.getAcceptedAt(),
        ticket.getWarrantyStartedAt(),
        ticket.getWarrantyEndsAt(),
        ticket.getReleasedAt(),
        ticket.getClosedAt(),
        ticket.getCreatedAt(),
        ticket.getUpdatedAt(),
        findingIds);
  }

  @Transactional(readOnly = true)
  public Page<MaintenanceTicketSummaryResponse> listTickets(UUID userId, Pageable pageable) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    Page<MaintenanceTicket> page;
    if (user.hasRole(Roles.CLIENT)) {
      page = tickets.findByOrganizationIdOrderByCreatedAtDesc(user.organizationId(), pageable);
    } else {
      page = tickets.findAll(pageable);
    }

    return page.map(
        t ->
            new MaintenanceTicketSummaryResponse(
                t.getId(),
                t.getOrganizationId(),
                t.getAssetId(),
                t.getAcceptedReportVersionId(),
                t.getPriority(),
                t.getStatus(),
                t.getResolutionDecision(),
                t.getPreferredDeadline(),
                t.getCreatedAt(),
                t.getAcceptedAt(),
                t.getClosedAt()));
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

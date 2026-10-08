package com.smartdroneinspection.maintenance.service;

import com.smartdroneinspection.maintenance.api.dto.request.CreateMaintenanceAssessmentQuotationRequest;
import com.smartdroneinspection.maintenance.api.dto.response.MaintenanceQuotationResponse;
import com.smartdroneinspection.maintenance.domain.MaintenanceAssessment;
import com.smartdroneinspection.maintenance.domain.MaintenanceQuotation;
import com.smartdroneinspection.maintenance.domain.MaintenanceTicket;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceQuotationStatus;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceTicketStatus;
import com.smartdroneinspection.maintenance.repository.MaintenanceAssessmentRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceQuotationRepository;
import com.smartdroneinspection.maintenance.repository.MaintenanceTicketRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceQuotationService {

  private final MaintenanceAssessmentRepository assessments;
  private final MaintenanceQuotationRepository quotations;
  private final MaintenanceTicketRepository tickets;
  private final UserAccess users;

  public MaintenanceQuotationService(
      MaintenanceAssessmentRepository assessments,
      MaintenanceQuotationRepository quotations,
      MaintenanceTicketRepository tickets,
      UserAccess users) {
    this.assessments = assessments;
    this.quotations = quotations;
    this.tickets = tickets;
    this.users = users;
  }

  @Transactional
  public UUID createAssessmentAndQuotation(
      UUID userId, UUID ticketId, CreateMaintenanceAssessmentQuotationRequest request) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    if (!user.hasRole(Roles.PROVIDER_MANAGER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "MAINTENANCE_SCOPE_DENIED",
          "Only provider managers can prepare assessments and quotations.");
    }

    MaintenanceTicket ticket =
        tickets
            .findById(ticketId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "MAINTENANCE_TICKET_NOT_FOUND",
                        "Maintenance ticket was not found."));

    UUID assessmentAssignmentId = UUID.randomUUID();
    MaintenanceAssessment assessment =
        new MaintenanceAssessment(
            assessmentAssignmentId,
            ticketId,
            userId,
            request.assessmentMode(),
            request.requiredWork(),
            request.materialsEstimate(),
            request.laborHoursEstimate(),
            request.durationHoursEstimate(),
            request.riskNotes(),
            request.assumptions(),
            request.subtotal(),
            request.totalAmount(),
            "VND");
    MaintenanceAssessment savedAssessment = assessments.save(assessment);

    Optional<MaintenanceQuotation> latestOpt =
        quotations.findFirstByMaintenanceTicketIdOrderByVersionNumberDesc(ticketId);

    UUID seriesId =
        latestOpt.map(MaintenanceQuotation::getQuotationSeriesId).orElseGet(UUID::randomUUID);
    int versionNumber = latestOpt.map(q -> q.getVersionNumber() + 1).orElse(1);
    UUID previousVersionId = latestOpt.map(MaintenanceQuotation::getId).orElse(null);

    latestOpt.ifPresent(MaintenanceQuotation::supersede);

    MaintenanceQuotation quotation =
        new MaintenanceQuotation(
            seriesId,
            ticketId,
            savedAssessment.getId(),
            versionNumber,
            previousVersionId,
            userId,
            "VND",
            request.subtotal(),
            request.taxAmount(),
            request.totalAmount(),
            request.pricingDetails(),
            request.scopeSnapshot(),
            request.durationHoursEstimate(),
            request.paymentTerms());

    quotation.setProviderId(user.organizationId());
    quotation.setLockedWarrantyDays(request.lockedWarrantyDays());
    quotation.send(Instant.now());

    MaintenanceQuotation savedQuotation = quotations.save(quotation);

    ticket.transitionTo(MaintenanceTicketStatus.AWAITING_CLIENT_APPROVAL);

    return savedQuotation.getId();
  }

  @Transactional
  public void approveQuotation(UUID userId, UUID quotationId) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    MaintenanceQuotation quotation =
        quotations
            .findById(quotationId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "QUOTATION_NOT_FOUND",
                        "Maintenance quotation was not found."));

    MaintenanceTicket ticket =
        tickets
            .findById(quotation.getMaintenanceTicketId())
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
          "You can only approve quotations for your organization's tickets.");
    }

    if (quotation.getStatus() == MaintenanceQuotationStatus.SUPERSEDED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "QUOTATION_STALE_VERSION",
          "Cannot decide on a superseded quotation version.");
    }

    if (quotation.getStatus() != MaintenanceQuotationStatus.SENT) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "QUOTATION_STATE_CONFLICT", "Quotation is not in SENT status.");
    }

    quotation.approve(userId, Instant.now());
    ticket.transitionTo(MaintenanceTicketStatus.ORDER_CONFIRMED);
  }

  @Transactional
  public void rejectQuotation(UUID userId, UUID quotationId, String reason) {
    UserAccess.ActiveUser user = requireActiveUser(userId);
    MaintenanceQuotation quotation =
        quotations
            .findById(quotationId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "QUOTATION_NOT_FOUND",
                        "Maintenance quotation was not found."));

    MaintenanceTicket ticket =
        tickets
            .findById(quotation.getMaintenanceTicketId())
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
          "You can only reject quotations for your organization's tickets.");
    }

    if (quotation.getStatus() == MaintenanceQuotationStatus.SUPERSEDED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "QUOTATION_STALE_VERSION",
          "Cannot decide on a superseded quotation version.");
    }

    quotation.reject(userId, reason, Instant.now());
  }

  @Transactional(readOnly = true)
  public List<MaintenanceQuotationResponse> listQuotationsForTicket(UUID userId, UUID ticketId) {
    requireActiveUser(userId);
    return quotations.findByMaintenanceTicketIdOrderByVersionNumberDesc(ticketId).stream()
        .map(this::toResponse)
        .toList();
  }

  private MaintenanceQuotationResponse toResponse(MaintenanceQuotation q) {
    return new MaintenanceQuotationResponse(
        q.getId(),
        q.getQuotationSeriesId(),
        q.getMaintenanceTicketId(),
        q.getMaintenanceAssessmentId(),
        q.getVersionNumber(),
        q.getPreviousVersionId(),
        q.getPreparedByUserId(),
        q.getProviderId(),
        q.getLockedWarrantyDays(),
        q.getCurrency(),
        q.getSubtotal(),
        q.getTaxAmount(),
        q.getTotalAmount(),
        q.getPricingDetails(),
        q.getScopeSnapshot(),
        q.getPaymentTerms(),
        q.getStatus(),
        q.getSentAt(),
        q.getDecidedByUserId(),
        q.getDecidedAt(),
        q.getRevisionReason(),
        q.getCreatedAt());
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

package com.smartdroneinspection.maintenance.api;

import com.smartdroneinspection.maintenance.api.dto.request.CreateMaintenanceAssessmentQuotationRequest;
import com.smartdroneinspection.maintenance.api.dto.response.MaintenanceQuotationResponse;
import com.smartdroneinspection.maintenance.service.MaintenanceQuotationService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maintenance-quotations")
public class MaintenanceQuotationController {

  private final MaintenanceQuotationService quotations;

  public MaintenanceQuotationController(MaintenanceQuotationService quotations) {
    this.quotations = quotations;
  }

  @PostMapping("/tickets/{ticketId}")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('PROVIDER_MANAGER')")
  public ApiResponse<UUID> createQuotation(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID ticketId,
      @Valid @RequestBody CreateMaintenanceAssessmentQuotationRequest request) {
    UUID quotationId = quotations.createAssessmentAndQuotation(subject(jwt), ticketId, request);
    return ApiResponse.success("Maintenance quotation created successfully", quotationId);
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasRole('CLIENT')")
  public ApiResponse<Void> approveQuotation(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    quotations.approveQuotation(subject(jwt), id);
    return ApiResponse.success("Maintenance quotation approved", null);
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasRole('CLIENT')")
  public ApiResponse<Void> rejectQuotation(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam(required = false) String reason) {
    quotations.rejectQuotation(subject(jwt), id, reason);
    return ApiResponse.success("Maintenance quotation rejected", null);
  }

  @GetMapping("/tickets/{ticketId}")
  @PreAuthorize("hasAnyRole('CLIENT', 'PROVIDER_MANAGER', 'PLATFORM_ADMIN')")
  public ApiResponse<List<MaintenanceQuotationResponse>> listQuotations(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID ticketId) {
    return ApiResponse.success(quotations.listQuotationsForTicket(subject(jwt), ticketId));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}

package com.smartdroneinspection.maintenance.api;

import com.smartdroneinspection.maintenance.api.dto.request.CreateMaintenanceTicketRequest;
import com.smartdroneinspection.maintenance.api.dto.response.MaintenanceTicketDetailResponse;
import com.smartdroneinspection.maintenance.api.dto.response.MaintenanceTicketSummaryResponse;
import com.smartdroneinspection.maintenance.service.MaintenanceTicketService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maintenance-tickets")
public class MaintenanceTicketController {

  private final MaintenanceTicketService tickets;

  public MaintenanceTicketController(MaintenanceTicketService tickets) {
    this.tickets = tickets;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('CLIENT')")
  public ApiResponse<UUID> createTicket(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateMaintenanceTicketRequest request) {
    UUID ticketId = tickets.createTicket(subject(jwt), request);
    return ApiResponse.success("Maintenance ticket created successfully", ticketId);
  }

  @GetMapping("/{id}")
  @PreAuthorize(
      "hasAnyRole('CLIENT', 'PROVIDER_MANAGER', 'MAINTENANCE_ENGINEER', 'PLATFORM_ADMIN')")
  public ApiResponse<MaintenanceTicketDetailResponse> getTicketDetail(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ApiResponse.success(tickets.getTicketDetail(subject(jwt), id));
  }

  @GetMapping
  @PreAuthorize(
      "hasAnyRole('CLIENT', 'PROVIDER_MANAGER', 'MAINTENANCE_ENGINEER', 'PLATFORM_ADMIN')")
  public ApiResponse<Page<MaintenanceTicketSummaryResponse>> listTickets(
      @AuthenticationPrincipal Jwt jwt,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ApiResponse.success(tickets.listTickets(subject(jwt), pageable));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}

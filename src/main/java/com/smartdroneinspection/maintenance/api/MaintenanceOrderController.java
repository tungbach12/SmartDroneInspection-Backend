package com.smartdroneinspection.maintenance.api;

import com.smartdroneinspection.maintenance.api.dto.request.CreateChangeRequestPayload;
import com.smartdroneinspection.maintenance.api.dto.response.MaintenanceOrderResponse;
import com.smartdroneinspection.maintenance.service.MaintenanceOrderService;
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
@RequestMapping("/api/v1/maintenance-orders")
public class MaintenanceOrderController {

  private final MaintenanceOrderService orders;

  public MaintenanceOrderController(MaintenanceOrderService orders) {
    this.orders = orders;
  }

  @PostMapping("/quotations/{quotationId}")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('CLIENT')")
  public ApiResponse<UUID> createOrder(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID quotationId) {
    UUID orderId = orders.createOrderFromApprovedQuotation(subject(jwt), quotationId);
    return ApiResponse.success("Maintenance order established", orderId);
  }

  @PostMapping("/{id}/change-requests")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('PROVIDER_MANAGER')")
  public ApiResponse<UUID> createChangeRequest(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody CreateChangeRequestPayload payload) {
    UUID crId = orders.createChangeRequest(subject(jwt), id, payload);
    return ApiResponse.success("Change request submitted", crId);
  }

  @PostMapping("/change-requests/{changeRequestId}/approve")
  @PreAuthorize("hasRole('CLIENT')")
  public ApiResponse<Void> approveChangeRequest(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID changeRequestId) {
    orders.approveChangeRequest(subject(jwt), changeRequestId);
    return ApiResponse.success("Change request approved and order updated", null);
  }

  @PostMapping("/{id}/accept-completion")
  @PreAuthorize("hasRole('CLIENT')")
  public ApiResponse<Void> acceptCompletion(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    orders.acceptCompletion(subject(jwt), id);
    return ApiResponse.success("Maintenance completion accepted and warranty activated", null);
  }

  @PostMapping("/{id}/confirm-payment")
  @PreAuthorize("hasRole('PROVIDER_MANAGER')")
  public ApiResponse<Void> confirmPayment(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam String bankAccountNumber,
      @RequestParam String bankName) {
    orders.confirmPaymentReceipt(subject(jwt), id, bankAccountNumber, bankName);
    return ApiResponse.success("Payment receipt confirmed", null);
  }

  @GetMapping("/{id}")
  @PreAuthorize(
      "hasAnyRole('CLIENT', 'PROVIDER_MANAGER', 'MAINTENANCE_ENGINEER', 'PLATFORM_ADMIN')")
  public ApiResponse<MaintenanceOrderResponse> getOrderDetail(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ApiResponse.success(orders.getOrderDetail(subject(jwt), id));
  }

  @GetMapping("/tickets/{ticketId}")
  @PreAuthorize(
      "hasAnyRole('CLIENT', 'PROVIDER_MANAGER', 'MAINTENANCE_ENGINEER', 'PLATFORM_ADMIN')")
  public ApiResponse<List<MaintenanceOrderResponse>> listOrders(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID ticketId) {
    return ApiResponse.success(orders.listOrdersForTicket(subject(jwt), ticketId));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}

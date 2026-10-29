package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.CaptureDroneLeasePayPalRequest;
import com.smartdroneinspection.assets.api.dto.request.ConfirmDroneLeasePaymentRequest;
import com.smartdroneinspection.assets.api.dto.request.CreateDroneLeaseRequest;
import com.smartdroneinspection.assets.api.dto.request.CreatePlatformDroneRequest;
import com.smartdroneinspection.assets.api.dto.request.ReclaimDroneLeaseRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdatePlatformDroneStatusRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneLeaseResponse;
import com.smartdroneinspection.assets.api.dto.response.PayPalLeaseOrderResponse;
import com.smartdroneinspection.assets.api.dto.response.PlatformDroneResponse;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.DroneLease;
import com.smartdroneinspection.assets.domain.PlatformDrone;
import com.smartdroneinspection.assets.domain.enums.DroneLeaseStatus;
import com.smartdroneinspection.assets.domain.enums.PlatformDroneStatus;
import com.smartdroneinspection.assets.repository.DroneLeaseRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.assets.repository.PlatformDroneRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.gateway.PaymentGateway;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DroneLeaseService {

  private static final String SETTLEMENT_CURRENCY = "USD";
  private static final String CAPTURE_STATUS_COMPLETED = "COMPLETED";

  private final PlatformDroneRepository platformDrones;
  private final DroneLeaseRepository droneLeases;
  private final DroneRepository customerDrones;
  private final UserAccess userAccess;
  private final PaymentGateway paymentGateway;

  public DroneLeaseService(
      PlatformDroneRepository platformDrones,
      DroneLeaseRepository droneLeases,
      DroneRepository customerDrones,
      UserAccess userAccess,
      PaymentGateway paymentGateway) {
    this.platformDrones = platformDrones;
    this.droneLeases = droneLeases;
    this.customerDrones = customerDrones;
    this.userAccess = userAccess;
    this.paymentGateway = paymentGateway;
  }

  // ===========================================================================
  // Platform Pool Operations
  // ===========================================================================

  @Transactional(readOnly = true)
  public List<PlatformDroneResponse> listPool(UUID actorId) {
    requirePlatformReader(actorId);
    return platformDrones.findAllByOrderByCreatedAtDesc().stream()
        .map(PlatformDroneResponse::from)
        .toList();
  }

  public PlatformDroneResponse createPlatformDrone(
      UUID actorId, CreatePlatformDroneRequest request) {
    requirePlatformManager(actorId);
    requireJsonDocument(
        request.payloadMetadata(), "Payload metadata must be a valid JSON document.");
    String serialNumber = request.serialNumber().trim();
    if (platformDrones.existsBySerialNumber(serialNumber)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "DUPLICATE_SERIAL_NUMBER", "Serial number already exists in pool");
    }
    if (request.monthlyRateUsd() == null
        || request.monthlyRateUsd().compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_MONTHLY_RATE", "Monthly rate must be greater than zero");
    }
    PlatformDrone created =
        platformDrones.saveAndFlush(
            new PlatformDrone(
                serialNumber,
                request.model(),
                request.manufacturer(),
                request.payloadMetadata(),
                request.monthlyRateUsd().setScale(2, RoundingMode.HALF_UP),
                request.notes()));
    return PlatformDroneResponse.from(created);
  }

  public PlatformDroneResponse updatePlatformDroneStatus(
      UUID actorId, UUID platformDroneId, UpdatePlatformDroneStatusRequest request) {
    requirePlatformManager(actorId);
    PlatformDrone drone =
        platformDrones
            .findByIdForUpdate(platformDroneId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "PLATFORM_DRONE_NOT_FOUND",
                        "Platform drone not found"));
    if (drone.getStatus() == PlatformDroneStatus.LEASED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "DRONE_CURRENTLY_LEASED",
          "Cannot manually change status while drone is leased; reclaim the lease first");
    }
    if (request.status() == PlatformDroneStatus.LEASED) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_STATE",
          "Platform drone status cannot be set to LEASED manually");
    }
    drone.changeStatus(request.status(), request.notes());
    return PlatformDroneResponse.from(platformDrones.saveAndFlush(drone));
  }

  // ===========================================================================
  // Customer Lease Operations (CUSTOMER)
  // ===========================================================================

  @Transactional(readOnly = true)
  public List<PlatformDroneResponse> listAvailableDrones(UUID actorId) {
    requireOrgManager(actorId);
    return platformDrones.findByStatusOrderByCreatedAtDesc(PlatformDroneStatus.AVAILABLE).stream()
        .map(PlatformDroneResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<DroneLeaseResponse> listOrganizationLeases(UUID actorId) {
    UserAccess.ActiveUser actor = requireOrgManager(actorId);
    UUID organizationId = actor.organizationId();
    String organizationName =
        userAccess.findOrganizationName(organizationId).orElse("Organization");
    Map<UUID, PlatformDrone> droneCache = new LinkedHashMap<>();
    return droneLeases.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
        .map(
            lease -> {
              PlatformDrone pd =
                  droneCache.computeIfAbsent(
                      lease.getPlatformDroneId(), id -> platformDrones.findById(id).orElse(null));
              return DroneLeaseResponse.from(lease, pd, organizationName);
            })
        .toList();
  }

  public DroneLeaseResponse requestLease(UUID actorId, CreateDroneLeaseRequest request) {
    UserAccess.ActiveUser actor = requireOrgManager(actorId);
    if (request.leasePeriodMonths() == null
        || request.leasePeriodMonths() < 1
        || request.leasePeriodMonths() > 60) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_LEASE_PERIOD",
          "Lease period must be between 1 and 60 months");
    }
    PlatformDrone platformDrone =
        platformDrones
            .findById(request.platformDroneId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "PLATFORM_DRONE_NOT_FOUND",
                        "Platform drone not found"));
    if (platformDrone.getStatus() != PlatformDroneStatus.AVAILABLE) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "DRONE_NOT_AVAILABLE",
          "Platform drone is not available for leasing");
    }
    BigDecimal agreedAmount =
        platformDrone
            .getMonthlyRateUsd()
            .multiply(BigDecimal.valueOf(request.leasePeriodMonths()))
            .setScale(2, RoundingMode.HALF_UP);
    DroneLease lease =
        droneLeases.saveAndFlush(
            new DroneLease(
                platformDrone.getId(),
                actor.organizationId(),
                actor.id(),
                request.leasePeriodMonths(),
                platformDrone.getMonthlyRateUsd(),
                agreedAmount,
                request.notes()));
    String orgName = userAccess.findOrganizationName(actor.organizationId()).orElse("Organization");
    return DroneLeaseResponse.from(lease, platformDrone, orgName);
  }

  public PayPalLeaseOrderResponse createPayPalOrder(UUID actorId, UUID leaseId) {
    UserAccess.ActiveUser actor = requireOrgManager(actorId);
    DroneLease lease =
        droneLeases
            .findByIdAndOrganizationId(leaseId, actor.organizationId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "LEASE_NOT_FOUND", "Drone lease not found"));
    if (lease.getStatus() == DroneLeaseStatus.ACTIVE) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "LEASE_ALREADY_ACTIVE", "Drone lease is already active");
    }
    if (lease.getStatus() != DroneLeaseStatus.PENDING) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "LEASE_NOT_PENDING", "Only pending drone leases can be paid");
    }
    PlatformDrone platformDrone =
        platformDrones
            .findById(lease.getPlatformDroneId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "PLATFORM_DRONE_NOT_FOUND",
                        "Platform drone not found"));
    if (platformDrone.getStatus() != PlatformDroneStatus.AVAILABLE) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "DRONE_NOT_AVAILABLE",
          "Platform drone is no longer available for leasing");
    }
    BigDecimal amount = lease.getAgreedAmountUsd().setScale(2, RoundingMode.HALF_UP);
    PaymentGateway.PaymentGatewayOrder order =
        invokeGateway(
            () ->
                paymentGateway.createOrder(
                    amount, SETTLEMENT_CURRENCY, "Drone lease " + platformDrone.getSerialNumber()));
    lease.recordPayPalOrder(order.orderId());
    droneLeases.saveAndFlush(lease);
    return new PayPalLeaseOrderResponse(order.orderId(), order.status(), order.approvalUrl());
  }

  public DroneLeaseResponse capturePayPalOrder(
      UUID actorId, UUID leaseId, CaptureDroneLeasePayPalRequest request) {
    UserAccess.ActiveUser actor = requireOrgManager(actorId);
    DroneLease lease =
        droneLeases
            .findByIdAndOrganizationId(leaseId, actor.organizationId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "LEASE_NOT_FOUND", "Drone lease not found"));
    if (lease.getStatus() == DroneLeaseStatus.ACTIVE) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "LEASE_ALREADY_ACTIVE", "Drone lease is already active");
    }
    PlatformDrone platformDrone =
        platformDrones
            .findByIdForUpdate(lease.getPlatformDroneId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "PLATFORM_DRONE_NOT_FOUND",
                        "Platform drone not found"));
    if (platformDrone.getStatus() != PlatformDroneStatus.AVAILABLE) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "DRONE_NOT_AVAILABLE",
          "Platform drone is no longer available for leasing");
    }
    if (lease.getStatus() != DroneLeaseStatus.PENDING) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "LEASE_NOT_PENDING", "Only pending drone leases can be captured");
    }
    if (lease.getPaypalOrderId() == null
        || !lease.getPaypalOrderId().equals(request.paypalOrderId())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PAYMENT_ORDER_MISMATCH",
          "The captured order does not match the lease payment order");
    }

    PaymentGateway.Capture capture =
        invokeGateway(() -> paymentGateway.captureOrder(request.paypalOrderId()));
    if (capture.captureId() == null
        || capture.captureId().isBlank()
        || !CAPTURE_STATUS_COMPLETED.equalsIgnoreCase(capture.status())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PAYMENT_CAPTURE_NOT_COMPLETED",
          "The payment provider did not report a completed capture");
    }
    BigDecimal settledAmount = parseAmount(capture.amount());
    BigDecimal expectedAmount = lease.getAgreedAmountUsd().setScale(2, RoundingMode.HALF_UP);
    if (settledAmount == null || settledAmount.compareTo(expectedAmount) != 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PAYMENT_AMOUNT_MISMATCH",
          "The settled amount does not match the contracted lease amount");
    }

    Instant startsAt = Instant.now();
    Instant endsAt =
        startsAt.atZone(ZoneOffset.UTC).plusMonths(lease.getLeasePeriodMonths()).toInstant();
    Drone customerDrone = provisionCustomerDrone(lease, platformDrone, endsAt, lease.getNotes());
    platformDrone.markLeased();
    platformDrones.saveAndFlush(platformDrone);

    lease.activatePayPal(
        request.paypalOrderId(),
        capture.captureId(),
        startsAt,
        endsAt,
        settledAmount,
        actor.id(),
        customerDrone.getId());
    DroneLease saved = droneLeases.saveAndFlush(lease);
    cancelCompetingPendingLeases(platformDrone.getId(), saved.getId());

    String orgName = userAccess.findOrganizationName(actor.organizationId()).orElse("Organization");
    return DroneLeaseResponse.from(saved, platformDrone, orgName);
  }

  // ===========================================================================
  // Platform Lease Operations
  // ===========================================================================

  @Transactional(readOnly = true)
  public List<DroneLeaseResponse> listAllLeases(UUID actorId, DroneLeaseStatus statusFilter) {
    requirePlatformReader(actorId);
    List<DroneLease> list =
        statusFilter != null
            ? droneLeases.findByStatusOrderByCreatedAtDesc(statusFilter)
            : droneLeases.findAllByOrderByCreatedAtDesc();
    Map<UUID, PlatformDrone> droneCache = new LinkedHashMap<>();
    Map<UUID, String> orgNameCache = new LinkedHashMap<>();
    return list.stream()
        .map(
            lease -> {
              PlatformDrone pd =
                  droneCache.computeIfAbsent(
                      lease.getPlatformDroneId(), id -> platformDrones.findById(id).orElse(null));
              String orgName =
                  orgNameCache.computeIfAbsent(
                      lease.getOrganizationId(),
                      id -> userAccess.findOrganizationName(id).orElse("Organization"));
              return DroneLeaseResponse.from(lease, pd, orgName);
            })
        .toList();
  }

  public DroneLeaseResponse confirmLeasePayment(
      UUID actorId, UUID leaseId, ConfirmDroneLeasePaymentRequest request) {
    requirePlatformManager(actorId);
    if (request.externalReference() == null || request.externalReference().isBlank()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EXTERNAL_REFERENCE_REQUIRED",
          "Bank wire statement or invoice reference is required");
    }
    DroneLease lease =
        droneLeases
            .findById(leaseId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "LEASE_NOT_FOUND", "Drone lease not found"));
    if (lease.getStatus() == DroneLeaseStatus.ACTIVE) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "LEASE_ALREADY_ACTIVE", "This drone lease is already active");
    }
    PlatformDrone platformDrone =
        platformDrones
            .findByIdForUpdate(lease.getPlatformDroneId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "PLATFORM_DRONE_NOT_FOUND",
                        "Platform drone not found"));
    if (platformDrone.getStatus() != PlatformDroneStatus.AVAILABLE) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "DRONE_NOT_AVAILABLE",
          "Platform drone is not available for leasing");
    }
    if (lease.getStatus() != DroneLeaseStatus.PENDING) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "LEASE_NOT_PENDING", "Only pending drone leases can be confirmed");
    }

    Instant effectiveStartsAt = request.startsAt() != null ? request.startsAt() : Instant.now();
    Instant effectiveEndsAt =
        request.endsAt() != null
            ? request.endsAt()
            : effectiveStartsAt
                .atZone(ZoneOffset.UTC)
                .plusMonths(lease.getLeasePeriodMonths())
                .toInstant();
    if (!effectiveEndsAt.isAfter(effectiveStartsAt)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_DATE_RANGE",
          "End date must be strictly after start date");
    }
    BigDecimal finalAmount =
        request.agreedAmountUsd() != null
            ? request.agreedAmountUsd().setScale(2, RoundingMode.HALF_UP)
            : lease.getAgreedAmountUsd();

    String effectiveNotes =
        request.notes() != null && !request.notes().isBlank()
            ? request.notes().trim()
            : lease.getNotes();
    Drone customerDrone =
        provisionCustomerDrone(lease, platformDrone, effectiveEndsAt, effectiveNotes);
    platformDrone.markLeased();
    platformDrones.saveAndFlush(platformDrone);

    lease.activateWire(
        request.externalReference(),
        effectiveStartsAt,
        effectiveEndsAt,
        finalAmount,
        actorId,
        customerDrone.getId(),
        request.notes());
    DroneLease saved = droneLeases.saveAndFlush(lease);
    cancelCompetingPendingLeases(platformDrone.getId(), saved.getId());

    String orgName =
        userAccess.findOrganizationName(saved.getOrganizationId()).orElse("Organization");
    return DroneLeaseResponse.from(saved, platformDrone, orgName);
  }

  public DroneLeaseResponse reclaimLease(
      UUID actorId, UUID leaseId, ReclaimDroneLeaseRequest request) {
    requirePlatformManager(actorId);
    DroneLease lease =
        droneLeases
            .findById(leaseId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "LEASE_NOT_FOUND", "Drone lease not found"));
    if (lease.getStatus() != DroneLeaseStatus.ACTIVE) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "LEASE_NOT_ACTIVE", "Only active drone leases can be reclaimed");
    }
    PlatformDrone platformDrone =
        platformDrones
            .findByIdForUpdate(lease.getPlatformDroneId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "PLATFORM_DRONE_NOT_FOUND",
                        "Platform drone not found"));

    if (lease.getCustomerDroneId() != null) {
      customerDrones
          .findByIdAndOrganizationId(lease.getCustomerDroneId(), lease.getOrganizationId())
          .ifPresent(
              drone -> {
                drone.reclaimLease();
                customerDrones.saveAndFlush(drone);
              });
    } else {
      customerDrones
          .findByOrganizationIdAndSerialNumber(
              lease.getOrganizationId(), platformDrone.getSerialNumber())
          .ifPresent(
              drone -> {
                drone.reclaimLease();
                customerDrones.saveAndFlush(drone);
              });
    }

    platformDrone.markAvailable();
    platformDrones.saveAndFlush(platformDrone);

    String notes = request != null ? request.notes() : null;
    lease.reclaim(Instant.now(), notes);
    DroneLease saved = droneLeases.saveAndFlush(lease);

    String orgName =
        userAccess.findOrganizationName(saved.getOrganizationId()).orElse("Organization");
    return DroneLeaseResponse.from(saved, platformDrone, orgName);
  }

  // ===========================================================================
  // Helpers
  // ===========================================================================

  private Drone provisionCustomerDrone(
      DroneLease lease, PlatformDrone platformDrone, Instant leaseExpiresAt, String notes) {
    return customerDrones
        .findByOrganizationIdAndSerialNumber(
            lease.getOrganizationId(), platformDrone.getSerialNumber())
        .map(
            existing -> {
              existing.activateLease(
                  lease.getId(),
                  leaseExpiresAt,
                  platformDrone.getModel(),
                  platformDrone.getManufacturer(),
                  platformDrone.getPayloadMetadata(),
                  notes);
              return customerDrones.saveAndFlush(existing);
            })
        .orElseGet(
            () ->
                customerDrones.saveAndFlush(
                    Drone.createLeased(
                        lease.getOrganizationId(),
                        platformDrone.getSerialNumber(),
                        platformDrone.getModel(),
                        platformDrone.getManufacturer(),
                        platformDrone.getPayloadMetadata(),
                        lease.getId(),
                        leaseExpiresAt,
                        notes != null ? notes : platformDrone.getNotes())));
  }

  private void cancelCompetingPendingLeases(UUID platformDroneId, UUID activatedLeaseId) {
    droneLeases
        .findByPlatformDroneIdAndStatus(platformDroneId, DroneLeaseStatus.PENDING)
        .forEach(
            other -> {
              if (!other.getId().equals(activatedLeaseId)) {
                other.cancel("Cancelled because platform drone was leased under another contract");
                droneLeases.saveAndFlush(other);
              }
            });
  }

  private UserAccess.ActiveUser requirePlatformReader(UUID userId) {
    UserAccess.ActiveUser user =
        userAccess
            .findActiveUser(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.FORBIDDEN, "FORBIDDEN", "Active platform session required"));
    if ((!user.hasRole(Roles.ADMIN) && !user.hasRole(Roles.MANAGER))
        || user.organizationId() != null) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Platform authority required");
    }
    return user;
  }

  private UserAccess.ActiveUser requirePlatformManager(UUID userId) {
    UserAccess.ActiveUser user =
        userAccess
            .findActiveUser(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "FORBIDDEN",
                        "Active platform manager session required"));
    if (!user.hasRole(Roles.MANAGER) || user.organizationId() != null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Platform Manager authority required");
    }
    return user;
  }

  private UserAccess.ActiveUser requireOrgManager(UUID userId) {
    UserAccess.ActiveUser user =
        userAccess
            .findActiveUser(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.FORBIDDEN, "FORBIDDEN", "Active user session required"));
    if (!user.hasRole(Roles.CUSTOMER) || user.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Organization Manager authority required");
    }
    return user;
  }

  private void requireJsonDocument(String value, String message) {
    if (value == null) {
      return;
    }
    try {
      tools.jackson.databind.json.JsonMapper.shared().readTree(value);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException(message);
    }
  }

  private <T> T invokeGateway(Supplier<T> request) {
    try {
      return request.get();
    } catch (IllegalStateException exception) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "PAYMENT_GATEWAY_UNAVAILABLE",
          "Payment provider request failed. Please retry or use corporate wire transfer.");
    }
  }

  private BigDecimal parseAmount(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
    } catch (NumberFormatException exception) {
      return null;
    }
  }
}

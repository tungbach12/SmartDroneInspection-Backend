package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.DroneLease;
import com.smartdroneinspection.assets.domain.PlatformDrone;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DroneLeaseResponse(
    UUID id,
    UUID platformDroneId,
    String droneSerialNumber,
    String droneModel,
    String droneManufacturer,
    UUID organizationId,
    String organizationName,
    UUID customerDroneId,
    UUID requestedByUserId,
    int leasePeriodMonths,
    BigDecimal monthlyRateUsd,
    BigDecimal agreedAmountUsd,
    String status,
    Instant startsAt,
    Instant endsAt,
    Instant reclaimedAt,
    String externalReference,
    String wireReferenceCode,
    String paypalOrderId,
    String paypalCaptureId,
    UUID confirmedByUserId,
    Instant confirmedAt,
    String notes,
    Instant createdAt,
    Instant updatedAt) {

  public static DroneLeaseResponse from(
      DroneLease lease, PlatformDrone platformDrone, String organizationName) {
    String wireRef =
        lease.getId() != null
            ? "SDI-LEASE-" + lease.getId().toString().substring(0, 8).toUpperCase()
            : null;
    return new DroneLeaseResponse(
        lease.getId(),
        lease.getPlatformDroneId(),
        platformDrone != null ? platformDrone.getSerialNumber() : null,
        platformDrone != null ? platformDrone.getModel() : null,
        platformDrone != null ? platformDrone.getManufacturer() : null,
        lease.getOrganizationId(),
        organizationName,
        lease.getCustomerDroneId(),
        lease.getRequestedByUserId(),
        lease.getLeasePeriodMonths(),
        lease.getMonthlyRateUsd(),
        lease.getAgreedAmountUsd(),
        lease.getStatus().name(),
        lease.getStartsAt(),
        lease.getEndsAt(),
        lease.getReclaimedAt(),
        lease.getExternalReference(),
        wireRef,
        lease.getPaypalOrderId(),
        lease.getPaypalCaptureId(),
        lease.getConfirmedByUserId(),
        lease.getConfirmedAt(),
        lease.getNotes(),
        lease.getCreatedAt(),
        lease.getUpdatedAt());
  }
}

package com.smartdroneinspection.assets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.api.dto.request.CaptureDroneLeasePayPalRequest;
import com.smartdroneinspection.assets.api.dto.request.ConfirmDroneLeasePaymentRequest;
import com.smartdroneinspection.assets.api.dto.request.CreateDroneLeaseRequest;
import com.smartdroneinspection.assets.api.dto.request.CreatePlatformDroneRequest;
import com.smartdroneinspection.assets.api.dto.request.ReclaimDroneLeaseRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdatePlatformDroneStatusRequest;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DroneLeaseServiceTest {

  private PlatformDroneRepository platformDrones;
  private DroneLeaseRepository droneLeases;
  private DroneRepository customerDrones;
  private UserAccess userAccess;
  private PaymentGateway paymentGateway;
  private DroneLeaseService service;

  private final UUID platformManagerId = UUID.randomUUID();
  private final UUID platformAdminId = UUID.randomUUID();
  private final UUID orgManagerId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();
  private final UUID platformDroneId = UUID.randomUUID();
  private final UUID leaseId = UUID.randomUUID();
  private final UUID customerDroneId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    platformDrones = mock(PlatformDroneRepository.class);
    droneLeases = mock(DroneLeaseRepository.class);
    customerDrones = mock(DroneRepository.class);
    userAccess = mock(UserAccess.class);
    paymentGateway = mock(PaymentGateway.class);

    service =
        new DroneLeaseService(
            platformDrones, droneLeases, customerDrones, userAccess, paymentGateway);

    when(platformDrones.saveAndFlush(any(PlatformDrone.class)))
        .thenAnswer(
            inv -> {
              PlatformDrone pd = inv.getArgument(0);
              if (pd.getId() == null) {
                setEntityId(pd, platformDroneId);
              }
              return pd;
            });
    when(droneLeases.saveAndFlush(any(DroneLease.class)))
        .thenAnswer(
            inv -> {
              DroneLease dl = inv.getArgument(0);
              if (dl.getId() == null) {
                setEntityId(dl, leaseId);
              }
              return dl;
            });
    when(customerDrones.saveAndFlush(any(Drone.class)))
        .thenAnswer(
            inv -> {
              Drone cd = inv.getArgument(0);
              if (cd.getId() == null) {
                setEntityId(cd, customerDroneId);
              }
              return cd;
            });

    when(userAccess.findActiveUser(platformManagerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    platformManagerId, Set.of(Roles.MANAGER), null, "Platform Manager")));
    when(userAccess.findActiveUser(platformAdminId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    platformAdminId, Set.of(Roles.ADMIN), null, "Platform Admin")));
    when(userAccess.findActiveUser(orgManagerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    orgManagerId, Set.of(Roles.CUSTOMER), orgId, "Customer Manager")));
    when(userAccess.findOrganizationName(orgId)).thenReturn(Optional.of("Apex Infra"));
  }

  @Test
  void listPoolAccessibleToPlatformReaderAndForbiddenForCustomer() {
    PlatformDrone drone = platformDrone("SDI-001", BigDecimal.valueOf(1000));
    when(platformDrones.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(drone));

    assertThat(service.listPool(platformManagerId)).hasSize(1);
    assertThat(service.listPool(platformAdminId)).hasSize(1);

    assertThatThrownBy(() -> service.listPool(orgManagerId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Platform authority required");
  }

  @Test
  void createPlatformDroneValidatesUniqueSerialAndRateAndJson() {
    CreatePlatformDroneRequest validRequest =
        new CreatePlatformDroneRequest(
            "SDI-002",
            "M350",
            "DJI",
            "{\"sensor\":\"H20T\"}",
            BigDecimal.valueOf(1200),
            "Pool notes");
    when(platformDrones.existsBySerialNumber("SDI-002")).thenReturn(false);
    when(platformDrones.saveAndFlush(any(PlatformDrone.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    var response = service.createPlatformDrone(platformManagerId, validRequest);
    assertThat(response.serialNumber()).isEqualTo("SDI-002");

    when(platformDrones.existsBySerialNumber("SDI-002")).thenReturn(true);
    assertThatThrownBy(() -> service.createPlatformDrone(platformManagerId, validRequest))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Serial number already exists");

    CreatePlatformDroneRequest invalidRate =
        new CreatePlatformDroneRequest("SDI-003", "M350", "DJI", null, BigDecimal.ZERO, null);
    assertThatThrownBy(() -> service.createPlatformDrone(platformManagerId, invalidRate))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Monthly rate must be greater than zero");

    CreatePlatformDroneRequest invalidJson =
        new CreatePlatformDroneRequest(
            "SDI-004", "M350", "DJI", "invalid-json", BigDecimal.valueOf(100), null);
    assertThatThrownBy(() -> service.createPlatformDrone(platformManagerId, invalidJson))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("valid JSON");
  }

  @Test
  void updatePlatformDroneStatusValidatesLeaseStateAndExistence() {
    PlatformDrone drone = platformDrone("SDI-005", BigDecimal.valueOf(1000));
    when(platformDrones.findByIdForUpdate(platformDroneId)).thenReturn(Optional.of(drone));

    service.updatePlatformDroneStatus(
        platformManagerId,
        platformDroneId,
        new UpdatePlatformDroneStatusRequest(PlatformDroneStatus.MAINTENANCE, null));
    assertThat(drone.getStatus()).isEqualTo(PlatformDroneStatus.MAINTENANCE);

    drone.markLeased();
    assertThatThrownBy(
            () ->
                service.updatePlatformDroneStatus(
                    platformManagerId,
                    platformDroneId,
                    new UpdatePlatformDroneStatusRequest(PlatformDroneStatus.AVAILABLE, null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Cannot manually change status while drone is leased");

    when(platformDrones.findByIdForUpdate(platformDroneId)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                service.updatePlatformDroneStatus(
                    platformManagerId,
                    platformDroneId,
                    new UpdatePlatformDroneStatusRequest(PlatformDroneStatus.AVAILABLE, null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Platform drone not found");
  }

  @Test
  void customerListsAvailableDronesAndOrganizationLeases() {
    PlatformDrone available = platformDrone("SDI-006", BigDecimal.valueOf(800));
    when(platformDrones.findByStatusOrderByCreatedAtDesc(PlatformDroneStatus.AVAILABLE))
        .thenReturn(List.of(available));
    assertThat(service.listAvailableDrones(orgManagerId)).hasSize(1);

    DroneLease lease = droneLease(platformDroneId, orgId, 6, BigDecimal.valueOf(800));
    when(droneLeases.findByOrganizationIdOrderByCreatedAtDesc(orgId)).thenReturn(List.of(lease));
    when(platformDrones.findAllById(any())).thenReturn(List.of(available));
    assertThat(service.listOrganizationLeases(orgManagerId)).hasSize(1);
  }

  @Test
  void requestLeaseValidatesDroneAvailabilityAndMonths() {
    PlatformDrone drone = platformDrone("SDI-007", BigDecimal.valueOf(1500));
    when(platformDrones.findById(platformDroneId)).thenReturn(Optional.of(drone));

    CreateDroneLeaseRequest request =
        new CreateDroneLeaseRequest(platformDroneId, 12, "Project lease");
    var response = service.requestLease(orgManagerId, request);
    assertThat(response.agreedAmountUsd()).isEqualByComparingTo("18000.00");
    assertThat(response.wireReferenceCode()).startsWith("SDI-LEASE-");

    CreateDroneLeaseRequest invalidPeriod = new CreateDroneLeaseRequest(platformDroneId, 0, null);
    assertThatThrownBy(() -> service.requestLease(orgManagerId, invalidPeriod))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("between 1 and 60 months");

    drone.markLeased();
    assertThatThrownBy(() -> service.requestLease(orgManagerId, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("not available for leasing");

    when(platformDrones.findById(platformDroneId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.requestLease(orgManagerId, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Platform drone not found");
  }

  @Test
  void createPayPalOrderValidatesLeaseStateAndAvailability() {
    DroneLease lease = droneLease(platformDroneId, orgId, 3, BigDecimal.valueOf(1000));
    PlatformDrone drone = platformDrone("SDI-008", BigDecimal.valueOf(1000));

    when(droneLeases.findByIdAndOrganizationId(leaseId, orgId)).thenReturn(Optional.of(lease));
    when(platformDrones.findById(platformDroneId)).thenReturn(Optional.of(drone));
    when(paymentGateway.createOrder(any(), any(), any()))
        .thenReturn(
            new PaymentGateway.PaymentGatewayOrder(
                "PAYPAL-123", "CREATED", "https://paypal.test/approve"));

    var order = service.createPayPalOrder(orgManagerId, leaseId);
    assertThat(order.orderId()).isEqualTo("PAYPAL-123");
    assertThat(lease.getPaypalOrderId()).isEqualTo("PAYPAL-123");

    lease.activateWire(
        "WIRE-1",
        Instant.now(),
        Instant.now().plusSeconds(86400),
        BigDecimal.valueOf(3000),
        orgManagerId,
        customerDroneId,
        "Notes");
    assertThatThrownBy(() -> service.createPayPalOrder(orgManagerId, leaseId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("already active");

    when(droneLeases.findByIdAndOrganizationId(leaseId, orgId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.createPayPalOrder(orgManagerId, leaseId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Drone lease not found");
  }

  @Test
  void capturePayPalOrderValidatesOrderMatchCompletionAndAmount() {
    DroneLease lease = droneLease(platformDroneId, orgId, 3, BigDecimal.valueOf(1000));
    lease.recordPayPalOrder("PAYPAL-EXPECTED");
    PlatformDrone drone = platformDrone("SDI-009", BigDecimal.valueOf(1000));

    when(droneLeases.findByIdAndOrganizationId(leaseId, orgId)).thenReturn(Optional.of(lease));
    when(platformDrones.findByIdForUpdate(platformDroneId)).thenReturn(Optional.of(drone));
    when(customerDrones.findByOrganizationIdAndSerialNumber(orgId, "SDI-009"))
        .thenReturn(Optional.empty());
    when(paymentGateway.captureOrder("PAYPAL-EXPECTED"))
        .thenReturn(
            new PaymentGateway.Capture(
                "PAYPAL-EXPECTED", "CAP-1", "COMPLETED", "3000.00", "USD", "payer@example.test"));

    CaptureDroneLeasePayPalRequest matchRequest =
        new CaptureDroneLeasePayPalRequest("PAYPAL-EXPECTED");
    var response = service.capturePayPalOrder(orgManagerId, leaseId, matchRequest);
    assertThat(response.status()).isEqualTo("ACTIVE");
    assertThat(drone.getStatus()).isEqualTo(PlatformDroneStatus.LEASED);

    CaptureDroneLeasePayPalRequest mismatch = new CaptureDroneLeasePayPalRequest("PAYPAL-OTHER");
    DroneLease pendingLease = droneLease(platformDroneId, orgId, 3, BigDecimal.valueOf(1000));
    pendingLease.recordPayPalOrder("PAYPAL-EXPECTED");
    drone.markAvailable();
    when(droneLeases.findByIdAndOrganizationId(leaseId, orgId))
        .thenReturn(Optional.of(pendingLease));
    assertThatThrownBy(() -> service.capturePayPalOrder(orgManagerId, leaseId, mismatch))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("captured order does not match");

    when(paymentGateway.captureOrder("PAYPAL-EXPECTED"))
        .thenReturn(
            new PaymentGateway.Capture(
                "PAYPAL-EXPECTED", "CAP-2", "DECLINED", "3000.00", "USD", "payer@example.test"));
    assertThatThrownBy(() -> service.capturePayPalOrder(orgManagerId, leaseId, matchRequest))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("payment provider did not report a completed capture");

    when(paymentGateway.captureOrder("PAYPAL-EXPECTED"))
        .thenReturn(
            new PaymentGateway.Capture(
                "PAYPAL-EXPECTED", "CAP-3", "COMPLETED", "2500.00", "USD", "payer@example.test"));
    assertThatThrownBy(() -> service.capturePayPalOrder(orgManagerId, leaseId, matchRequest))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("settled amount does not match");
  }

  @Test
  void confirmLeasePaymentActivatesLeaseAndProvisionsDrone() {
    DroneLease lease = droneLease(platformDroneId, orgId, 6, BigDecimal.valueOf(1000));
    PlatformDrone drone = platformDrone("SDI-010", BigDecimal.valueOf(1000));

    when(droneLeases.findById(leaseId)).thenReturn(Optional.of(lease));
    when(platformDrones.findByIdForUpdate(platformDroneId)).thenReturn(Optional.of(drone));
    when(customerDrones.findByOrganizationIdAndSerialNumber(orgId, "SDI-010"))
        .thenReturn(Optional.empty());

    ConfirmDroneLeasePaymentRequest request =
        new ConfirmDroneLeasePaymentRequest(
            "WIRE-REF-100", null, null, null, "Confirmed by finance");
    var response = service.confirmLeasePayment(platformManagerId, leaseId, request);

    assertThat(response.status()).isEqualTo("ACTIVE");
    assertThat(drone.getStatus()).isEqualTo(PlatformDroneStatus.LEASED);
  }

  @Test
  void reclaimLeaseReclaimsCustomerDroneAndMakesPlatformDroneAvailable() {
    DroneLease lease = droneLease(platformDroneId, orgId, 6, BigDecimal.valueOf(1000));
    Drone customerDrone = new Drone(orgId, "SDI-011");
    setEntityId(customerDrone, customerDroneId);
    lease.activateWire(
        "WIRE-1",
        Instant.now(),
        Instant.now().plusSeconds(86400),
        BigDecimal.valueOf(6000),
        orgManagerId,
        customerDroneId,
        "Notes");

    PlatformDrone platformDrone = platformDrone("SDI-011", BigDecimal.valueOf(1000));
    platformDrone.markLeased();

    when(droneLeases.findById(leaseId)).thenReturn(Optional.of(lease));
    when(platformDrones.findByIdForUpdate(platformDroneId)).thenReturn(Optional.of(platformDrone));
    when(customerDrones.findByIdAndOrganizationId(customerDroneId, orgId))
        .thenReturn(Optional.of(customerDrone));
    when(droneLeases.saveAndFlush(any(DroneLease.class))).thenAnswer(inv -> inv.getArgument(0));

    var response =
        service.reclaimLease(
            platformManagerId, leaseId, new ReclaimDroneLeaseRequest("Return to base"));
    assertThat(response.status()).isEqualTo("RECLAIMED");
    assertThat(platformDrone.getStatus()).isEqualTo(PlatformDroneStatus.AVAILABLE);

    when(droneLeases.findById(leaseId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.reclaimLease(platformManagerId, leaseId, null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Drone lease not found");
  }

  @Test
  void listAllLeasesReturnsMappedLeasesWithAndWithoutFilter() {
    DroneLease lease = droneLease(platformDroneId, orgId, 3, BigDecimal.valueOf(1000));
    PlatformDrone drone = platformDrone("SDI-012", BigDecimal.valueOf(1000));

    when(droneLeases.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(lease));
    when(droneLeases.findByStatusOrderByCreatedAtDesc(DroneLeaseStatus.PENDING))
        .thenReturn(List.of(lease));
    when(platformDrones.findAllById(any())).thenReturn(List.of(drone));

    assertThat(service.listAllLeases(platformManagerId, null)).hasSize(1);
    assertThat(service.listAllLeases(platformManagerId, DroneLeaseStatus.PENDING)).hasSize(1);
  }

  private PlatformDrone platformDrone(String serial, BigDecimal monthlyRate) {
    PlatformDrone drone = new PlatformDrone(serial, "M350", "DJI", "{}", monthlyRate, "Pool note");
    setEntityId(drone, platformDroneId);
    return drone;
  }

  private DroneLease droneLease(UUID droneId, UUID org, int months, BigDecimal rate) {
    DroneLease lease =
        new DroneLease(
            droneId,
            org,
            orgManagerId,
            months,
            rate,
            rate.multiply(BigDecimal.valueOf(months)),
            "Lease note");
    setEntityId(lease, leaseId);
    return lease;
  }

  private static void setEntityId(Object target, UUID id) {
    ReflectionTestUtils.setField(target, "id", id);
  }
}

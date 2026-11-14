package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.api.dto.request.CreateInspectionRequest;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class InspectionCreationServiceTest {

  private InspectionRepository inspections;
  private AssetRepository assets;
  private DroneRepository drones;
  private UserAccess userAccess;
  private InspectionCreationService service;

  private final UUID ownerCustomerId = UUID.randomUUID();
  private final UUID inspectorId = UUID.randomUUID();
  private final UUID partnerInspectorId = UUID.randomUUID();
  private final UUID assetId = UUID.randomUUID();
  private final UUID droneId = UUID.randomUUID();
  private final UUID inspectionId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    inspections = mock(InspectionRepository.class);
    assets = mock(AssetRepository.class);
    drones = mock(DroneRepository.class);
    userAccess = mock(UserAccess.class);
    service =
        new InspectionCreationService(
            inspections,
            assets,
            drones,
            userAccess,
            Optional.empty(),
            mock(ApplicationEventPublisher.class));

    when(userAccess.findActiveUser(ownerCustomerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(ownerCustomerId, Set.of(Roles.CUSTOMER), "Customer")));
  }

  @Test
  void createRejectsExpiredPilotLicenseOrExpiredDroneRegistration() {
    Instant plannedStart = Instant.now().plus(10, ChronoUnit.DAYS);
    Instant plannedEnd = plannedStart.plus(2, ChronoUnit.HOURS);

    Asset asset =
        new Asset(
            ownerCustomerId,
            UUID.randomUUID(),
            "A-1",
            "Bridge",
            null,
            "Loc",
            null,
            null,
            null,
            ownerCustomerId);
    ReflectionTestUtils.setField(asset, "id", assetId);
    when(assets.findByIdAndOwnerCustomerId(assetId, ownerCustomerId))
        .thenReturn(Optional.of(asset));

    UserAccess.ActiveUser expiredPilot =
        new UserAccess.ActiveUser(
            inspectorId,
            Set.of(Roles.INSPECTOR),
            "CUSTOMER",
            "Pilot",
            "pilot@org.test",
            "LIC-01",
            "CLASS_B",
            plannedStart.minus(1, ChronoUnit.DAYS),
            "licenses/pilot.pdf");
    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(expiredPilot));

    Drone validDrone =
        Drone.createCustomerOwned(
            ownerCustomerId,
            "SN-01",
            "Mavic 3",
            "DJI",
            "REG-01",
            "key",
            plannedStart.plus(30, ChronoUnit.DAYS));
    ReflectionTestUtils.setField(validDrone, "id", droneId);
    when(drones.findWithLockByIdAndOwnerCustomerId(droneId, ownerCustomerId))
        .thenReturn(Optional.of(validDrone));

    CreateInspectionRequest request =
        new CreateInspectionRequest(assetId, inspectorId, droneId, plannedStart, plannedEnd);

    assertThatThrownBy(() -> service.create(ownerCustomerId, request))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                org.assertj.core.api.Assertions.assertThat(((BusinessException) ex).code())
                    .isEqualTo("PILOT_LICENSE_EXPIRED"));

    // Valid pilot, but drone registration expires before plannedStart
    UserAccess.ActiveUser validPilot =
        new UserAccess.ActiveUser(
            inspectorId,
            Set.of(Roles.INSPECTOR),
            "CUSTOMER",
            "Pilot",
            "pilot@org.test",
            "LIC-01",
            "CLASS_B",
            plannedStart.plus(30, ChronoUnit.DAYS),
            "licenses/pilot.pdf");
    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(validPilot));

    Drone expiringDrone =
        Drone.createCustomerOwned(
            ownerCustomerId,
            "SN-01",
            "Mavic 3",
            "DJI",
            "REG-01",
            "key",
            plannedStart.minus(1, ChronoUnit.DAYS));
    ReflectionTestUtils.setField(expiringDrone, "id", droneId);
    when(drones.findWithLockByIdAndOwnerCustomerId(droneId, ownerCustomerId))
        .thenReturn(Optional.of(expiringDrone));

    assertThatThrownBy(() -> service.create(ownerCustomerId, request))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                org.assertj.core.api.Assertions.assertThat(((BusinessException) ex).code())
                    .isEqualTo("DRONE_REGISTRATION_EXPIRED"));
  }

  @Test
  void rescheduleRejectsWhenLicenseOrDroneExpiresBeforeNewWindow() {
    Instant oldStart = Instant.now().plus(2, ChronoUnit.DAYS);
    Instant oldEnd = oldStart.plus(2, ChronoUnit.HOURS);
    Instant newStart = Instant.now().plus(60, ChronoUnit.DAYS);
    Instant newEnd = newStart.plus(2, ChronoUnit.HOURS);

    Inspection inspection =
        new Inspection(ownerCustomerId, assetId, inspectorId, droneId, oldStart, oldEnd);
    ReflectionTestUtils.setField(inspection, "id", inspectionId);
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(inspection));

    UserAccess.ActiveUser soonExpiringPilot =
        new UserAccess.ActiveUser(
            inspectorId,
            Set.of(Roles.INSPECTOR),
            "CUSTOMER",
            "Pilot",
            "pilot@org.test",
            "LIC-01",
            "CLASS_B",
            Instant.now().plus(15, ChronoUnit.DAYS),
            "licenses/pilot.pdf");
    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(soonExpiringPilot));

    assertThatThrownBy(() -> service.reschedule(ownerCustomerId, inspectionId, newStart, newEnd))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                org.assertj.core.api.Assertions.assertThat(((BusinessException) ex).code())
                    .isEqualTo("PILOT_LICENSE_EXPIRED"));

    // Valid pilot, expiring drone
    UserAccess.ActiveUser validPilot =
        new UserAccess.ActiveUser(
            inspectorId,
            Set.of(Roles.INSPECTOR),
            "CUSTOMER",
            "Pilot",
            "pilot@org.test",
            "LIC-01",
            "CLASS_B",
            Instant.now().plus(365, ChronoUnit.DAYS),
            "licenses/pilot.pdf");
    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(validPilot));

    Drone expiringDrone =
        Drone.createCustomerOwned(
            ownerCustomerId,
            "SN-01",
            "Mavic 3",
            "DJI",
            "REG-01",
            "key",
            Instant.now().plus(15, ChronoUnit.DAYS));
    ReflectionTestUtils.setField(expiringDrone, "id", droneId);
    when(drones.findById(droneId)).thenReturn(Optional.of(expiringDrone));

    assertThatThrownBy(() -> service.reschedule(ownerCustomerId, inspectionId, newStart, newEnd))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                org.assertj.core.api.Assertions.assertThat(((BusinessException) ex).code())
                    .isEqualTo("DRONE_REGISTRATION_EXPIRED"));
  }

  @Test
  void reassignRejectsExpiredDroneAndCrossPartnerDrone() {
    Instant start = Instant.now().plus(5, ChronoUnit.DAYS);
    Instant end = start.plus(2, ChronoUnit.HOURS);

    Inspection partnerInspection =
        new Inspection(ownerCustomerId, assetId, inspectorId, droneId, start, end);
    partnerInspection.markPartnerAssignment();
    ReflectionTestUtils.setField(partnerInspection, "id", inspectionId);

    UUID platformManagerId = UUID.randomUUID();
    when(userAccess.findActiveUser(platformManagerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(platformManagerId, Set.of(Roles.MANAGER), "Mgr")));
    when(inspections.findById(inspectionId)).thenReturn(Optional.of(partnerInspection));

    UserAccess.ActiveUser newPartner =
        new UserAccess.ActiveUser(
            partnerInspectorId,
            Set.of(Roles.INSPECTOR),
            "INSPECTOR",
            "Partner 2",
            "p2@test.local",
            "LIC-2",
            "CLASS_B",
            start.plus(60, ChronoUnit.DAYS),
            "licenses/pilot.pdf");
    when(userAccess.findActiveUser(partnerInspectorId)).thenReturn(Optional.of(newPartner));

    // Old partner's drone left unreplaced -> rejected
    Drone oldPartnerDrone =
        Drone.createInspectorOwned(
            inspectorId,
            "SN-OLD",
            "Mavic 3",
            "DJI",
            "REG-OLD",
            "key",
            start.plus(60, ChronoUnit.DAYS));
    ReflectionTestUtils.setField(oldPartnerDrone, "id", droneId);
    when(drones.findById(droneId)).thenReturn(Optional.of(oldPartnerDrone));

    assertThatThrownBy(
            () -> service.reassign(platformManagerId, inspectionId, partnerInspectorId, null))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                org.assertj.core.api.Assertions.assertThat(((BusinessException) ex).code())
                    .isEqualTo("INVALID_DRONE"));

    // New partner's drone with expired registration -> rejected
    UUID newDroneId = UUID.randomUUID();
    Drone expiredNewDrone =
        Drone.createInspectorOwned(
            partnerInspectorId,
            "SN-NEW",
            "Mavic 3",
            "DJI",
            "REG-NEW",
            "key",
            start.minus(1, ChronoUnit.DAYS));
    ReflectionTestUtils.setField(expiredNewDrone, "id", newDroneId);
    when(drones.findById(newDroneId)).thenReturn(Optional.of(expiredNewDrone));

    assertThatThrownBy(
            () -> service.reassign(platformManagerId, inspectionId, partnerInspectorId, newDroneId))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                org.assertj.core.api.Assertions.assertThat(((BusinessException) ex).code())
                    .isEqualTo("DRONE_REGISTRATION_EXPIRED"));
  }

  @Test
  void uploadFlightPermitRejectsInactiveOrExpiredDrone() {
    Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
    Instant end = start.plus(2, ChronoUnit.HOURS);

    Inspection inspection =
        new Inspection(ownerCustomerId, assetId, inspectorId, droneId, start, end);
    ReflectionTestUtils.setField(inspection, "id", inspectionId);
    when(inspections.findByIdAndOwnerCustomerId(inspectionId, ownerCustomerId))
        .thenReturn(Optional.of(inspection));

    UserAccess.ActiveUser validPilot =
        new UserAccess.ActiveUser(
            inspectorId,
            Set.of(Roles.INSPECTOR),
            "CUSTOMER",
            "Pilot",
            "pilot@org.test",
            "LIC-01",
            "CLASS_B",
            Instant.now().plus(90, ChronoUnit.DAYS),
            "licenses/pilot.pdf");
    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(validPilot));

    Drone maintenanceDrone =
        Drone.createCustomerOwned(
            ownerCustomerId,
            "SN-01",
            "Mavic 3",
            "DJI",
            "REG-01",
            "key",
            Instant.now().plus(90, ChronoUnit.DAYS));
    maintenanceDrone.changeServiceability(DroneServiceability.MAINTENANCE);
    ReflectionTestUtils.setField(maintenanceDrone, "id", droneId);
    when(drones.findById(droneId)).thenReturn(Optional.of(maintenanceDrone));

    assertThatThrownBy(
            () ->
                service.uploadFlightPermit(
                    ownerCustomerId,
                    inspectionId,
                    "permits/p.pdf",
                    "p.pdf",
                    null,
                    "application/pdf"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                org.assertj.core.api.Assertions.assertThat(((BusinessException) ex).code())
                    .isEqualTo("INVALID_DRONE"));

    maintenanceDrone.changeServiceability(DroneServiceability.ACTIVE);
    maintenanceDrone.updateRegistration("REG-01", "key", Instant.now().minus(1, ChronoUnit.DAYS));

    assertThatThrownBy(
            () ->
                service.uploadFlightPermit(
                    ownerCustomerId,
                    inspectionId,
                    "permits/p.pdf",
                    "p.pdf",
                    null,
                    "application/pdf"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                org.assertj.core.api.Assertions.assertThat(((BusinessException) ex).code())
                    .isEqualTo("DRONE_REGISTRATION_EXPIRED"));
  }
}

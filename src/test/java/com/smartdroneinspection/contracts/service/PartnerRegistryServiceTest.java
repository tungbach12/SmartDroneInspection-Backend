package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PartnerRegistryServiceTest {

  private DroneRepository drones;
  private UserAccess users;
  private PartnerRegistryService service;

  private final UUID managerId = UUID.randomUUID();
  private final UUID droneId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    drones = mock(DroneRepository.class);
    users = mock(UserAccess.class);
    service = new PartnerRegistryService(drones, users);

    when(users.findActiveUser(managerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(managerId, Set.of(Roles.MANAGER), null, "Manager")));
  }

  @Test
  void listsOnlyEligibleInspectorOwnedDronesFromMf1() {
    Drone orgDrone = mock(Drone.class);
    when(orgDrone.getInspectorOwnerId()).thenReturn(null);
    when(drones.findActivePartnerDrones()).thenReturn(List.of(orgDrone));

    assertThat(service.listDrones(managerId)).isEmpty();

    UUID inspectorId = UUID.randomUUID();
    Drone partnerDrone = mock(Drone.class);
    when(partnerDrone.getId()).thenReturn(droneId);
    when(partnerDrone.getInspectorOwnerId()).thenReturn(inspectorId);
    when(partnerDrone.isEligibleForAssignment()).thenReturn(true);
    when(partnerDrone.getSerialNumber()).thenReturn("SDI-001");
    when(partnerDrone.getModel()).thenReturn("M350");
    when(partnerDrone.getRegistrationExpiresAt()).thenReturn(null);
    when(drones.findActivePartnerDrones()).thenReturn(List.of(partnerDrone));
    when(users.findActiveUser(inspectorId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    inspectorId,
                    Set.of(Roles.INSPECTOR),
                    null,
                    "INSPECTOR_PARTNER",
                    "Pilot",
                    "pilot@example.test",
                    "LIC-001",
                    "CLASS_A",
                    Instant.now().plusSeconds(3600),
                    "licenses/pilot.pdf")));

    assertThat(service.listDrones(managerId))
        .singleElement()
        .satisfies(option -> assertThat(option.inspectorOwnerId()).isEqualTo(inspectorId));
  }

  @Test
  void listsOnlyActivePartnerInspectorsWithValidLicenses() {
    UUID inspectorId = UUID.randomUUID();
    UserAccess.ActiveUser inspector =
        new UserAccess.ActiveUser(
            inspectorId,
            Set.of(Roles.INSPECTOR),
            null,
            "INSPECTOR_PARTNER",
            "Pilot",
            "pilot@example.test",
            "LIC-001",
            "CLASS_A",
            Instant.now().plusSeconds(3600),
            "licenses/pilot.pdf");
    when(users.listActiveInspectorPartners()).thenReturn(List.of(inspector));

    assertThat(service.listInspectors(managerId))
        .hasSize(1)
        .first()
        .satisfies(option -> assertThat(option.id()).isEqualTo(inspectorId));
  }

  @Test
  void rejectsRegisteringDroneWithoutInspectorOwner() {
    Drone organizationDrone = mock(Drone.class);
    when(drones.findWithLockById(droneId)).thenReturn(Optional.of(organizationDrone));
    when(organizationDrone.getInspectorOwnerId()).thenReturn(null);

    assertThatThrownBy(() -> service.registerDrone(managerId, droneId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Drone must be approved");
  }

  @Test
  void rejectsNonManagerFromPartnerRegistryOperations() {
    UUID customerId = UUID.randomUUID();
    when(users.findActiveUser(customerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(customerId, Set.of(Roles.CUSTOMER), UUID.randomUUID())));

    assertThatThrownBy(() -> service.listInspectors(customerId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Only a Manager");
  }

  @Test
  void verifiesPartnerDroneAndInspectorEligibility() {
    UUID inspectorId = UUID.randomUUID();
    Drone drone = mock(Drone.class);
    when(drone.getId()).thenReturn(droneId);
    when(drone.getInspectorOwnerId()).thenReturn(inspectorId);
    when(drone.getServiceability()).thenReturn(DroneServiceability.ACTIVE);
    when(drone.getSerialNumber()).thenReturn("SDI-002");
    when(drone.getModel()).thenReturn("M350");
    when(drone.getRegistrationExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
    when(drones.findWithLockById(droneId)).thenReturn(Optional.of(drone));
    when(users.findActiveUser(inspectorId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    inspectorId,
                    Set.of(Roles.INSPECTOR),
                    null,
                    "INSPECTOR_PARTNER",
                    "Pilot",
                    "pilot@example.test",
                    "LIC-001",
                    "CLASS_A",
                    Instant.now().plusSeconds(3600),
                    "licenses/pilot.pdf")));

    PartnerRegistryService.DroneOption option = service.registerDrone(managerId, droneId);

    assertThat(option.id()).isEqualTo(droneId);
    assertThat(option.inspectorOwnerId()).isEqualTo(inspectorId);
  }

  @Test
  void rejectsClassALicenseWhenDroneRequiresClassBUnderDecree288() {
    UUID inspectorId = UUID.randomUUID();
    Drone heavyDrone = mock(Drone.class);
    when(heavyDrone.getId()).thenReturn(droneId);
    when(heavyDrone.getInspectorOwnerId()).thenReturn(inspectorId);
    when(heavyDrone.isEligibleForAssignment()).thenReturn(true);
    when(heavyDrone.requiresClassBLicense()).thenReturn(true);
    when(heavyDrone.getRegistrationExpiresAt()).thenReturn(Instant.now().plusSeconds(7200));
    when(drones.findWithLockById(droneId)).thenReturn(Optional.of(heavyDrone));
    when(drones.findById(droneId)).thenReturn(Optional.of(heavyDrone));

    when(users.findActiveUser(inspectorId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    inspectorId,
                    Set.of(Roles.INSPECTOR),
                    null,
                    "INSPECTOR_PARTNER",
                    "Pilot Class A",
                    "pilota@example.test",
                    "LIC-001",
                    "CLASS_A",
                    Instant.now().plusSeconds(7200),
                    "licenses/pilot.pdf")));

    assertThat(service.isEligibleDrone(droneId, inspectorId)).isFalse();
    assertThatThrownBy(() -> service.lockEligibleDrone(droneId, inspectorId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Class B pilot license");
  }
}

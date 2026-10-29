package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.contracts.domain.PartnerDrone;
import com.smartdroneinspection.contracts.domain.PartnerInspector;
import com.smartdroneinspection.contracts.repository.ContractPartnerDroneRepository;
import com.smartdroneinspection.contracts.repository.ContractPartnerInspectorRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PartnerRegistryServiceTest {

  private ContractPartnerInspectorRepository inspectors;
  private ContractPartnerDroneRepository partnerDrones;
  private DroneRepository drones;
  private UserAccess users;
  private PartnerRegistryService service;

  private final UUID managerId = UUID.randomUUID();
  private final UUID droneId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    inspectors = mock(ContractPartnerInspectorRepository.class);
    partnerDrones = mock(ContractPartnerDroneRepository.class);
    drones = mock(DroneRepository.class);
    users = mock(UserAccess.class);
    service = new PartnerRegistryService(inspectors, partnerDrones, drones, users);

    when(users.findActiveUser(managerId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(managerId, Set.of(Roles.MANAGER), null, "Manager")));
  }

  @Test
  void ignoresLegacyOrganizationDroneRowsWithoutPartnerInspectorOwner() {
    PartnerDrone legacyPartnerDrone = new PartnerDrone(droneId, null, managerId);
    Drone drone = mock(Drone.class);
    when(partnerDrones.findActive()).thenReturn(List.of(legacyPartnerDrone));
    when(drones.findById(droneId)).thenReturn(Optional.of(drone));

    assertThat(service.listDrones(managerId)).isEmpty();
  }

  @Test
  void listsOnlyActivePartnerInspectorsWithValidLicenses() {
    UUID inspectorId = UUID.randomUUID();
    when(inspectors.findActive()).thenReturn(List.of(new PartnerInspector(inspectorId, managerId)));
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
                    java.time.Instant.now().plusSeconds(3600),
                    "licenses/pilot.pdf")));

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
  void registersPartnerDroneAndUpdatesItsInspectorOwner() {
    UUID inspectorId = UUID.randomUUID();
    Drone drone = mock(Drone.class);
    when(drones.findWithLockById(droneId)).thenReturn(Optional.of(drone));
    when(drone.getInspectorOwnerId()).thenReturn(inspectorId);
    when(drone.getServiceability()).thenReturn(DroneServiceability.ACTIVE);
    when(drone.getRegistrationExpiresAt()).thenReturn(java.time.Instant.now().plusSeconds(3600));
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
                    java.time.Instant.now().plusSeconds(3600),
                    "licenses/pilot.pdf")));

    service.registerDrone(managerId, droneId);

    verify(partnerDrones).saveAndFlush(any(PartnerDrone.class));
  }

  @Test
  void deactivatesAndReactivatesPartnerInspector() {
    UUID inspectorId = UUID.randomUUID();
    PartnerInspector inspector = new PartnerInspector(inspectorId, managerId);
    when(inspectors.findWithLockByInspectorId(inspectorId)).thenReturn(Optional.of(inspector));

    service.setInspectorActive(managerId, inspectorId, false);
    assertThat(inspector.getStatus()).isEqualTo("INACTIVE");

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
                    java.time.Instant.now().plusSeconds(3600),
                    "licenses/pilot.pdf")));
    service.setInspectorActive(managerId, inspectorId, true);
    assertThat(inspector.getStatus()).isEqualTo("ACTIVE");
  }

  @Test
  void marksRegisteredPartnerDroneInactive() {
    UUID inspectorId = UUID.randomUUID();
    PartnerDrone drone = new PartnerDrone(droneId, inspectorId, managerId);
    when(partnerDrones.findWithLockByDroneId(droneId)).thenReturn(Optional.of(drone));

    service.setDroneActive(managerId, droneId, false);

    assertThat(drone.getStatus()).isEqualTo("INACTIVE");
  }
}

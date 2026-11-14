package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.service.Mf2InspectionAssignmentAdapter;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class Mf2InspectionAssignmentAdapterTest {

  private InspectionRepository inspections;
  private AssetRepository assets;
  private DroneRepository drones;
  private UserAccess users;
  private ApplicationEventPublisher events;
  private Mf2InspectionAssignmentAdapter adapter;

  private final UUID managerId = UUID.randomUUID();
  private final UUID organizationId = UUID.randomUUID();
  private final UUID assetId = UUID.randomUUID();
  private final UUID inspectorId = UUID.randomUUID();
  private final UUID droneId = UUID.randomUUID();
  private final Instant start = Instant.parse("2027-02-10T09:00:00Z");
  private final Instant end = Instant.parse("2027-02-10T10:00:00Z");

  @BeforeEach
  void setUp() {
    inspections = mock(InspectionRepository.class);
    assets = mock(AssetRepository.class);
    drones = mock(DroneRepository.class);
    users = mock(UserAccess.class);
    events = mock(ApplicationEventPublisher.class);
    adapter = new Mf2InspectionAssignmentAdapter(inspections, assets, drones, users, events);

    when(users.findActiveUser(managerId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(managerId, Set.of(Roles.MANAGER), "Manager")));
    when(users.findActiveUser(inspectorId))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(
                    inspectorId,
                    Set.of(Roles.INSPECTOR),
                    "INSPECTOR",
                    "Inspector",
                    "inspector@example.test",
                    "LIC-001",
                    "CLASS_A",
                    end.plusSeconds(3600),
                    "licenses/pilot.pdf")));
    Asset asset = mock(Asset.class);
    when(asset.getId()).thenReturn(assetId);
    when(asset.getStatus()).thenReturn(AssetStatus.ACTIVE);
    when(asset.getOwnerCustomerId()).thenReturn(organizationId);
    when(assets.findWithLockByIdAndOwnerCustomerId(assetId, organizationId))
        .thenReturn(Optional.of(asset));
    Drone drone = mock(Drone.class);
    when(drone.getId()).thenReturn(droneId);
    when(drone.getInspectorOwnerId()).thenReturn(inspectorId);
    when(drone.getServiceability()).thenReturn(DroneServiceability.ACTIVE);
    when(drone.getRegistrationNumber()).thenReturn("VN-RPAS-001");
    when(drone.getRegistrationFileKey()).thenReturn("registrations/drone.pdf");
    when(drone.getRegistrationExpiresAt()).thenReturn(end.plusSeconds(3600));
    when(drone.isEligibleForAssignment(start)).thenReturn(true);
    when(drone.hasKnownMtow()).thenReturn(true);
    when(drone.requiresClassBForSafety()).thenReturn(false);
    when(drones.findWithLockById(droneId)).thenReturn(Optional.of(drone));
    when(inspections.hasPlatformPartnerScheduleConflict(inspectorId, droneId, start, end))
        .thenReturn(false);
    when(inspections.saveAndFlush(any(Inspection.class)))
        .thenAnswer(
            invocation -> {
              Inspection inspection = invocation.getArgument(0);
              org.springframework.test.util.ReflectionTestUtils.setField(
                  inspection, "id", UUID.randomUUID());
              return inspection;
            });
  }

  @Test
  void createsPartnerInspectionWithRequestedTimeWindow() {
    UUID inspectionId =
        adapter.assign(organizationId, assetId, inspectorId, droneId, start, end, managerId);

    org.mockito.ArgumentCaptor<Inspection> captor =
        org.mockito.ArgumentCaptor.forClass(Inspection.class);
    verify(inspections).saveAndFlush(captor.capture());
    Inspection saved = captor.getValue();
    assertThat(inspectionId).isNotNull();
    assertThat(saved.getOwnerCustomerId()).isEqualTo(organizationId);
    assertThat(saved.getAssetId()).isEqualTo(assetId);
    assertThat(saved.getPlannedStartAt()).isEqualTo(start);
    assertThat(saved.getPlannedEndAt()).isEqualTo(end);
  }

  @Test
  void rejectsPlatformAdminFromCommercialInspectionAssignment() {
    when(users.findActiveUser(managerId))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(managerId, Set.of(Roles.ADMIN), "Admin")));

    assertThatThrownBy(
            () ->
                adapter.assign(
                    organizationId, assetId, inspectorId, droneId, start, end, managerId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Only an active Manager");
  }

  @Test
  void rejectsPartnerScheduleConflict() {
    when(inspections.hasPlatformPartnerScheduleConflict(inspectorId, droneId, start, end))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                adapter.assign(
                    organizationId, assetId, inspectorId, droneId, start, end, managerId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("conflicts with another active inspection");
  }

  @Test
  void rejectsAssignmentWhenDroneRequiresClassBAndInspectorOnlyHasClassA() {
    Drone heavyDrone = mock(Drone.class);
    when(heavyDrone.getId()).thenReturn(droneId);
    when(heavyDrone.getInspectorOwnerId()).thenReturn(inspectorId);
    when(heavyDrone.getServiceability()).thenReturn(DroneServiceability.ACTIVE);
    when(heavyDrone.getRegistrationNumber()).thenReturn("VN-RPAS-HEAVY");
    when(heavyDrone.getRegistrationFileKey()).thenReturn("registrations/heavy.pdf");
    when(heavyDrone.getRegistrationExpiresAt()).thenReturn(end.plusSeconds(3600));
    when(heavyDrone.isEligibleForAssignment(start)).thenReturn(true);
    when(heavyDrone.hasKnownMtow()).thenReturn(true);
    when(heavyDrone.requiresClassBForSafety()).thenReturn(true);
    when(heavyDrone.requiresClassBLicense()).thenReturn(true);
    when(drones.findWithLockById(droneId)).thenReturn(Optional.of(heavyDrone));

    assertThatThrownBy(
            () ->
                adapter.assign(
                    organizationId, assetId, inspectorId, droneId, start, end, managerId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Class B pilot license");
  }
}

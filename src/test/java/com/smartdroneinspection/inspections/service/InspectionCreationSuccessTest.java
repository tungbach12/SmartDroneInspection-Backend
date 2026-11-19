package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.api.dto.request.CreateInspectionRequest;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
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

class InspectionCreationSuccessTest {

  private static final UUID CUSTOMER_ID = UUID.randomUUID();
  private static final UUID INSPECTOR_ID = UUID.randomUUID();
  private static final UUID ASSET_ID = UUID.randomUUID();
  private static final UUID DRONE_ID = UUID.randomUUID();
  private static final UUID INSPECTION_ID = UUID.randomUUID();

  private InspectionRepository inspections;
  private AssetRepository assets;
  private DroneRepository drones;
  private UserAccess userAccess;
  private ApplicationEventPublisher events;
  private InspectionCreationService service;

  @BeforeEach
  void setUp() {
    inspections = mock(InspectionRepository.class);
    assets = mock(AssetRepository.class);
    drones = mock(DroneRepository.class);
    userAccess = mock(UserAccess.class);
    events = mock(ApplicationEventPublisher.class);
    service =
        new InspectionCreationService(
            inspections, assets, drones, userAccess, Optional.empty(), events);
    when(userAccess.findActiveUser(CUSTOMER_ID))
        .thenReturn(
            Optional.of(
                new UserAccess.ActiveUser(CUSTOMER_ID, Set.of(Roles.CUSTOMER), "Customer")));
  }

  @Test
  void createsScheduledInspectionForQualifiedCrew() {
    Instant plannedStart = Instant.now().plus(10, ChronoUnit.DAYS);
    Instant plannedEnd = plannedStart.plus(2, ChronoUnit.HOURS);
    Asset asset = asset();
    Drone drone = drone(plannedStart);
    when(assets.findByIdAndOwnerCustomerId(ASSET_ID, CUSTOMER_ID)).thenReturn(Optional.of(asset));
    when(userAccess.findActiveUser(INSPECTOR_ID)).thenReturn(Optional.of(inspector(plannedStart)));
    when(drones.findWithLockByIdAndOwnerCustomerId(DRONE_ID, CUSTOMER_ID))
        .thenReturn(Optional.of(drone));
    when(inspections.hasScheduleConflictByOwner(
            CUSTOMER_ID, INSPECTOR_ID, DRONE_ID, plannedStart, plannedEnd))
        .thenReturn(false);
    when(inspections.saveAndFlush(any(Inspection.class)))
        .thenAnswer(
            invocation -> {
              Inspection saved = invocation.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", INSPECTION_ID);
              return saved;
            });

    var response =
        service.create(
            CUSTOMER_ID,
            new CreateInspectionRequest(
                ASSET_ID, INSPECTOR_ID, DRONE_ID, plannedStart, plannedEnd));

    assertThat(response.id()).isEqualTo(INSPECTION_ID);
    assertThat(response.status()).isEqualTo("SCHEDULED");
    assertThat(response.assetName()).isEqualTo("Bridge");
    assertThat(response.inspectorId()).isEqualTo(INSPECTOR_ID);
    org.mockito.Mockito.verify(events)
        .publishEvent(
            any(com.smartdroneinspection.inspections.events.InspectionAssignedEvent.class));
  }

  @Test
  void readsInspectionWithinCustomerScope() {
    Inspection inspection = inspection();
    when(inspections.findByIdAndOwnerCustomerId(INSPECTION_ID, CUSTOMER_ID))
        .thenReturn(Optional.of(inspection));
    when(assets.findById(ASSET_ID)).thenReturn(Optional.of(asset()));

    var response = service.get(CUSTOMER_ID, INSPECTION_ID);

    assertThat(response.id()).isEqualTo(INSPECTION_ID);
    assertThat(response.assetName()).isEqualTo("Bridge");
  }

  private Asset asset() {
    Asset asset =
        new Asset(
            CUSTOMER_ID,
            UUID.randomUUID(),
            "A-1",
            "Bridge",
            null,
            "Loc",
            null,
            null,
            null,
            CUSTOMER_ID);
    ReflectionTestUtils.setField(asset, "id", ASSET_ID);
    return asset;
  }

  private Drone drone(Instant plannedStart) {
    Drone drone =
        Drone.createCustomerOwned(
            CUSTOMER_ID,
            "SN-01",
            "Mavic 3",
            "DJI",
            "REG-01",
            "key",
            plannedStart.plus(30, ChronoUnit.DAYS));
    ReflectionTestUtils.setField(drone, "id", DRONE_ID);
    ReflectionTestUtils.setField(drone, "payloadMetadata", "MTOW: 1.5 kg");
    return drone;
  }

  private UserAccess.ActiveUser inspector(Instant plannedStart) {
    return new UserAccess.ActiveUser(
        INSPECTOR_ID,
        Set.of(Roles.INSPECTOR),
        "Pilot",
        "pilot@org.test",
        "LIC-01",
        "CLASS_B",
        plannedStart.plus(30, ChronoUnit.DAYS),
        "licenses/pilot.pdf");
  }

  private Inspection inspection() {
    Inspection inspection =
        new Inspection(
            CUSTOMER_ID,
            ASSET_ID,
            INSPECTOR_ID,
            DRONE_ID,
            Instant.now().plus(10, ChronoUnit.DAYS),
            Instant.now().plus(10, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
    ReflectionTestUtils.setField(inspection, "id", INSPECTION_ID);
    return inspection;
  }
}

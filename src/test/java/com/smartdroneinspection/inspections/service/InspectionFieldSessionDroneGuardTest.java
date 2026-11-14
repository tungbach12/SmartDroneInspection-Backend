package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class InspectionFieldSessionDroneGuardTest {

  private InspectionRepository inspections;
  private FieldSessionRepository sessions;
  private UserAccess userAccess;
  private EntityManager entityManager;
  private DroneRepository drones;
  private InspectionFieldSessionService service;

  private final UUID ownerCustomerId = UUID.randomUUID();
  private final UUID inspectorId = UUID.randomUUID();
  private final UUID assetId = UUID.randomUUID();
  private final UUID droneId = UUID.randomUUID();
  private final UUID inspectionId = UUID.randomUUID();

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    inspections = mock(InspectionRepository.class);
    sessions = mock(FieldSessionRepository.class);
    userAccess = mock(UserAccess.class);
    entityManager = mock(EntityManager.class);
    drones = mock(DroneRepository.class);

    ObjectProvider<DroneRepository> dronesProvider = mock(ObjectProvider.class);
    when(dronesProvider.getIfAvailable()).thenReturn(drones);

    service =
        new InspectionFieldSessionService(
            inspections, sessions, userAccess, entityManager, null, null, dronesProvider);

    UserAccess.ActiveUser inspector =
        new UserAccess.ActiveUser(
            inspectorId,
            Set.of(Roles.INSPECTOR),
            "CUSTOMER",
            "Pilot",
            "pilot@org.test",
            "LIC-01",
            "CLASS_B",
            Instant.now().plus(60, ChronoUnit.DAYS),
            "licenses/pilot.pdf");
    when(userAccess.findActiveUser(inspectorId)).thenReturn(Optional.of(inspector));

    Inspection inspection =
        new Inspection(
            ownerCustomerId,
            assetId,
            inspectorId,
            droneId,
            Instant.now().plus(1, ChronoUnit.HOURS),
            Instant.now().plus(3, ChronoUnit.HOURS));
    inspection.recordFlightPermit("permits/permit.pdf", "permit.pdf", Instant.now());
    ReflectionTestUtils.setField(inspection, "id", inspectionId);

    when(inspections.findAssignedByInspectorWithLock(inspectionId, inspectorId))
        .thenReturn(Optional.of(inspection));
    when(sessions.findByInspectionIdOrderByCreatedAtAsc(inspectionId)).thenReturn(List.of());
  }

  @Test
  void startBlocksFieldSessionWhenAssignedDroneIsExpiredOrInactive() {
    Drone expiredDrone =
        Drone.createCustomerOwned(
            ownerCustomerId,
            "SN-01",
            "Mavic 3",
            "DJI",
            "REG-01",
            "key",
            Instant.now().minus(1, ChronoUnit.DAYS));
    ReflectionTestUtils.setField(expiredDrone, "id", droneId);
    when(drones.findById(droneId)).thenReturn(Optional.of(expiredDrone));

    assertThatThrownBy(() -> service.start(inspectorId, inspectionId, "Pre-flight checked"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).code())
                    .isEqualTo("DRONE_REGISTRATION_EXPIRED"));

    expiredDrone.updateRegistration("REG-01", "key", Instant.now().plus(30, ChronoUnit.DAYS));
    expiredDrone.changeServiceability(DroneServiceability.MAINTENANCE);

    assertThatThrownBy(() -> service.start(inspectorId, inspectionId, "Pre-flight checked"))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("INVALID_DRONE"));
  }
}

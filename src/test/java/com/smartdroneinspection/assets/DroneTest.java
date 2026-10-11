package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DroneTest {

  @Test
  void aNewDroneStartsActiveAndIsEligibleForAssignment() {
    Drone drone = new Drone(UUID.randomUUID(), "DJI-M350-RTK-001");

    assertThat(drone.getServiceability()).isEqualTo(DroneServiceability.ACTIVE);
    assertThat(drone.isEligibleForAssignment()).isTrue();
  }

  @Test
  void blankSerialNumberIsRejected() {
    UUID organizationId = UUID.randomUUID();

    assertThatThrownBy(() -> new Drone(organizationId, "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Drone serial number must not be blank");
  }

  @Test
  void suspendedOrRetiredDroneIsNotEligibleForAssignment() {
    Drone drone = new Drone(UUID.randomUUID(), "DJI-M350-RTK-002");

    drone.changeServiceability(DroneServiceability.SUSPENDED);
    assertThat(drone.isEligibleForAssignment()).isFalse();

    drone.changeServiceability(DroneServiceability.RETIRED);
    assertThat(drone.isEligibleForAssignment()).isFalse();

    drone.changeServiceability(DroneServiceability.MAINTENANCE);
    assertThat(drone.isEligibleForAssignment()).isFalse();
  }

  @Test
  void recordingMaintenanceKeepsTheDueDateAndNotes() {
    Drone drone = new Drone(UUID.randomUUID(), "DJI-M350-RTK-003");
    Instant servicedAt = Instant.now();
    Instant nextDueAt = servicedAt.plusSeconds(90L * 86_400L);

    drone.recordMaintenance(servicedAt, nextDueAt, "Replaced gimbal bearing");

    assertThat(drone.getLastMaintenanceAt()).isEqualTo(servicedAt);
    assertThat(drone.getNextMaintenanceAt()).isEqualTo(nextDueAt);
    assertThat(drone.getNotes()).isEqualTo("Replaced gimbal bearing");
  }
}

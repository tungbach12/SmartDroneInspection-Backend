package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DroneTest {

  @Test
  void aNewDroneStartsActiveButCannotBeAssignedUntilRegistered() {
    Drone drone = new Drone(UUID.randomUUID(), "DJI-M350-RTK-001");

    assertThat(drone.getServiceability()).isEqualTo(DroneServiceability.ACTIVE);
    assertThat(drone.isEligibleForAssignment()).isFalse();
  }

  @Test
  void blankSerialNumberIsRejected() {

    assertThatThrownBy(() -> new Drone(UUID.randomUUID(), "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Drone serial number must not be blank");
  }

  @Test
  void registrationExpiryMustBePresentAndFutureForAssignmentEligibility() {
    Drone missingExpiry =
        Drone.createInspectorOwned(
            UUID.randomUUID(),
            "DRN-NO-EXPIRY",
            "DJI Matrice 350",
            "DJI",
            "REG-01",
            "reg/key",
            null);
    Drone expired =
        Drone.createInspectorOwned(
            UUID.randomUUID(),
            "DRN-EXPIRED",
            "DJI Matrice 350",
            "DJI",
            "REG-02",
            "reg/key",
            java.time.Instant.now().minusSeconds(1));
    Drone current =
        Drone.createInspectorOwned(
            UUID.randomUUID(),
            "DRN-CURRENT",
            "DJI Matrice 350",
            "DJI",
            "REG-03",
            "reg/key",
            java.time.Instant.now().plusSeconds(3600));

    assertThat(missingExpiry.isEligibleForAssignment()).isFalse();
    assertThat(expired.isEligibleForAssignment()).isFalse();
    assertThat(current.isEligibleForAssignment()).isTrue();
  }

  @Test
  void suspendedOrRetiredDroneIsNotEligibleForAssignment() {
    Drone drone = new Drone(UUID.randomUUID(), "DJI-M350-RTK-002");

    drone.changeServiceability(DroneServiceability.SUSPENDED);
    assertThat(drone.isEligibleForAssignment()).isFalse();

    drone.changeServiceability(DroneServiceability.RETIRED);
    assertThat(drone.isEligibleForAssignment()).isFalse();
    assertThatThrownBy(() -> drone.changeServiceability(DroneServiceability.ACTIVE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Retired drones cannot change serviceability");
  }

  @Test
  void resolvesMtowKgAndClassBLicenseRequirementUnderDecree288() {
    Drone m350 =
        Drone.createInspectorOwned(
            UUID.randomUUID(),
            "DJI-M350-001",
            "DJI Matrice 350 RTK (MTOW 9.2 kg)",
            "DJI",
            "REG-001",
            "drones/reg.pdf",
            java.time.Instant.now().plusSeconds(3600));
    assertThat(m350.resolveMtowKg()).isEqualTo(9.2);
    assertThat(m350.requiresClassBLicense()).isTrue();
    assertThat(m350.resolveLegalCategory()).isEqualTo("CATEGORY_3");

    Drone m3t =
        Drone.createInspectorOwned(
            UUID.randomUUID(),
            "DJI-M3T-001",
            "DJI Mavic 3 Thermal (M3T) (MTOW 1.05 kg)",
            "DJI",
            "REG-002",
            "drones/reg2.pdf",
            java.time.Instant.now().plusSeconds(3600));
    assertThat(m3t.resolveMtowKg()).isEqualTo(1.05);
    assertThat(m3t.requiresClassBLicense()).isFalse();
    assertThat(m3t.resolveLegalCategory()).isEqualTo("CATEGORY_2");

    Drone cleanModel =
        Drone.createInspectorOwned(
            UUID.randomUUID(),
            "DJI-M350-CLEAN",
            "DJI Matrice 350 RTK",
            "DJI",
            "REG-003",
            "drones/reg3.pdf",
            java.time.Instant.now().plusSeconds(3600));
    cleanModel.applyStructuredSpecs(new BigDecimal("6.47"), 55, "20 MP");
    assertThat(cleanModel.resolveMtowKg()).isEqualTo(6.47);
    assertThat(cleanModel.getMaxFlightTimeMinutes()).isEqualTo(55);
    assertThat(cleanModel.getCameraSensor()).isEqualTo("20 MP");
    assertThat(cleanModel.resolveLegalCategory()).isEqualTo("CATEGORY_3");
  }
}

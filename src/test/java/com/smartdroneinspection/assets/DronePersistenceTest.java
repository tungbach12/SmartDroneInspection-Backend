package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.DroneRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DronePersistenceTest {

  @Autowired DroneRepository drones;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void roundTripsEveryMappedDroneColumn() {
    Fixture fixture = persistFixture();
    Instant registrationExpiresAt = Instant.parse("2027-08-01T10:00:00Z");

    Drone saved =
        drones.saveAndFlush(
            new Drone(
                fixture.ownerCustomerId(),
                "SN-ROUNDTRIP",
                "Mavic 3 Enterprise",
                "DJI",
                "{\"payloadGrams\": 500}",
                DroneServiceability.MAINTENANCE,
                "Awaiting blades",
                "REG-12345",
                "drones/reg/cert.pdf",
                registrationExpiresAt));
    saved.applyCapabilitiesAndDocuments(
        null,
        new java.math.BigDecimal("6000"),
        null,
        false,
        false,
        null,
        java.util.List.of(),
        java.util.List.of());
    saved = drones.saveAndFlush(saved);

    Drone reloaded =
        drones.findByIdAndOwnerCustomerId(saved.getId(), fixture.ownerCustomerId()).orElseThrow();

    assertThat(reloaded.getSerialNumber()).isEqualTo("SN-ROUNDTRIP");
    assertThat(reloaded.getModel()).isEqualTo("Mavic 3 Enterprise");
    assertThat(reloaded.getManufacturer()).isEqualTo("DJI");
    assertThat(reloaded.getPayloadMetadata()).isEqualTo("{\"payloadGrams\": 500}");
    assertThat(reloaded.getServiceability()).isEqualTo(DroneServiceability.MAINTENANCE);
    assertThat(reloaded.getNotes()).isEqualTo("Awaiting blades");
    assertThat(reloaded.getRegistrationNumber()).isEqualTo("REG-12345");
    assertThat(reloaded.getRegistrationFileKey()).isEqualTo("drones/reg/cert.pdf");
    assertThat(reloaded.getRegistrationExpiresAt()).isEqualTo(registrationExpiresAt);
    assertThat(reloaded.getMaxAltitudeMeters()).isEqualByComparingTo("6000");
    assertThat(reloaded.getCreatedAt()).isNotNull();
    assertThat(reloaded.getUpdatedAt()).isNotNull();
    assertThat(reloaded.getOwnerCustomerId()).isEqualTo(fixture.ownerCustomerId());
  }

  @Test
  void databaseRejectsNegativeMaximumAltitude() {
    Fixture fixture = persistFixture();
    Drone saved = drones.saveAndFlush(newDrone(fixture.ownerCustomerId(), "SN-NEGATIVE-ALTITUDE"));

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE drones SET max_altitude_meters = -1 WHERE id = ?", saved.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void returnsDroneOnlyForItsOwnOwner() {
    Fixture fixture = persistFixture();
    Drone saved = drones.saveAndFlush(newDrone(fixture.ownerCustomerId(), "SN-SCOPE"));

    assertThat(drones.findByIdAndOwnerCustomerId(saved.getId(), fixture.ownerCustomerId()))
        .isPresent();
    assertThat(drones.findByIdAndOwnerCustomerId(saved.getId(), UUID.randomUUID())).isEmpty();
  }

  @Test
  void rejectsTheSameSerialNumberAcrossOwners() {
    Fixture firstFixture = persistFixture();
    Fixture secondFixture = persistFixture();

    drones.saveAndFlush(newDrone(firstFixture.ownerCustomerId(), "SN-SHARED"));

    assertThatThrownBy(
            () -> drones.saveAndFlush(newDrone(secondFixture.ownerCustomerId(), "SN-SHARED")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsDuplicateSerialNumberWithinOneOwner() {
    Fixture fixture = persistFixture();
    drones.saveAndFlush(newDrone(fixture.ownerCustomerId(), "SN-DUP"));

    assertThatThrownBy(() -> drones.saveAndFlush(newDrone(fixture.ownerCustomerId(), "SN-DUP")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void selectionQueryReturnsOnlyActiveDronesOfTheOwnOwner() {
    Fixture fixture = persistFixture();
    Fixture otherFixture = persistFixture();

    Drone active = drones.saveAndFlush(newDrone(fixture.ownerCustomerId(), "SN-ACTIVE"));
    Drone suspended = drones.saveAndFlush(newDrone(fixture.ownerCustomerId(), "SN-SUSPENDED"));
    suspended.changeServiceability(DroneServiceability.SUSPENDED);
    drones.saveAndFlush(suspended);
    drones.saveAndFlush(newDrone(otherFixture.ownerCustomerId(), "SN-FOREIGN-ACTIVE"));

    assertThat(
            drones.findAllByOwnerCustomerIdAndServiceability(
                fixture.ownerCustomerId(), DroneServiceability.ACTIVE))
        .extracting(Drone::getId)
        .containsExactly(active.getId());
  }

  private Drone newDrone(UUID ownerCustomerId, String serialNumber) {
    return new Drone(
        ownerCustomerId, serialNumber, "Mavic 3", "DJI", "{}", DroneServiceability.ACTIVE, null);
  }

  private Fixture persistFixture() {
    UUID userId = persistUser();
    return new Fixture(userId, userId);
  }

  private UUID persistUser() {
    UUID userId = UUID.randomUUID();
    String email = "pilot-" + userId + "@example.test";
    jdbcTemplate.update(
        """
        INSERT INTO users
          (id, email, normalized_email, full_name, status, actor_zone)
        VALUES (?, ?, ?, 'Test Pilot', 'ACTIVE', 'INSPECTOR')
        """,
        userId,
        email,
        email);
    return userId;
  }

  private record Fixture(UUID ownerCustomerId, UUID userId) {}
}

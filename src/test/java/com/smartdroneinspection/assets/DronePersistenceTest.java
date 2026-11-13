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
                fixture.organizationId(),
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
        drones.findByIdAndOrganizationId(saved.getId(), fixture.organizationId()).orElseThrow();

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
    assertThat(reloaded.getOrganizationId()).isEqualTo(fixture.organizationId());
  }

  @Test
  void databaseRejectsNegativeMaximumAltitude() {
    Fixture fixture = persistFixture();
    Drone saved = drones.saveAndFlush(newDrone(fixture.organizationId(), "SN-NEGATIVE-ALTITUDE"));

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE drones SET max_altitude_meters = -1 WHERE id = ?", saved.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void returnsDroneOnlyForItsOwnOrganization() {
    Fixture fixture = persistFixture();
    Drone saved = drones.saveAndFlush(newDrone(fixture.organizationId(), "SN-SCOPE"));

    assertThat(drones.findByIdAndOrganizationId(saved.getId(), fixture.organizationId()))
        .isPresent();
    assertThat(drones.findByIdAndOrganizationId(saved.getId(), UUID.randomUUID())).isEmpty();
  }

  @Test
  void rejectsTheSameSerialNumberAcrossOrganizations() {
    UUID firstOrganizationId = persistOrganization();
    UUID secondOrganizationId = persistOrganization();
    persistUser(firstOrganizationId);
    persistUser(secondOrganizationId);

    drones.saveAndFlush(newDrone(firstOrganizationId, "SN-SHARED"));

    assertThatThrownBy(() -> drones.saveAndFlush(newDrone(secondOrganizationId, "SN-SHARED")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsDuplicateSerialNumberWithinOneOrganization() {
    Fixture fixture = persistFixture();
    drones.saveAndFlush(newDrone(fixture.organizationId(), "SN-DUP"));

    assertThatThrownBy(() -> drones.saveAndFlush(newDrone(fixture.organizationId(), "SN-DUP")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void selectionQueryReturnsOnlyActiveDronesOfTheOwnOrganization() {
    UUID organizationId = persistOrganization();
    UUID otherOrganizationId = persistOrganization();
    persistUser(organizationId);
    persistUser(otherOrganizationId);

    Drone active = drones.saveAndFlush(newDrone(organizationId, "SN-ACTIVE"));
    Drone suspended = drones.saveAndFlush(newDrone(organizationId, "SN-SUSPENDED"));
    suspended.changeServiceability(DroneServiceability.SUSPENDED);
    drones.saveAndFlush(suspended);
    drones.saveAndFlush(newDrone(otherOrganizationId, "SN-FOREIGN-ACTIVE"));

    assertThat(
            drones.findAllByOrganizationIdAndServiceability(
                organizationId, DroneServiceability.ACTIVE))
        .extracting(Drone::getId)
        .containsExactly(active.getId());
  }

  private Drone newDrone(UUID organizationId, String serialNumber) {
    return new Drone(
        organizationId, serialNumber, "Mavic 3", "DJI", "{}", DroneServiceability.ACTIVE, null);
  }

  private Fixture persistFixture() {
    UUID organizationId = persistOrganization();
    UUID userId = persistUser(organizationId);
    return new Fixture(organizationId, userId);
  }

  private UUID persistOrganization() {
    UUID organizationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, display_name, registration_code, status)
        VALUES (?, ?, ?, 'ACTIVE')
        """,
        organizationId,
        "Org " + organizationId,
        "ORG-" + organizationId);
    return organizationId;
  }

  private UUID persistUser(UUID organizationId) {
    UUID userId = UUID.randomUUID();
    String email = "pilot-" + userId + "@example.test";
    jdbcTemplate.update(
        """
        INSERT INTO users
          (id, email, normalized_email, full_name, status, actor_zone, organization_id)
        VALUES (?, ?, ?, 'Test Pilot', 'ACTIVE', 'CUSTOMER_ORGANIZATION', ?)
        """,
        userId,
        email,
        email,
        organizationId);
    return userId;
  }

  private record Fixture(UUID organizationId, UUID userId) {}
}

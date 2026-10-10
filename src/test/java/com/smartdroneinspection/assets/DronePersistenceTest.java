package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.DroneDocument;
import com.smartdroneinspection.assets.domain.enums.DroneDocumentStatus;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.DroneDocumentRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Persistence behaviour of the {@code drones} and {@code drone_documents} mappings. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DronePersistenceTest {

  private static final String CHECKSUM = "a".repeat(64);

  @Autowired DroneRepository drones;
  @Autowired DroneDocumentRepository droneDocuments;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void roundTripsEveryMappedDroneColumn() {
    Fixture fixture = persistFixture();
    Instant lastMaintenanceAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant nextMaintenanceAt = Instant.parse("2026-09-01T10:00:00Z");

    Drone saved =
        drones.saveAndFlush(
            new Drone(
                fixture.organizationId(),
                "SN-ROUNDTRIP",
                "Mavic 3 Enterprise",
                "DJI",
                "{\"payloadGrams\": 500}",
                DroneServiceability.MAINTENANCE,
                lastMaintenanceAt,
                nextMaintenanceAt,
                "Awaiting blades"));

    Drone reloaded =
        drones.findByIdAndOrganizationId(saved.getId(), fixture.organizationId()).orElseThrow();

    assertThat(reloaded.getSerialNumber()).isEqualTo("SN-ROUNDTRIP");
    assertThat(reloaded.getModel()).isEqualTo("Mavic 3 Enterprise");
    assertThat(reloaded.getManufacturer()).isEqualTo("DJI");
    assertThat(reloaded.getPayloadMetadata()).isEqualTo("{\"payloadGrams\": 500}");
    assertThat(reloaded.getServiceability()).isEqualTo(DroneServiceability.MAINTENANCE);
    assertThat(reloaded.getLastMaintenanceAt()).isEqualTo(lastMaintenanceAt);
    assertThat(reloaded.getNextMaintenanceAt()).isEqualTo(nextMaintenanceAt);
    assertThat(reloaded.getNotes()).isEqualTo("Awaiting blades");
    assertThat(reloaded.getCreatedAt()).isNotNull();
    assertThat(reloaded.getUpdatedAt()).isNotNull();
    assertThat(reloaded.getOrganizationId()).isEqualTo(fixture.organizationId());
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
  void allowsTheSameSerialNumberInDifferentOrganizations() {
    UUID firstOrganizationId = persistOrganization();
    UUID secondOrganizationId = persistOrganization();
    persistUser(firstOrganizationId);
    persistUser(secondOrganizationId);

    drones.saveAndFlush(newDrone(firstOrganizationId, "SN-SHARED"));
    drones.saveAndFlush(newDrone(secondOrganizationId, "SN-SHARED"));

    assertThat(
            drones.findAllByOrganizationIdAndServiceability(
                firstOrganizationId, DroneServiceability.ACTIVE))
        .hasSize(1);
    assertThat(
            drones.findAllByOrganizationIdAndServiceability(
                secondOrganizationId, DroneServiceability.ACTIVE))
        .hasSize(1);
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

  @Test
  void roundTripsEveryMappedDroneDocumentColumn() {
    Fixture fixture = persistFixture();
    Drone drone = drones.saveAndFlush(newDrone(fixture.organizationId(), "SN-DOC"));
    Instant reviewedAt = Instant.parse("2026-08-02T09:00:00Z");

    DroneDocument document =
        droneDocuments.saveAndFlush(
            new DroneDocument(
                drone,
                "AIRWORTHINESS_CERTIFICATE",
                "Civil Aviation Authority",
                "AWC-2026-001",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"),
                DroneDocumentStatus.ACTIVE,
                "drones/" + drone.getId() + "/documents/awc.pdf",
                CHECKSUM,
                fixture.userId(),
                fixture.userId(),
                reviewedAt));

    DroneDocument reloaded = droneDocuments.findById(document.getId()).orElseThrow();

    assertThat(reloaded.getDrone().getId()).isEqualTo(drone.getId());
    assertThat(reloaded.getDocumentType()).isEqualTo("AIRWORTHINESS_CERTIFICATE");
    assertThat(reloaded.getIssuer()).isEqualTo("Civil Aviation Authority");
    assertThat(reloaded.getDocumentReference()).isEqualTo("AWC-2026-001");
    assertThat(reloaded.getValidFrom()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(reloaded.getValidUntil()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
    assertThat(reloaded.getStatus()).isEqualTo(DroneDocumentStatus.ACTIVE);
    assertThat(reloaded.getObjectKey()).isEqualTo("drones/" + drone.getId() + "/documents/awc.pdf");
    assertThat(reloaded.getChecksumSha256()).isEqualTo(CHECKSUM);
    assertThat(reloaded.getUploadedByUserId()).isEqualTo(fixture.userId());
    assertThat(reloaded.getReviewedByUserId()).isEqualTo(fixture.userId());
    assertThat(reloaded.getReviewedAt()).isEqualTo(reviewedAt);
    assertThat(reloaded.getCreatedAt()).isNotNull();
  }

  @Test
  void rejectsDuplicateDocumentObjectKey() {
    Fixture fixture = persistFixture();
    Drone drone = drones.saveAndFlush(newDrone(fixture.organizationId(), "SN-KEY"));
    droneDocuments.saveAndFlush(
        newPendingDocument(drone, fixture.userId(), "drones/shared/awc.pdf", "b".repeat(64)));

    assertThatThrownBy(
            () ->
                droneDocuments.saveAndFlush(
                    newPendingDocument(
                        drone, fixture.userId(), "drones/shared/awc.pdf", "c".repeat(64))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsAValidityWindowThatEndsBeforeItStarts() {
    Fixture fixture = persistFixture();
    Drone drone = drones.saveAndFlush(newDrone(fixture.organizationId(), "SN-WINDOW"));

    assertThatThrownBy(
            () ->
                new DroneDocument(
                    drone,
                    "FLIGHT_PERMIT",
                    null,
                    null,
                    Instant.parse("2027-01-01T00:00:00Z"),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    DroneDocumentStatus.PENDING_REVIEW,
                    "drones/" + drone.getId() + "/documents/permit.pdf",
                    CHECKSUM,
                    fixture.userId()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAChecksumThatIsNotSixtyFourHexCharacters() {
    Fixture fixture = persistFixture();
    Drone drone = drones.saveAndFlush(newDrone(fixture.organizationId(), "SN-CHECKSUM"));

    assertThatThrownBy(
            () -> newPendingDocument(drone, fixture.userId(), "drones/x/bad.pdf", "not-a-checksum"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void listsOnlyTheDocumentsOfTheRequestedDrone() {
    Fixture fixture = persistFixture();
    Drone first = drones.saveAndFlush(newDrone(fixture.organizationId(), "SN-DOC-1"));
    Drone second = drones.saveAndFlush(newDrone(fixture.organizationId(), "SN-DOC-2"));
    DroneDocument expected =
        droneDocuments.saveAndFlush(
            newPendingDocument(first, fixture.userId(), "drones/first/a.pdf", CHECKSUM));
    droneDocuments.saveAndFlush(
        newPendingDocument(second, fixture.userId(), "drones/second/b.pdf", "d".repeat(64)));

    assertThat(droneDocuments.findAllByDrone_IdOrderByCreatedAtDesc(first.getId()))
        .extracting(DroneDocument::getId)
        .containsExactly(expected.getId());
  }

  private DroneDocument newPendingDocument(
      Drone drone, UUID userId, String objectKey, String checksum) {
    return new DroneDocument(
        drone,
        "FLIGHT_PERMIT",
        null,
        null,
        null,
        null,
        DroneDocumentStatus.PENDING_REVIEW,
        objectKey,
        checksum,
        userId);
  }

  private Drone newDrone(UUID organizationId, String serialNumber) {
    return new Drone(
        organizationId,
        serialNumber,
        "Mavic 3",
        "DJI",
        null,
        DroneServiceability.ACTIVE,
        null,
        null,
        null);
  }

  private Fixture persistFixture() {
    UUID organizationId = persistOrganization();
    return new Fixture(organizationId, persistUser(organizationId));
  }

  private UUID persistOrganization() {
    UUID organizationId = UUID.randomUUID();
    String suffix = organizationId.toString();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        organizationId,
        "Organization " + suffix,
        "Organization " + suffix,
        "ORG-" + suffix);
    return organizationId;
  }

  private UUID persistUser(UUID organizationId) {
    UUID userId = UUID.randomUUID();
    String email = "drone-user-" + userId + "@example.test";
    jdbcTemplate.update(
        """
        INSERT INTO users (
          id, email, normalized_email, full_name, status, actor_zone,
          organization_id, auth_version, failed_login_count, must_change_password
        ) VALUES (?, ?, ?, ?, 'ACTIVE', 'CUSTOMER_ORGANIZATION', ?, 0, 0, FALSE)
        """,
        userId,
        email,
        email,
        "Drone User",
        organizationId);
    return userId;
  }

  private record Fixture(UUID organizationId, UUID userId) {}
}

package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.TestcontainersConfiguration;
import com.smartdroneinspection.assets.domain.DroneDocument;
import com.smartdroneinspection.assets.domain.enums.DroneDocumentStatus;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Persistence contract for V25 Drone documents. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DroneDocumentPersistenceTest {

  @PersistenceContext EntityManager entityManager;

  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbcTemplate;

  private UUID organizationId;
  private UUID uploaderId;
  private UUID reviewerId;
  private UUID droneId;

  @BeforeEach
  void setUp() {
    organizationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO organizations (id, legal_name, display_name, registration_code, timezone, status)
        VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', 'ACTIVE')
        """,
        organizationId,
        "Drone document organization " + organizationId,
        "Drone document organization " + organizationId,
        "ORG-" + organizationId);
    uploaderId = createUser("uploader");
    reviewerId = createUser("reviewer");
    droneId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO drones (id, organization_id, serial_number, serviceability)
        VALUES (?, ?, ?, 'ACTIVE')
        """,
        droneId,
        organizationId,
        "DRONE-" + droneId);
  }

  @Test
  void reviewedDroneDocumentRoundTripsItsValidityStorageAndReviewAttribution() {
    UUID documentId = UUID.randomUUID();
    Instant validFrom = Instant.parse("2026-01-01T00:00:00Z");
    Instant validUntil = Instant.parse("2027-01-01T00:00:00Z");
    Instant reviewedAt = Instant.parse("2026-02-01T12:30:00Z");
    String objectKey = "drone-documents/" + documentId;
    String checksum = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    jdbcTemplate.update(
        """
        INSERT INTO drone_documents
            (id, drone_id, document_type, issuer, document_reference, valid_from, valid_until,
             status, object_key, checksum_sha256, uploaded_by_user_id, reviewed_by_user_id,
             reviewed_at)
        VALUES (?, ?, 'AIRWORTHINESS_CERTIFICATE', 'Aviation Authority', 'CERT-123', ?, ?,
                'ACTIVE', ?, ?, ?, ?, ?)
        """,
        documentId,
        droneId,
        Timestamp.from(validFrom),
        Timestamp.from(validUntil),
        objectKey,
        checksum,
        uploaderId,
        reviewerId,
        Timestamp.from(reviewedAt));
    entityManager.flush();
    entityManager.clear();

    DroneDocument document = entityManager.find(DroneDocument.class, documentId);

    assertThat(document).isNotNull();
    assertThat(document.getDroneId()).isEqualTo(droneId);
    assertThat(document.getDocumentType()).isEqualTo("AIRWORTHINESS_CERTIFICATE");
    assertThat(document.getIssuer()).isEqualTo("Aviation Authority");
    assertThat(document.getDocumentReference()).isEqualTo("CERT-123");
    assertThat(document.getValidFrom()).isEqualTo(validFrom);
    assertThat(document.getValidUntil()).isEqualTo(validUntil);
    assertThat(document.getStatus()).isEqualTo(DroneDocumentStatus.ACTIVE);
    assertThat(document.getObjectKey()).isEqualTo(objectKey);
    assertThat(document.getChecksumSha256()).isEqualTo(checksum);
    assertThat(document.getUploadedByUserId()).isEqualTo(uploaderId);
    assertThat(document.getReviewedByUserId()).isEqualTo(reviewerId);
    assertThat(document.getReviewedAt()).isEqualTo(reviewedAt);
    assertThat(document.getCreatedAt()).isNotNull();
  }

  @Test
  void missingReviewAttributionRemainsNull() {
    UUID documentId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO drone_documents
            (id, drone_id, document_type, status, object_key, checksum_sha256, uploaded_by_user_id)
        VALUES (?, ?, 'AIRWORTHINESS_CERTIFICATE', 'PENDING_REVIEW', ?, ?, ?)
        """,
        documentId,
        droneId,
        "drone-documents/" + documentId,
        "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
        uploaderId);
    entityManager.flush();
    entityManager.clear();

    DroneDocument document = entityManager.find(DroneDocument.class, documentId);

    assertThat(document.getReviewedByUserId()).isNull();
    assertThat(document.getReviewedAt()).isNull();
  }

  private UUID createUser(String label) {
    User user =
        new User(
            label + "-" + UUID.randomUUID() + "@example.test",
            label,
            null,
            UserStatus.ACTIVE,
            ActorZone.CUSTOMER_ORGANIZATION,
            organizationId);
    user.addRole(UserRole.ORG_ADMIN);
    return users.saveAndFlush(user).getId();
  }
}

package com.smartdroneinspection.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.workforce.config.WorkforceProperties;
import com.smartdroneinspection.workforce.domain.WorkforceCredential;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The expiring-soon horizon is an operational threshold, so it must come from configuration rather
 * than a literal in the entity.
 */
class WorkforceCredentialExpiryWindowTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final UUID SUBJECT_ID = UUID.randomUUID();

  /**
   * The default horizon is a product decision, so it is pinned here: application.yml, the
   * WorkforceProperties default and the entity fallback must all agree, and a silent divergence
   * between them would change what customers see without any test failing.
   */
  @Test
  void theDefaultHorizonIsSixtyDays() {
    assertThat(WorkforceCredential.DEFAULT_EXPIRING_SOON_WINDOW).isEqualTo(Duration.ofDays(60));
    assertThat(new WorkforceProperties().getCredentialExpiringSoonWindow())
        .isEqualTo(Duration.ofDays(60));
  }

  @Test
  void aCredentialExpiringInsideTheWindowIsReportedAsExpiringSoon() {
    Instant now = Instant.parse("2026-10-10T00:00:00Z");
    Instant expiresInTenDays = now.plus(Duration.ofDays(10));

    assertThat(statusAt(expiresInTenDays, Duration.ofDays(30), now))
        .isEqualTo(WorkforceCredential.Status.EXPIRING_SOON);
  }

  @Test
  void aCredentialExpiringOutsideTheWindowStaysActive() {
    Instant now = Instant.parse("2026-10-10T00:00:00Z");
    Instant expiresInNinetyDays = now.plus(Duration.ofDays(90));

    assertThat(statusAt(expiresInNinetyDays, Duration.ofDays(30), now))
        .isEqualTo(WorkforceCredential.Status.ACTIVE);
  }

  /**
   * The configured window must be what decides, not a literal compiled into the entity: the same
   * credential changes reported status purely because the configuration changed.
   */
  @Test
  void theConfiguredWindowDecidesRatherThanAnyLiteral() {
    Instant now = Instant.parse("2026-10-10T00:00:00Z");
    Instant expiresInNinetyDays = now.plus(Duration.ofDays(90));

    assertThat(statusAt(expiresInNinetyDays, Duration.ofDays(120), now))
        .isEqualTo(WorkforceCredential.Status.EXPIRING_SOON);
    assertThat(statusAt(expiresInNinetyDays, Duration.ofDays(30), now))
        .isEqualTo(WorkforceCredential.Status.ACTIVE);
  }

  @Test
  void aRejectedCredentialIsNeverResurrectedByItsExpiryDate() {
    Instant now = Instant.parse("2026-10-10T00:00:00Z");

    assertThat(statusAt(now.plus(Duration.ofDays(5)), Duration.ofDays(30), now, rejected()))
        .isEqualTo(WorkforceCredential.Status.REJECTED);
  }

  private static java.util.function.Consumer<WorkforceCredential> rejected() {
    return credential ->
        credential.review(
            WorkforceCredential.Status.REJECTED,
            SUBJECT_ID,
            Instant.parse("2026-10-01T00:00:00Z"),
            "not acceptable");
  }

  private static WorkforceCredential.Status statusAt(
      Instant expiresAt, Duration window, Instant at) {
    return statusAt(expiresAt, window, at, credential -> {});
  }

  private static WorkforceCredential.Status statusAt(
      Instant expiresAt,
      Duration window,
      Instant at,
      java.util.function.Consumer<WorkforceCredential> review) {
    WorkforceCredential credential =
        new WorkforceCredential(
            ORGANIZATION_ID,
            SUBJECT_ID,
            "QUALIFICATION",
            "Issuer",
            "REF-1",
            at.minus(Duration.ofDays(365)),
            expiresAt,
            null,
            null,
            null,
            null);

    // A new credential is PENDING_REVIEW; only a reviewed one can be reported ACTIVE, so the
    // window question is asked of a reviewed credential.
    credential.review(WorkforceCredential.Status.ACTIVE, SUBJECT_ID, at, "recorded by the tenant");
    review.accept(credential);

    return credential.currentStatus(at, window);
  }
}

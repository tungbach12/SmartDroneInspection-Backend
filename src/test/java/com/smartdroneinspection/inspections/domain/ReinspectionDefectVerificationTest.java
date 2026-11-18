package com.smartdroneinspection.inspections.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.inspections.domain.enums.DefectResolutionStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReinspectionDefectVerificationTest {

  private static final Duration VALIDITY = Duration.ofDays(90);

  @Test
  @DisplayName("A RESOLVED verdict is stamped with verified_at and valid_until")
  void resolvedStampsValidityWindow() {
    ReinspectionDefectVerification verification =
        new ReinspectionDefectVerification(UUID.randomUUID(), UUID.randomUUID());
    Instant before = Instant.now();

    verification.updateVerification(
        DefectResolutionStatus.RESOLVED, UUID.randomUUID(), "Sealed", VALIDITY);

    assertThat(verification.getVerifiedAt()).isNotNull().isAfterOrEqualTo(before);
    assertThat(verification.getValidUntil()).isEqualTo(verification.getVerifiedAt().plus(VALIDITY));
    assertThat(verification.isResolutionExpired(Instant.now())).isFalse();
  }

  @Test
  @DisplayName("An expired RESOLVED verdict is reported as expired")
  void expiredResolvedVerdictIsFlagged() {
    ReinspectionDefectVerification verification =
        new ReinspectionDefectVerification(UUID.randomUUID(), UUID.randomUUID());
    verification.updateVerification(
        DefectResolutionStatus.RESOLVED, UUID.randomUUID(), "Sealed", Duration.ofMinutes(1));

    // Just past the window.
    assertThat(verification.isResolutionExpired(verification.getValidUntil().plusSeconds(1)))
        .isTrue();
    // Exactly at the boundary the verdict is still valid.
    assertThat(verification.isResolutionExpired(verification.getValidUntil())).isFalse();
  }

  @Test
  @DisplayName("A non-RESOLVED verdict carries no validity window")
  void unresolvedHasNoValidityWindow() {
    ReinspectionDefectVerification verification =
        new ReinspectionDefectVerification(UUID.randomUUID(), UUID.randomUUID());

    verification.updateVerification(
        DefectResolutionStatus.UNRESOLVED, null, "Still cracked", VALIDITY);

    assertThat(verification.getVerifiedAt()).isNull();
    assertThat(verification.getValidUntil()).isNull();
    assertThat(verification.isResolutionExpired(Instant.now().plus(Duration.ofDays(365))))
        .isFalse();
  }

  @Test
  @DisplayName("Without a configured duration a RESOLVED verdict gets no window")
  void resolvedWithoutDurationHasNoWindow() {
    ReinspectionDefectVerification verification =
        new ReinspectionDefectVerification(UUID.randomUUID(), UUID.randomUUID());

    verification.updateVerification(
        DefectResolutionStatus.RESOLVED, UUID.randomUUID(), "Sealed", null);

    assertThat(verification.getValidUntil()).isNull();
    assertThat(verification.isResolutionExpired(Instant.now().plus(Duration.ofDays(3650))))
        .isFalse();
  }
}

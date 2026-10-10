package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.assets.domain.FlightPermit;
import com.smartdroneinspection.assets.domain.enums.FlightPermitStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlightPermitTest {

  @Test
  void aNewRecordIsAnApplicationAndAuthorizesNothingYet() {
    FlightPermit permit =
        new FlightPermit(UUID.randomUUID(), UUID.randomUUID(), "UAV Flight Permit", null, null);

    assertThat(permit.getStatus()).isEqualTo(FlightPermitStatus.APPLICATION);
    assertThat(permit.authorizesAt(Instant.now())).isFalse();
  }

  @Test
  void aGrantedPermitAuthorizesOnlyInsideItsValidityWindow() {
    FlightPermit permit =
        new FlightPermit(UUID.randomUUID(), UUID.randomUUID(), "UAV Flight Permit", null, null);
    Instant from = Instant.now();
    Instant until = from.plusSeconds(30L * 86_400L);

    permit.grant("GP-2026-0042", "General Staff of the Army", from, until);

    assertThat(permit.getStatus()).isEqualTo(FlightPermitStatus.ACTIVE);
    assertThat(permit.getPermitReference()).isEqualTo("GP-2026-0042");
    assertThat(permit.authorizesAt(from.plusSeconds(3600))).isTrue();
  }

  @Test
  void aPermitOutsideOrPastItsWindowDoesNotAuthorize() {
    FlightPermit permit =
        new FlightPermit(UUID.randomUUID(), UUID.randomUUID(), "UAV Flight Permit", null, null);
    Instant from = Instant.now();
    Instant until = from.plusSeconds(30L * 86_400L);
    permit.grant("GP-2026-0043", "General Staff of the Army", from, until);

    assertThat(permit.authorizesAt(from.minusSeconds(60))).isFalse();
    assertThat(permit.authorizesAt(until.plusSeconds(60))).isFalse();
  }

  @Test
  void notApplicableIsADistinctStateFromAPendingApplication() {
    FlightPermit pending =
        new FlightPermit(UUID.randomUUID(), UUID.randomUUID(), "UAV Flight Permit", null, null);
    FlightPermit exempt =
        new FlightPermit(UUID.randomUUID(), UUID.randomUUID(), "UAV Flight Permit", null, null);

    exempt.markNotApplicable("Civil airspace below 120 m, no restricted zone involved");

    assertThat(pending.authorizesAt(Instant.now())).isFalse();
    assertThat(exempt.authorizesAt(Instant.now())).isTrue();
    assertThat(exempt.getStatus()).isEqualTo(FlightPermitStatus.NOT_APPLICABLE);
  }

  @Test
  void aRejectedOrRevokedPermitNeverAuthorizes() {
    FlightPermit rejected =
        new FlightPermit(
            UUID.randomUUID(), UUID.randomUUID(), "UAV Flight Permit", "GP-9", "Authority");
    rejected.refuse(FlightPermitStatus.REJECTED, "Airspace restriction", Instant.now());

    FlightPermit revoked =
        new FlightPermit(
            UUID.randomUUID(), UUID.randomUUID(), "UAV Flight Permit", "GP-8", "Authority");
    revoked.refuse(FlightPermitStatus.REVOKED, "Safety incident", Instant.now());

    assertThat(rejected.authorizesAt(Instant.now())).isFalse();
    assertThat(revoked.authorizesAt(Instant.now())).isFalse();
  }

  @Test
  void anInvertedValidityWindowIsRejected() {
    FlightPermit permit =
        new FlightPermit(UUID.randomUUID(), UUID.randomUUID(), "UAV Flight Permit", null, null);
    Instant from = Instant.now();

    assertThatThrownBy(() -> permit.defineValidity(from, from.minusSeconds(60)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Permit validity must end after it starts");
  }

  @Test
  void grantingWithoutARefenceIsRejected() {
    FlightPermit permit =
        new FlightPermit(UUID.randomUUID(), UUID.randomUUID(), "UAV Flight Permit", null, null);

    assertThatThrownBy(() -> permit.grant("  ", "Authority", Instant.now(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Granted permit requires a reference");
  }
}

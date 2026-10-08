package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.assets.domain.AssetPairAssignment;
import com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus;
import com.smartdroneinspection.assets.domain.enums.AssignmentResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetPairAssignmentTest {

  UUID organizationId;
  UUID assetId;
  UUID inspectorId;
  UUID droneId;
  UUID assignerId;
  Instant validFrom;
  AssetPairAssignment pairing;

  @BeforeEach
  void setUp() {
    organizationId = UUID.randomUUID();
    assetId = UUID.randomUUID();
    inspectorId = UUID.randomUUID();
    droneId = UUID.randomUUID();
    assignerId = UUID.randomUUID();
    validFrom = Instant.now();
    pairing =
        new AssetPairAssignment(
            organizationId, assetId, inspectorId, droneId, validFrom, assignerId);
  }

  @Test
  void aNewPairingIsADraftThatHasNotAnswered() {
    assertThat(pairing.getStatus()).isEqualTo(AssetPairAssignmentStatus.DRAFT);
    assertThat(pairing.hasResponded()).isFalse();
    assertThat(pairing.getAssignmentResponse()).isNull();
  }

  @Test
  void onlyTheAssignedInspectorMayAnswer() {
    pairing.activate("Primary crew", null);

    assertThatThrownBy(() -> pairing.respond(UUID.randomUUID(), AssignmentResponse.ACCEPTED, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Only the assigned inspector may respond to this pairing");
  }

  @Test
  void theAssignedInspectorCanAcceptOnce() {
    pairing.activate("Primary crew", null);

    pairing.respond(inspectorId, AssignmentResponse.ACCEPTED, null);

    assertThat(pairing.getAssignmentResponse()).isEqualTo(AssignmentResponse.ACCEPTED);
    assertThat(pairing.getRespondedAt()).isNotNull();
    assertThat(pairing.hasResponded()).isTrue();
  }

  @Test
  void decliningRequiresAReason() {
    pairing.activate("Primary crew", null);

    assertThatThrownBy(() -> pairing.respond(inspectorId, AssignmentResponse.REJECTED, "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Declining a pairing requires a reason");

    pairing.respond(
        inspectorId, AssignmentResponse.REJECTED, "No flight qualification for this site");
    assertThat(pairing.getReason()).isEqualTo("No flight qualification for this site");
  }

  @Test
  void aPairingCannotBeAnsweredTwice() {
    pairing.activate("Primary crew", null);
    pairing.respond(inspectorId, AssignmentResponse.ACCEPTED, null);

    assertThatThrownBy(
            () -> pairing.respond(inspectorId, AssignmentResponse.REJECTED, "Changed my mind"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("This pairing has already received a response");
  }

  @Test
  void acceptanceIsNotReadinessAndOnlyAnActivePairingIsLive() {
    pairing.activate("Primary crew", null);
    pairing.respond(inspectorId, AssignmentResponse.ACCEPTED, null);

    assertThat(pairing.getStatus()).isEqualTo(AssetPairAssignmentStatus.ACTIVE);
    assertThat(pairing.isActiveAt(validFrom.plusSeconds(60))).isTrue();

    pairing.supersede("Replaced by a second crew");
    assertThat(pairing.isActiveAt(validFrom.plusSeconds(60))).isFalse();
  }

  @Test
  void aPairingOutsideItsWindowIsNotLive() {
    Instant until = validFrom.plusSeconds(86_400L);
    pairing.activate("Primary crew", until);

    assertThat(pairing.isActiveAt(validFrom.minusSeconds(60))).isFalse();
    assertThat(pairing.isActiveAt(until.plusSeconds(60))).isFalse();
  }

  @Test
  void anInvertedValidityWindowIsRejected() {
    assertThatThrownBy(() -> pairing.activate("Primary crew", validFrom.minusSeconds(60)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Pairing validity must end after it starts");
  }
}

package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.domain.InspectionReadinessDecision;
import com.smartdroneinspection.inspections.domain.enums.ReadinessDecisionType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The immutable, attributable decision record at the MF2-07/08 boundary. */
class InspectionReadinessDecisionTest {

  @Test
  void anApprovalCapturesThePreparationReviewerAndSourceHash() {
    UUID inspectionId = UUID.randomUUID();
    UUID preparationId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    String permitSnapshot = "[{\"id\":\"permit-1\",\"status\":\"ACTIVE\"}]";
    String permitIds = "[\"permit-1\"]";

    InspectionReadinessDecision decision =
        new InspectionReadinessDecision(
            inspectionId,
            preparationId,
            3,
            ReadinessDecisionType.APPROVED,
            reviewerId,
            null,
            permitSnapshot,
            "[]",
            "[]",
            permitIds,
            "[]",
            "[]",
            "sha256:7c4f2a8d");

    assertThat(decision.getInspectionId()).isEqualTo(inspectionId);
    assertThat(decision.getPreparationId()).isEqualTo(preparationId);
    assertThat(decision.getPreparationVersion()).isEqualTo(3);
    assertThat(decision.getDecision()).isEqualTo(ReadinessDecisionType.APPROVED);
    assertThat(decision.getReviewedByUserId()).isEqualTo(reviewerId);
    assertThat(decision.getDecidedAt()).isNotNull();
    assertThat(decision.getPermitSnapshot()).isEqualTo(permitSnapshot);
    assertThat(decision.getPermitSnapshotIds()).isEqualTo(permitIds);
    assertThat(decision.getSourceHash()).isEqualTo("sha256:7c4f2a8d");
  }

  @Test
  void aDecisionRequiresAnInspectionReviewerAndSourceHash() {
    UUID inspectionId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    String hash = "sha256:7c4f2a8d";

    assertThatThrownBy(
            () ->
                new InspectionReadinessDecision(
                    null,
                    null,
                    null,
                    ReadinessDecisionType.RETURNED,
                    reviewerId,
                    "Missing permit",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    hash))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("inspection");

    assertThatThrownBy(
            () ->
                new InspectionReadinessDecision(
                    inspectionId,
                    null,
                    null,
                    ReadinessDecisionType.RETURNED,
                    null,
                    "Missing permit",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    hash))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("reviewer");

    assertThatThrownBy(
            () ->
                new InspectionReadinessDecision(
                    inspectionId,
                    null,
                    null,
                    ReadinessDecisionType.RETURNED,
                    reviewerId,
                    "Missing permit",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("hash");
  }

  @Test
  void aReturnedDecisionRequiresAReason() {
    assertThatThrownBy(
            () ->
                new InspectionReadinessDecision(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    1,
                    ReadinessDecisionType.RETURNED,
                    UUID.randomUUID(),
                    "  ",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "sha256:7c4f2a8d"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
  }

  @Test
  void aPreparationVersionMustBePositiveWhenPresent() {
    assertThatThrownBy(
            () ->
                new InspectionReadinessDecision(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    0,
                    ReadinessDecisionType.APPROVED,
                    UUID.randomUUID(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "sha256:7c4f2a8d"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("version");
  }
}

package com.smartdroneinspection.inspections.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.domain.enums.AiFindingCandidateStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A candidate is advisory until a human decides, and a rejection must say why. */
class AiFindingCandidateTest {

  private final UUID evidenceId = UUID.randomUUID();
  private final UUID reviewer = UUID.randomUUID();

  private AiFindingCandidate candidate() {
    return new AiFindingCandidate(
        evidenceId,
        UUID.randomUUID(),
        "yolo-v8",
        "2026.02",
        "acme",
        "corrosion",
        new BigDecimal("0.91230"),
        "{\"x\":10,\"y\":20,\"w\":30,\"h\":40}");
  }

  @Test
  void newCandidateIsPendingAndCarriesModelProvenance() {
    AiFindingCandidate candidate = candidate();

    assertThat(candidate.getStatus()).isEqualTo(AiFindingCandidateStatus.PENDING);
    assertThat(candidate.getModelName()).isEqualTo("yolo-v8");
    assertThat(candidate.getModelVersion()).isEqualTo("2026.02");
    assertThat(candidate.getConfidence()).isEqualByComparingTo("0.91230");
    assertThat(candidate.getReviewedByUserId()).isNull();
  }

  @Test
  void confirmationRecordsTheReviewerAndTime() {
    AiFindingCandidate candidate = candidate();

    candidate.review(AiFindingCandidateStatus.CONFIRMED, reviewer, null);

    assertThat(candidate.getStatus()).isEqualTo(AiFindingCandidateStatus.CONFIRMED);
    assertThat(candidate.getReviewedByUserId()).isEqualTo(reviewer);
    assertThat(candidate.getReviewedAt()).isNotNull();
  }

  @Test
  void rejectionRequiresAReason() {
    AiFindingCandidate candidate = candidate();

    assertThatThrownBy(() -> candidate.review(AiFindingCandidateStatus.REJECTED, reviewer, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rejection reason");
    assertThatThrownBy(() -> candidate.review(AiFindingCandidateStatus.REJECTED, reviewer, "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aCandidateCannotBeReviewedTwice() {
    AiFindingCandidate candidate = candidate();
    candidate.review(AiFindingCandidateStatus.REJECTED, reviewer, "Not a defect");

    assertThatThrownBy(() -> candidate.review(AiFindingCandidateStatus.CONFIRMED, reviewer, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only pending candidates");
  }
}

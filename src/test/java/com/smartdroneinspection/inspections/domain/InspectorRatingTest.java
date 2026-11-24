package com.smartdroneinspection.inspections.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.domain.enums.RaterRole;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InspectorRatingTest {

  private final UUID inspectionId = UUID.randomUUID();
  private final UUID inspectorId = UUID.randomUUID();
  private final UUID customerId = UUID.randomUUID();

  @Test
  void constructsValidCustomerRatingAndAllowsUpdateWithin7Days() {
    Instant createdAt = Instant.parse("2026-11-10T10:00:00Z");
    InspectorRating rating =
        new InspectorRating(
            inspectionId,
            inspectorId,
            customerId,
            RaterRole.CUSTOMER,
            5,
            Map.of("evidenceQuality", 5, "communication", 4, "professionalism", 5),
            "  Thorough coverage and clear photos.  ",
            createdAt);

    assertThat(rating.getOverallScore()).isEqualTo(5);
    assertThat(rating.getComment()).isEqualTo("Thorough coverage and clear photos.");
    assertThat(rating.editableUntil()).isEqualTo(createdAt.plus(7, ChronoUnit.DAYS));
    assertThat(rating.isEditableAt(createdAt.plus(6, ChronoUnit.DAYS))).isTrue();

    rating.updateRating(
        customerId,
        4,
        Map.of("evidenceQuality", 4, "communication", 4, "professionalism", 4),
        "Updated review",
        createdAt.plus(7, ChronoUnit.DAYS));

    assertThat(rating.getOverallScore()).isEqualTo(4);
    assertThat(rating.getComment()).isEqualTo("Updated review");
    assertThat(rating.getUpdatedAt()).isEqualTo(createdAt.plus(7, ChronoUnit.DAYS));
  }

  @Test
  void rejectsEditAfter7DayWindowExpires() {
    Instant createdAt = Instant.parse("2026-11-10T10:00:00Z");
    InspectorRating rating =
        new InspectorRating(
            inspectionId,
            inspectorId,
            customerId,
            RaterRole.MANAGER,
            5,
            Map.of("sopAndSafety", 5, "technicalAccuracy", 5, "timeliness", 5),
            null,
            createdAt);

    Instant expired = createdAt.plus(7, ChronoUnit.DAYS).plusSeconds(1);
    assertThat(rating.isEditableAt(expired)).isFalse();
    assertThatThrownBy(
            () ->
                rating.updateRating(
                    customerId,
                    3,
                    Map.of("sopAndSafety", 3, "technicalAccuracy", 3, "timeliness", 3),
                    "Too late",
                    expired))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).code())
                    .isEqualTo("RATING_EDIT_WINDOW_EXPIRED"));
  }

  @Test
  void rejectsOutOfRangeScoresOrMismatchedCriteriaKeys() {
    Instant now = Instant.now();

    assertThatThrownBy(
            () ->
                new InspectorRating(
                    inspectionId,
                    inspectorId,
                    customerId,
                    RaterRole.CUSTOMER,
                    6,
                    Map.of("evidenceQuality", 5, "communication", 5, "professionalism", 5),
                    null,
                    now))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> assertThat(((BusinessException) ex).code()).isEqualTo("INVALID_RATING_PAYLOAD"));

    assertThatThrownBy(
            () ->
                new InspectorRating(
                    inspectionId,
                    inspectorId,
                    customerId,
                    RaterRole.CUSTOMER,
                    4,
                    Map.of("sopAndSafety", 5, "technicalAccuracy", 5, "timeliness", 5),
                    null,
                    now))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> assertThat(((BusinessException) ex).code()).isEqualTo("INVALID_RATING_PAYLOAD"));
  }
}

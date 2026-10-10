package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.domain.InspectionPreparation;
import com.smartdroneinspection.inspections.domain.enums.InspectionPreparationStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The MF2-03/06 preparation draft.
 *
 * <p>MF2-03 lets an inspector draft a component shot-list and MF2-06 lets them submit it. The
 * transitions below are the part MF2-07 depends on: a reviewer can only return or approve a
 * submission, never edit it, and a returned draft must go back through DRAFT before it is
 * resubmitted, so every approval points at a submission the inspector actually made.
 */
class InspectionPreparationTest {

  static final String A_SHOT_LIST =
      """
      [{"component":"Span P4","modality":"RGB","required":true}]\
      """;

  UUID inspectionId;
  UUID inspectorId;
  InspectionPreparation preparation;

  @BeforeEach
  void setUp() {
    inspectionId = UUID.randomUUID();
    inspectorId = UUID.randomUUID();
    preparation = new InspectionPreparation(inspectionId, inspectorId, 1);
  }

  @Test
  void aNewPreparationIsADraftThatHasNotBeenSubmitted() {
    assertThat(preparation.getStatus()).isEqualTo(InspectionPreparationStatus.DRAFT);
    assertThat(preparation.getSubmittedAt()).isNull();
    assertThat(preparation.getPreparationVersion()).isEqualTo(1);
    assertThat(preparation.belongsTo(inspectionId)).isTrue();
    assertThat(preparation.belongsTo(UUID.randomUUID())).isFalse();
  }

  @Test
  void aPreparationVersionMustStartAtOne() {
    assertThatThrownBy(() -> new InspectionPreparation(inspectionId, inspectorId, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("version");
  }

  @Test
  void theInspectorRecordsTheShotListAndSafetyObservations() {
    preparation.recordShotList(A_SHOT_LIST);
    preparation.recordSafetyObservations("Live 110V near pier 3; harbour traffic after 16:00");

    assertThat(preparation.getShotList()).isEqualTo(A_SHOT_LIST);
    assertThat(preparation.getSafetyObservations()).contains("harbour traffic");
  }

  @Test
  void submittingRecordsAnAttributableSubmission() {
    preparation.recordShotList(A_SHOT_LIST);
    preparation.recordSafetyObservations("Live 110V near pier 3");

    preparation.submit(inspectorId);

    assertThat(preparation.getStatus()).isEqualTo(InspectionPreparationStatus.SUBMITTED);
    assertThat(preparation.getSubmittedAt()).isNotNull();
  }

  @Test
  void submittingRequiresAComponentShotList() {
    preparation.recordSafetyObservations("Live 110V near pier 3");

    assertThatThrownBy(() -> preparation.submit(inspectorId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("shot-list");
  }

  @Test
  void submittingRequiresRecordedSafetyObservations() {
    preparation.recordShotList(A_SHOT_LIST);

    assertThatThrownBy(() -> preparation.submit(inspectorId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Safety");
  }

  @Test
  void anEmptyShotListIsNotAShotList() {
    preparation.recordShotList("  []  ");
    preparation.recordSafetyObservations("Live 110V near pier 3");

    assertThatThrownBy(() -> preparation.submit(inspectorId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("shot-list");
  }

  @Test
  void onlyThePreparingInspectorMaySubmit() {
    preparation.recordShotList(A_SHOT_LIST);
    preparation.recordSafetyObservations("Live 110V near pier 3");

    assertThatThrownBy(() -> preparation.submit(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("inspector");
  }

  @Test
  void aSubmittedPreparationCannotBeResubmitted() {
    preparation.recordShotList(A_SHOT_LIST);
    preparation.recordSafetyObservations("Live 110V near pier 3");
    preparation.submit(inspectorId);

    assertThatThrownBy(() -> preparation.submit(inspectorId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("draft");
  }

  @Test
  void aSubmittedPreparationCannotBeEdited() {
    preparation.recordShotList(A_SHOT_LIST);
    preparation.recordSafetyObservations("Live 110V near pier 3");
    preparation.submit(inspectorId);

    assertThatThrownBy(
            () ->
                preparation.recordShotList(
                    """
                [{"component":"Pier base","modality":"RGB","required":true}]\
                """))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("draft");
  }

  @Test
  void aReviewerMarksTheSubmissionReadyOrReturnsIt() {
    submitted();

    preparation.markReady();
    assertThat(preparation.getStatus()).isEqualTo(InspectionPreparationStatus.READY);

    InspectionPreparation returned = new InspectionPreparation(inspectionId, inspectorId, 2);
    returned.recordShotList(A_SHOT_LIST);
    returned.recordSafetyObservations("Live 110V near pier 3");
    returned.submit(inspectorId);
    returned.markReturned();

    assertThat(returned.getStatus()).isEqualTo(InspectionPreparationStatus.RETURNED);
  }

  @Test
  void aReturnedPreparationMustGoBackToDraftBeforeResubmission() {
    submitted();
    preparation.markReturned();

    assertThatThrownBy(() -> preparation.submit(inspectorId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("draft");

    preparation.startRevision();
    assertThat(preparation.getStatus()).isEqualTo(InspectionPreparationStatus.DRAFT);

    preparation.recordShotList(A_SHOT_LIST);
    preparation.submit(inspectorId);
    assertThat(preparation.getStatus()).isEqualTo(InspectionPreparationStatus.SUBMITTED);
  }

  @Test
  void aDraftIsNeitherApprovedNorReturned() {
    assertThatThrownBy(() -> preparation.markReady())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("submitted");

    assertThatThrownBy(() -> preparation.markReturned())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("submitted");
  }

  private void submitted() {
    preparation.recordShotList(A_SHOT_LIST);
    preparation.recordSafetyObservations("Live 110V near pier 3");
    preparation.submit(inspectorId);
  }
}

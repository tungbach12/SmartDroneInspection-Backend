package com.smartdroneinspection.inspections.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.domain.enums.FindingStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A finding is one observation, whoever made it, and the only thing the workflow asks of it is
 * whether the Inspector has confirmed it.
 */
class FindingTest {

  private final UUID evidenceId = UUID.randomUUID();
  private final UUID inspector = UUID.randomUUID();

  private Finding detected() {
    return new Finding(
        UUID.randomUUID(),
        evidenceId,
        inspector,
        "F-1",
        "corrosion",
        FindingSeverity.MEDIUM,
        null,
        "corrosion",
        null,
        "{\"x\":10,\"y\":20,\"w\":30,\"h\":40}",
        null,
        null,
        null,
        null,
        null,
        false,
        "yolo-v8",
        "2026.02",
        "acme",
        new BigDecimal("0.91230"));
  }

  private Finding manual() {
    return new Finding(
        UUID.randomUUID(),
        evidenceId,
        inspector,
        "F-2",
        "Spalling",
        FindingSeverity.HIGH,
        "Lower chord",
        "Surface loss of 5 mm",
        "Patch and seal",
        "Lower chord",
        "Spalling observed",
        "5 mm of surface loss",
        "HIGH",
        "5 mm",
        true);
  }

  @Test
  void aNewFindingIsPendingAndCarriesItsProvenanceWhenItHasOne() {
    Finding finding = detected();

    assertThat(finding.getStatus()).isEqualTo(FindingStatus.PENDING);
    assertThat(finding.getModelName()).isEqualTo("yolo-v8");
    assertThat(finding.getModelVersion()).isEqualTo("2026.02");
    assertThat(finding.getConfidence()).isEqualByComparingTo("0.91230");
    assertThat(finding.getInspectorConfirmedAt()).isNull();
  }

  @Test
  void onlyPendingAiFindingsAreRejectedRatherThanRetainedAsDeletedFindings() {
    Finding suggestion = detected();
    Finding inspectorFinding = manual();

    assertThat(suggestion.isPendingAiSuggestion()).isTrue();
    suggestion.confirmByInspector(inspector);
    assertThat(suggestion.isPendingAiSuggestion()).isFalse();
    assertThat(inspectorFinding.isPendingAiSuggestion()).isFalse();
  }

  @Test
  void aManualFindingHasNoModelBehindIt() {
    Finding finding = manual();

    assertThat(finding.getStatus()).isEqualTo(FindingStatus.PENDING);
    assertThat(finding.getModelName()).isNull();
    assertThat(finding.getConfidence()).isNull();
    assertThat(finding.isRepairRequired()).isTrue();
  }

  @Test
  void confirmingMakesTheFindingOfficialAndNamesWhoConfirmedIt() {
    Finding finding = detected();

    finding.confirmByInspector(inspector);

    assertThat(finding.getStatus()).isEqualTo(FindingStatus.CONFIRMED);
    assertThat(finding.getInspectorConfirmedAt()).isNotNull();
    assertThat(finding.getConfirmedByUserId()).isEqualTo(inspector);
    assertThat(finding.isOfficial()).isTrue();
  }

  @Test
  void removingAFindingKeepsWhoRemovedItAndWhy() {
    Finding finding = detected();

    finding.delete(inspector, "Not a defect");

    assertThat(finding.isDeleted()).isTrue();
    assertThat(finding.getDeletedByUserId()).isEqualTo(inspector);
    assertThat(finding.getDeletionReason()).isEqualTo("Not a defect");
  }

  @Test
  void removingAFindingNeedsAnActorAndAReason() {
    Finding finding = detected();

    assertThatThrownBy(() -> finding.delete(null, "Not a defect"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> finding.delete(inspector, "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void restoringBringsAFindingBackAsItWas() {
    Finding finding = detected();
    finding.confirmByInspector(inspector);
    finding.delete(inspector, "Removed by mistake");

    finding.restore();

    assertThat(finding.isDeleted()).isFalse();
    assertThat(finding.getDeletionReason()).isNull();
    // The confirmation still stands: removing a finding does not undo what the Inspector said.
    assertThat(finding.getStatus()).isEqualTo(FindingStatus.CONFIRMED);
  }

  @Test
  void restoringAFindingThatWasNotRemovedIsRefused() {
    Finding finding = detected();

    assertThatThrownBy(finding::restore).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void editingAFindingTakesBackTheConfirmationBecauseTheContentMoved() {
    Finding finding = detected();
    finding.confirmByInspector(inspector);

    finding.revise(
        evidenceId,
        "corrosion",
        FindingSeverity.HIGH,
        "Lower chord",
        "corrosion",
        null,
        null,
        null,
        null,
        null,
        null,
        false);

    assertThat(finding.getStatus()).isEqualTo(FindingStatus.PENDING);
    assertThat(finding.getInspectorConfirmedAt()).isNull();
    assertThat(finding.isOfficial()).isFalse();
  }
}

package com.smartdroneinspection.inspections.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.domain.enums.FindingSeverity;
import com.smartdroneinspection.inspections.domain.enums.FindingStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests the finding's review, removal, and MF4 forwarding state. */
class FindingTest {

  private final UUID evidenceId = UUID.randomUUID();

  private Finding detected() {
    return new Finding(
        UUID.randomUUID(),
        evidenceId,
        "F-1",
        "corrosion",
        FindingSeverity.MEDIUM,
        "corrosion visible",
        null,
        "{\"x\":10,\"y\":20,\"w\":30,\"h\":40}",
        "yolo-v8",
        "2026.02",
        "acme",
        new BigDecimal("0.91230"));
  }

  private Finding manual() {
    return new Finding(
        UUID.randomUUID(),
        evidenceId,
        "F-2",
        "Spalling",
        FindingSeverity.HIGH,
        "Surface loss of 5 mm",
        "Patch and seal",
        null,
        null,
        null,
        null,
        null);
  }

  @Test
  void aNewFindingIsPendingAndCarriesItsAiProvenance() {
    Finding finding = detected();

    assertThat(finding.getStatus()).isEqualTo(FindingStatus.PENDING);
    assertThat(finding.getModelName()).isEqualTo("yolo-v8");
    assertThat(finding.getModelVersion()).isEqualTo("2026.02");
    assertThat(finding.getModelProvider()).isEqualTo("acme");
    assertThat(finding.getConfidence()).isEqualByComparingTo("0.91230");
    assertThat(finding.getInspectorConfirmedAt()).isNull();
    assertThat(finding.getForwardedToMaintenanceAt()).isNull();
  }

  @Test
  void onlyPendingAiFindingsAreSuggestions() {
    Finding suggestion = detected();
    Finding inspectorFinding = manual();

    assertThat(suggestion.isPendingAiSuggestion()).isTrue();
    suggestion.confirmByInspector();
    assertThat(suggestion.isPendingAiSuggestion()).isFalse();
    assertThat(inspectorFinding.isPendingAiSuggestion()).isFalse();
  }

  @Test
  void confirmingMakesTheFindingOfficial() {
    Finding finding = detected();

    finding.confirmByInspector();

    assertThat(finding.getStatus()).isEqualTo(FindingStatus.CONFIRMED);
    assertThat(finding.getInspectorConfirmedAt()).isNotNull();
    assertThat(finding.isOfficial()).isTrue();
  }

  @Test
  void deletingKeepsATimestampAndRestoreRetainsTheFindingDecision() {
    Finding finding = detected();
    finding.confirmByInspector();

    finding.delete();

    assertThat(finding.isDeleted()).isTrue();
    assertThat(finding.getDeletedAt()).isNotNull();
    assertThatThrownBy(finding::delete)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already removed");

    finding.restore();

    assertThat(finding.isDeleted()).isFalse();
    assertThat(finding.getDeletedAt()).isNull();
    assertThat(finding.getStatus()).isEqualTo(FindingStatus.CONFIRMED);
    assertThat(finding.isOfficial()).isTrue();
  }

  @Test
  void restoringAFindingThatWasNotRemovedIsRefused() {
    assertThatThrownBy(detected()::restore).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void editingAFindingTakesBackItsConfirmation() {
    Finding finding = detected();
    finding.confirmByInspector();

    finding.revise(
        evidenceId, "corrosion", FindingSeverity.HIGH, "More extensive corrosion", "Repair");

    assertThat(finding.getStatus()).isEqualTo(FindingStatus.PENDING);
    assertThat(finding.getInspectorConfirmedAt()).isNull();
    assertThat(finding.isOfficial()).isFalse();
  }

  @Test
  void forwardingIsRecordedForEachConfirmedFindingOnlyOnce() {
    Finding finding = detected();
    assertThatThrownBy(finding::forwardToMaintenance)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("confirmed finding");

    finding.confirmByInspector();
    finding.forwardToMaintenance();

    assertThat(finding.getForwardedToMaintenanceAt()).isNotNull();
    assertThat(finding.isForwardedToMaintenance()).isTrue();
    assertThatThrownBy(finding::forwardToMaintenance)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already forwarded");
  }
}

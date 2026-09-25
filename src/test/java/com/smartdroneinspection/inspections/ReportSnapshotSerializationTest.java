package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.inspections.api.dto.response.ReportSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ReportSnapshotSerializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void deserializesLegacyJsonWithoutAiDraftNarrativeField() throws Exception {
    String legacyJson =
        """
        {
          "inspectionId": "11111111-1111-1111-1111-111111111111",
          "serviceOrderId": "22222222-2222-2222-2222-222222222222",
          "assetId": "33333333-3333-3333-3333-333333333333",
          "checklistTemplateId": "44444444-4444-4444-4444-444444444444",
          "checklistName": "Structural Deck Inspection",
          "generatedAt": "2026-09-24T12:00:00Z",
          "checklist": [],
          "evidence": [],
          "findings": []
        }
        """;

    ReportSnapshot snapshot = objectMapper.readValue(legacyJson, ReportSnapshot.class);

    assertThat(snapshot).isNotNull();
    assertThat(snapshot.checklistName()).isEqualTo("Structural Deck Inspection");
    assertThat(snapshot.aiDraftNarrative()).isNull();
  }

  @Test
  void serializesAndDeserializesWithAiDraftNarrative() throws Exception {
    ReportSnapshot original =
        new ReportSnapshot(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Bridge Checklist",
            Instant.parse("2026-09-25T10:00:00Z"),
            List.of(),
            List.of(),
            List.of(),
            "AI draft text: Surface inspection indicates superficial wear.");

    String json = objectMapper.writeValueAsString(original);
    ReportSnapshot roundTrip = objectMapper.readValue(json, ReportSnapshot.class);

    assertThat(roundTrip.aiDraftNarrative())
        .isEqualTo("AI draft text: Surface inspection indicates superficial wear.");
  }

  @Test
  void withAiDraftNarrativeProducesNewSnapshotPreservingAllFields() {
    ReportSnapshot original =
        new ReportSnapshot(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Bridge Checklist",
            Instant.parse("2026-09-25T10:00:00Z"),
            List.of(),
            List.of(),
            List.of(),
            null);

    ReportSnapshot updated = original.withAiDraftNarrative("Updated narrative text");

    assertThat(updated.inspectionId()).isEqualTo(original.inspectionId());
    assertThat(updated.serviceOrderId()).isEqualTo(original.serviceOrderId());
    assertThat(updated.assetId()).isEqualTo(original.assetId());
    assertThat(updated.checklistTemplateId()).isEqualTo(original.checklistTemplateId());
    assertThat(updated.checklistName()).isEqualTo(original.checklistName());
    assertThat(updated.generatedAt()).isEqualTo(original.generatedAt());
    assertThat(updated.aiDraftNarrative()).isEqualTo("Updated narrative text");
  }
}

package com.smartdroneinspection.inspections.api.dto.request;

import com.smartdroneinspection.inspections.api.dto.FieldSessionChecklistDto.ChecklistItemSnapshot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The Inspector's recorded field outcome before MF2-12 closes the session. */
public record EndFieldSessionRequest(
    @NotBlank @Size(max = 2000) String outcomeNotes,
    List<ChecklistItemSnapshot> postFlightChecklistSnapshot) {

  public EndFieldSessionRequest(String outcomeNotes) {
    this(outcomeNotes, null);
  }
}

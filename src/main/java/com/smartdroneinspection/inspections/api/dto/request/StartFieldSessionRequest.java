package com.smartdroneinspection.inspections.api.dto.request;

import com.smartdroneinspection.inspections.api.dto.FieldSessionChecklistDto.ChecklistItemSnapshot;
import com.smartdroneinspection.inspections.api.dto.FieldSessionChecklistDto.PreFlightConditions;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record StartFieldSessionRequest(
    @NotBlank @Size(max = 2000) String preFlightChecklistNote,
    Boolean startNow,
    LocalDate sessionDate,
    List<ChecklistItemSnapshot> preFlightChecklistSnapshot,
    PreFlightConditions preFlightConditions) {

  public StartFieldSessionRequest(String preFlightChecklistNote) {
    this(preFlightChecklistNote, null, null, null, null);
  }

  public StartFieldSessionRequest(String preFlightChecklistNote, Boolean startNow) {
    this(preFlightChecklistNote, startNow, null, null, null);
  }
}

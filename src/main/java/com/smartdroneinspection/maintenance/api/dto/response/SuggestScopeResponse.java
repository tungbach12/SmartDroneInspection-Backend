package com.smartdroneinspection.maintenance.api.dto.response;

import java.util.List;

/** Suggested corrective scope, acceptance criteria, engineering standards, and tasks. */
public record SuggestScopeResponse(
    String correctiveScope,
    String acceptanceCriteria,
    List<String> referenceStandards,
    List<SuggestedTaskDto> suggestedTasks,
    String modelName,
    boolean fallbackApplied) {

  public record SuggestedTaskDto(
      int taskNumber, String name, String method, String acceptanceCriteria) {}
}

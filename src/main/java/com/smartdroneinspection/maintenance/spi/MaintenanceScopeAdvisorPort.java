package com.smartdroneinspection.maintenance.spi;

import java.io.IOException;
import java.util.List;

/** Outbound port for generating AI/RAG-assisted corrective scope and acceptance criteria. */
public interface MaintenanceScopeAdvisorPort {

  String modelName();

  SuggestedScope suggestScope(ScopeAdvisorContext context) throws IOException;

  record ScopeAdvisorContext(
      String findingCode,
      String defectLabel,
      String severity,
      String component,
      String locationDescription,
      String description,
      String recommendedAction,
      String measurement,
      List<KnowledgeChunkSummary> referenceChunks) {}

  record KnowledgeChunkSummary(
      String standardCode, String title, String category, String content) {}

  record SuggestedScope(
      String correctiveScope,
      String acceptanceCriteria,
      List<String> referenceStandards,
      List<SuggestedTask> suggestedTasks) {}

  record SuggestedTask(
      int taskNumber, String name, String method, String acceptanceCriteria) {}
}

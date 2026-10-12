package com.smartdroneinspection.inspections.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The authored section values of a report version, as Report 3 §3.7.2 requires them to be stored.
 *
 * <p>Only the sections a human writes live here. The Evidence register, Confirmed findings,
 * Approval block and Appendices are rendered from the evidence records, the reviewed finding
 * records and the version's own authorship columns, so a copy of them is deliberately not stored:
 * there is one copy of each finding and the document cannot disagree with the finding decisions.
 *
 * <p>A missing section reads back as empty and renders as "not supplied". Absence is disclosed,
 * never presented as an empty value the author chose.
 */
public record ReportSections(
    String executiveSummary,
    String purposeScopeCriteria,
    String methodEquipmentConditions,
    String conclusionsRecommendations,
    OverallCondition overallCondition,
    String omissionDisclosure) {

  /** The overall condition assessment a reader looks up before reading the prose. */
  public enum OverallCondition {
    GOOD,
    FAIR,
    POOR,
    CRITICAL
  }

  public static final int MAX_SECTION_CHARS = 10_000;
  public static final int MAX_DISCLOSURE_CHARS = 2_000;

  public static final ReportSections EMPTY = new ReportSections(null, null, null, null, null, null);

  /**
   * Reads the authored sections out of a stored snapshot.
   *
   * <p>A snapshot that cannot be parsed yields no sections rather than failing the read. The
   * version's gates, hashes and provenance remain authoritative, and refusing to list them would
   * turn one malformed historical row into an outage.
   */
  public static ReportSections read(String contentSnapshot, ObjectMapper objectMapper) {
    if (contentSnapshot == null || contentSnapshot.isBlank()) {
      return EMPTY;
    }
    try {
      return fromJson(objectMapper.readTree(contentSnapshot), objectMapper);
    } catch (Exception exception) {
      return EMPTY;
    }
  }

  private static ReportSections fromJson(JsonNode node, ObjectMapper objectMapper) {
    if (node == null || !node.isObject()) {
      return EMPTY;
    }
    return new ReportSections(
        text(node, "executiveSummary"),
        text(node, "purposeScopeCriteria"),
        text(node, "methodEquipmentConditions"),
        text(node, "conclusionsRecommendations"),
        condition(text(node, "overallCondition")),
        text(node, "omissionDisclosure"));
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    String raw = value.asString();
    return raw == null || raw.isBlank() ? null : raw;
  }

  private static OverallCondition condition(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return OverallCondition.valueOf(raw.trim());
    } catch (IllegalArgumentException exception) {
      // A value this build does not know is treated as absent rather than failing the whole read.
      return null;
    }
  }

  /** Writes the authored sections into a snapshot, leaving the provenance keys untouched. */
  public String applyTo(String contentSnapshot, ObjectMapper objectMapper) {
    ObjectNode root;
    try {
      JsonNode parsed =
          contentSnapshot == null || contentSnapshot.isBlank()
              ? objectMapper.createObjectNode()
              : objectMapper.readTree(contentSnapshot);
      root =
          parsed != null && parsed.isObject()
              ? (ObjectNode) parsed
              : objectMapper.createObjectNode();
    } catch (Exception exception) {
      root = objectMapper.createObjectNode();
    }
    for (Map.Entry<String, String> entry : values().entrySet()) {
      if (entry.getValue() == null) {
        root.remove(entry.getKey());
      } else {
        root.put(entry.getKey(), entry.getValue());
      }
    }
    return root.toString();
  }

  private Map<String, String> values() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("executiveSummary", executiveSummary);
    values.put("purposeScopeCriteria", purposeScopeCriteria);
    values.put("methodEquipmentConditions", methodEquipmentConditions);
    values.put("conclusionsRecommendations", conclusionsRecommendations);
    values.put("overallCondition", overallCondition == null ? null : overallCondition.name());
    values.put("omissionDisclosure", omissionDisclosure);
    return values;
  }

  public Optional<String> executiveSummaryIfPresent() {
    return Optional.ofNullable(executiveSummary);
  }
}

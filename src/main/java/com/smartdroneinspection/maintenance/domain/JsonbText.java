package com.smartdroneinspection.maintenance.domain;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Normalizes plain-text and JSON inputs for PostgreSQL {@code jsonb} columns.
 *
 * <p>PostgreSQL rejects unquoted plain text in {@code jsonb} columns, while web, mobile, and RAG
 * callers naturally supply human-readable strings for scope, acceptance criteria, assumptions, and
 * narratives. Valid JSON objects/arrays are stored verbatim; plain strings are stored as JSON
 * string literals in the database and unwrapped on read.
 */
final class JsonbText {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JsonbText() {}

  static String toNullableJson(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (isStructuredJsonOrLiteral(trimmed)) {
      return value;
    }
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JacksonException ex) {
      throw new IllegalArgumentException("Unable to encode text as JSON", ex);
    }
  }

  static String toRequiredJson(String value) {
    if (value == null || value.isBlank()) {
      return "{}";
    }
    String normalized = toNullableJson(value);
    return normalized == null ? "{}" : normalized;
  }

  static String fromJson(String stored) {
    if (stored == null) {
      return null;
    }
    String trimmed = stored.trim();
    if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      try {
        return MAPPER.readValue(trimmed, String.class);
      } catch (JacksonException ignored) {
        return stored;
      }
    }
    return stored;
  }

  private static boolean isStructuredJsonOrLiteral(String trimmed) {
    char first = trimmed.charAt(0);
    if (first != '{' && first != '[' && first != '"') {
      return false;
    }
    try {
      JsonNode node = MAPPER.readTree(trimmed);
      return node != null && (node.isObject() || node.isArray() || node.isString());
    } catch (JacksonException ex) {
      return false;
    }
  }
}

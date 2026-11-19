package com.smartdroneinspection.inspections.spi;

import java.util.Optional;

/**
 * Outbound port for producing an embedding used by historical case retrieval.
 *
 * <p>The inspection module owns the retrieval contract; the concrete OpenAI-compatible embeddings
 * adapter lives in {@code infrastructure}. An empty result means the advisory capability is
 * unavailable, and retrieval must fall back to deterministic matching rather than fail the report.
 */
public interface HistoricalCaseAdvisorPort {

  /**
   * Returns a 1536-dimensional embedding for {@code text}, or empty when the model is unavailable.
   */
  Optional<float[]> embed(String text);
}

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
   * Returns a 384-dimensional embedding for {@code text}, or empty when the model is unavailable.
   */
  Optional<float[]> embed(String text);

  /**
   * Drafts a concise technical note from the supplied image and retrieved engineering context, or
   * empty when the chat/vision model is unconfigured, unavailable or returns no usable text. An
   * empty result lets the inspection module synthesize the note deterministically instead of
   * failing the request.
   */
  Optional<String> draftFindingNote(DraftFindingNoteCommand command);
}

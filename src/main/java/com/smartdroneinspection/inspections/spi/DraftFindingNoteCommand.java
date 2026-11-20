package com.smartdroneinspection.inspections.spi;

import java.util.List;

/**
 * Multimodal context used to draft one finding's technical note.
 *
 * <p>The inspection module owns this contract; the concrete chat/vision adapter lives in {@code
 * infrastructure}. The payload carries only the fields a model needs to ground its answer.
 * Identifiers are deliberately absent so a draft can never quote a raw UUID back to an Inspector.
 */
public record DraftFindingNoteCommand(
    String defectLabel,
    String severity,
    String boundingBox,
    String existingNote,
    String locale,
    String assetCategory,
    String assetLabel,
    String checkpointTitle,
    byte[] imageBytes,
    String imageContentType,
    List<CaseReference> cases,
    List<GuidelineReference> guidelines) {

  /** A short, source-backed engineering precedent. */
  public record CaseReference(String defectLabel, String technicalNotes, String outcome) {}

  /** A short, source-backed engineering standard. */
  public record GuidelineReference(String standardCode, String title, String guidance) {}
}

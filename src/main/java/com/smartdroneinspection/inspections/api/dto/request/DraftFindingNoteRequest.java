package com.smartdroneinspection.inspections.api.dto.request;

/** Optional editor context for a single-click finding note draft. */
public record DraftFindingNoteRequest(String locale, String existingNote) {}

package com.smartdroneinspection.inspections.api.dto.response;

/** A compact, source-backed reference attached to an executive AI summary. */
public record KnowledgeReferenceSummary(
    String kind, String codeOrLabel, String summary, Double similarity) {}

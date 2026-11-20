package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.api.dto.response.KnowledgeRecommendationResponse.CaseMatch;
import com.smartdroneinspection.inspections.api.dto.response.KnowledgeRecommendationResponse.GuidelineMatch;
import java.util.List;

/**
 * A ready-for-review technical note and the evidence used to produce it.
 *
 * <p>{@code mode} distinguishes an external vision/chat draft from the deterministic RAG fallback;
 * {@code retrievalMode} reports how the cited cases and guidelines were ranked.
 */
public record DraftFindingNoteResponse(
    String note,
    String mode,
    String retrievalMode,
    List<CaseMatch> citedCases,
    List<GuidelineMatch> citedGuidelines) {}

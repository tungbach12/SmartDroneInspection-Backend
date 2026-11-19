package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wrapped DroneKnowledgeAI payload shared by the Web and Mobile clients.
 *
 * <p>{@code retrievalMode} reports whether vector search or deterministic matching produced the
 * result. Similarity values are always normalized to {@code [0.0, 1.0]} so a client can render them
 * directly as a 0–100% match chip.
 */
public record KnowledgeRecommendationResponse(
    String retrievalMode, List<CaseMatch> cases, List<GuidelineMatch> guidelines) {

  public record CaseMatch(
      UUID id,
      String defectLabel,
      String severity,
      String assetCategory,
      String assetLabel,
      boolean sameAsset,
      boolean sameOrganization,
      double similarity,
      String technicalNotes,
      String outcome,
      Instant publishedAt) {}

  public record GuidelineMatch(
      UUID id,
      String standardCode,
      String title,
      String severity,
      double similarity,
      String guidance,
      String referenceUrl) {}
}

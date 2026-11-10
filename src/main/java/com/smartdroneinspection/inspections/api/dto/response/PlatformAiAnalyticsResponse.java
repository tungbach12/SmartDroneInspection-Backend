package com.smartdroneinspection.inspections.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PlatformAiAnalyticsResponse(
    boolean autoConfirmEnabled,
    BigDecimal autoConfirmThreshold,
    long totalAiDetections,
    long confirmedAiFindings,
    long autoConfirmedFindings,
    long pendingAiFindings,
    long rejectedAiFindings,
    long removedConfirmedAiFindings,
    long rejectedPendingFindings,
    long manualFindingsCount,
    double acceptanceRatePercent,
    double rejectionRatePercent,
    double averageConfidencePercent,
    double averageConfirmedConfidencePercent,
    Instant updatedAt,
    List<ModelPerformanceStat> modelBreakdown,
    List<DefectPerformanceStat> defectBreakdown,
    List<ConfidenceBucketStat> confidenceBuckets,
    int newlyAutoConfirmedCount) {

  public record ModelPerformanceStat(
      String modelName,
      long totalDetections,
      long confirmedCount,
      long pendingCount,
      long rejectedCount,
      double acceptanceRatePercent,
      double avgConfidencePercent) {}

  public record DefectPerformanceStat(
      String defectLabel,
      long totalDetections,
      long confirmedCount,
      long pendingCount,
      long rejectedCount,
      double avgConfidencePercent) {}

  public record ConfidenceBucketStat(
      String bucketLabel,
      int minPercent,
      int maxPercent,
      long totalCount,
      long confirmedCount,
      long pendingCount,
      long rejectedCount) {}
}

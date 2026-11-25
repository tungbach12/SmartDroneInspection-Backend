package com.smartdroneinspection.dashboard.api.dto;

import java.util.List;

public record DashboardAnalyticsResponse(
    List<DashboardMetricDto> summaryCards,
    PerformanceChartDto performanceChart,
    RadialGaugeDto radialGauge,
    StackedBarChartDto stackedBarChart,
    QueueTableDto queueTable,
    List<PipelineStageDto> pipelineStages,
    PerformanceChartDto secondaryPerformanceChart,
    List<ActionItemDto> actionItems,
    List<ScheduleSlotDto> upcomingSchedule,
    List<InspectorWorkloadDto> inspectorWorkloads,
    List<ServiceHealthDto> serviceHealth) {

  public DashboardAnalyticsResponse(
      List<DashboardMetricDto> summaryCards,
      PerformanceChartDto performanceChart,
      RadialGaugeDto radialGauge,
      StackedBarChartDto stackedBarChart,
      QueueTableDto queueTable) {
    this(
        summaryCards,
        performanceChart,
        radialGauge,
        stackedBarChart,
        queueTable,
        List.of(),
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  public record DashboardMetricDto(
      String id,
      String label,
      String value,
      String change,
      boolean positive,
      String subtext,
      String iconType) {}

  public record PerformanceChartDto(
      String headerLabel,
      String headlineValue,
      String trendBadge,
      String series1Name,
      String series1Color,
      List<Long> series1Data,
      String series2Name,
      String series2Color,
      List<Long> series2Data,
      List<String> months,
      List<String> yTicks,
      long maxVal) {}

  public record RadialGaugeDto(
      String title,
      String subtitle,
      String targetValue,
      String targetLabel,
      int percentage,
      List<RadialSubMetricDto> subMetrics) {}

  public record RadialSubMetricDto(String label, String value, int percentage) {}

  public record StackedBarChartDto(
      String title, List<String> categories, List<StackedBarSeriesDto> channels, int maxStack) {}

  public record StackedBarSeriesDto(String name, String color, List<Integer> data) {}

  public record QueueTableDto(
      String title,
      String subtitle,
      List<String> headers,
      List<QueueTableRowDto> rows,
      String viewAllLink,
      String viewAllText) {}

  public record QueueTableRowDto(
      String col1,
      String col2,
      String col3,
      String col4,
      String col5,
      String status,
      String link) {}

  public record PipelineStageDto(String label, long count, int percentage, String color) {}

  public record ActionItemDto(
      String category,
      String title,
      String code,
      String subtitle,
      String status,
      String badgeTone,
      String link) {}

  public record ScheduleSlotDto(
      String id, String title, String subtitle, String timeLabel, String status, String link) {}

  public record InspectorWorkloadDto(
      String inspectorName,
      long activeMissions,
      double averageRating,
      long ratingCount,
      String licenseClass) {}

  public record ServiceHealthDto(String name, String status, String detail) {}
}

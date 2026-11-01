package com.smartdroneinspection.dashboard.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.dashboard.api.dto.DashboardAnalyticsResponse;
import com.smartdroneinspection.dashboard.service.DashboardAnalyticsService;
import com.smartdroneinspection.shared.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class DashboardControllerTest {

  private DashboardAnalyticsService analyticsService;
  private DashboardController controller;

  @BeforeEach
  void setUp() {
    analyticsService = mock(DashboardAnalyticsService.class);
    controller = new DashboardController(analyticsService);
  }

  @Test
  void getAnalyticsReturnsSuccessResponse() {
    UUID actorId = UUID.randomUUID();
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn(actorId.toString());

    DashboardAnalyticsResponse mockResponse =
        new DashboardAnalyticsResponse(
            List.of(),
            new DashboardAnalyticsResponse.PerformanceChartDto(
                "Header", "100k", "+5%", "S1", "#000", List.of(), "S2", "#fff", List.of(),
                List.of(), List.of(), 100),
            new DashboardAnalyticsResponse.RadialGaugeDto(
                "Title", "Sub", "100%", "Target", 100, List.of()),
            new DashboardAnalyticsResponse.StackedBarChartDto("Bar", List.of(), List.of(), 10),
            new DashboardAnalyticsResponse.QueueTableDto(
                "Table", "Desc", List.of(), List.of(), "/link", "View"));

    when(analyticsService.getAnalytics(actorId)).thenReturn(mockResponse);

    ApiResponse<DashboardAnalyticsResponse> result = controller.getAnalytics(jwt);

    assertThat(result).isNotNull();
    assertThat(result.success()).isTrue();
    assertThat(result.data()).isEqualTo(mockResponse);
  }
}

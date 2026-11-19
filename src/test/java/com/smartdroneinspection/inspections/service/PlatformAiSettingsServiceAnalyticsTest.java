package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.inspections.api.dto.request.UpdatePlatformAiSettingsRequest;
import com.smartdroneinspection.inspections.service.PlatformAiSettingsService.AiThresholdConfig;
import com.smartdroneinspection.shared.api.PlatformAuditPort;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PlatformAiSettingsServiceAnalyticsTest {

  private JdbcTemplate jdbcTemplate;
  private PlatformAuditPort audit;
  private PlatformAiSettingsService service;

  @BeforeEach
  void setUp() {
    jdbcTemplate = mock(JdbcTemplate.class);
    audit = mock(PlatformAuditPort.class);
    service = new PlatformAiSettingsService(jdbcTemplate, audit);
  }

  @Test
  void readsThresholdConfigurationWithMapper() throws Exception {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getBoolean("auto_confirm_enabled")).thenReturn(true);
    when(rs.getBigDecimal("auto_confirm_threshold")).thenReturn(new BigDecimal("80.00"));
    when(rs.getLong("rejected_pending_count")).thenReturn(3L);
    Instant updatedAt = Instant.parse("2026-01-01T00:00:00Z");
    when(rs.getTimestamp("updated_at")).thenReturn(Timestamp.from(updatedAt));
    when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<AiThresholdConfig>>any()))
        .thenAnswer(
            invocation ->
                List.of(invocation.<RowMapper<AiThresholdConfig>>getArgument(1).mapRow(rs, 0)));

    AiThresholdConfig config = service.getThresholdConfig();

    assertThat(config.autoConfirmEnabled()).isTrue();
    assertThat(config.autoConfirmThreshold()).isEqualByComparingTo("80.00");
    assertThat(config.rejectedPendingCount()).isEqualTo(3L);
    assertThat(config.updatedAt()).isEqualTo(updatedAt);
  }

  @Test
  void fallsBackToDefaultsWhenConfigurationRowIsMissingOrTimestampIsNull() throws Exception {
    when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<AiThresholdConfig>>any()))
        .thenReturn(List.of());

    AiThresholdConfig defaults = service.getThresholdConfig();
    assertThat(defaults.autoConfirmEnabled()).isTrue();
    assertThat(defaults.autoConfirmThreshold()).isEqualByComparingTo("85.00");
    assertThat(defaults.rejectedPendingCount()).isZero();

    ResultSet rs = mock(ResultSet.class);
    when(rs.getTimestamp("updated_at")).thenReturn(null);
    when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<AiThresholdConfig>>any()))
        .thenAnswer(
            invocation ->
                List.of(invocation.<RowMapper<AiThresholdConfig>>getArgument(1).mapRow(rs, 0)));

    assertThat(service.getThresholdConfig().updatedAt()).isNotNull();
  }

  @Test
  void shouldAutoConfirmFetchesConfigurationAndHandlesNullInputs() throws Exception {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getBoolean("auto_confirm_enabled")).thenReturn(true);
    when(rs.getBigDecimal("auto_confirm_threshold")).thenReturn(new BigDecimal("80.00"));
    when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<AiThresholdConfig>>any()))
        .thenAnswer(
            invocation ->
                List.of(invocation.<RowMapper<AiThresholdConfig>>getArgument(1).mapRow(rs, 0)));

    assertThat(service.shouldAutoConfirm(new BigDecimal("0.90"))).isTrue();
    assertThat(service.shouldAutoConfirm((BigDecimal) null)).isFalse();
    assertThat(service.shouldAutoConfirm(new BigDecimal("0.90"), null)).isFalse();
  }

  @Test
  void recordRejectedPendingSuggestionIssuesCounterUpdate() {
    service.recordRejectedPendingSuggestion();

    verify(jdbcTemplate).update(anyString());
  }

  @Test
  void updateSettingsPersistsThresholdWithoutRetroactiveApply() throws Exception {
    UUID actorId = UUID.randomUUID();
    stubAnalyticsQueries();
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

    var response =
        service.updateSettings(
            actorId, new UpdatePlatformAiSettingsRequest(true, new BigDecimal("87.126")));

    assertThat(response).isNotNull();
    assertThat(response.newlyAutoConfirmedCount()).isZero();
    verify(jdbcTemplate)
        .update(ArgumentMatchers.contains("INSERT INTO platform_ai_settings"), any(Object[].class));
    verify(jdbcTemplate, org.mockito.Mockito.never())
        .update(ArgumentMatchers.contains("UPDATE findings"), any(Object[].class));
    verify(audit)
        .recordPlatformAction(
            actorId,
            "PLATFORM_AI_SETTINGS",
            actorId,
            "AI_SETTINGS_UPDATED",
            "SUCCESS",
            "autoConfirmEnabled=true, autoConfirmThreshold=87.13");
  }

  @Test
  void updateSettingsSkipsPendingFindingsWhenDisabled() throws Exception {
    stubAnalyticsQueries();

    var response =
        service.updateSettings(
            UUID.randomUUID(), new UpdatePlatformAiSettingsRequest(false, new BigDecimal("90")));

    assertThat(response.newlyAutoConfirmedCount()).isZero();
    verify(jdbcTemplate)
        .update(ArgumentMatchers.contains("INSERT INTO platform_ai_settings"), any(Object[].class));
    verify(jdbcTemplate, org.mockito.Mockito.never())
        .update(ArgumentMatchers.contains("UPDATE findings"), any(Object[].class));
  }

  @Test
  void getAnalyticsMapsAggregateModelDefectAndBucketRows() throws Exception {
    stubAnalyticsQueries();

    var response = service.getAnalytics();

    assertThat(response).isNotNull();
    assertThat(response.totalAiDetections()).isGreaterThan(0);
    assertThat(response.modelBreakdown()).hasSize(1);
    assertThat(response.defectBreakdown()).hasSize(1);
    assertThat(response.confidenceBuckets()).hasSize(5);
  }

  private void stubAnalyticsQueries() throws Exception {
    when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<Object>>any()))
        .thenAnswer(
            invocation -> {
              String sql = invocation.getArgument(0);
              ResultSet rs = mock(ResultSet.class);
              if (sql.contains("FROM platform_ai_settings")) {
                when(rs.getBoolean("auto_confirm_enabled")).thenReturn(true);
                when(rs.getBigDecimal("auto_confirm_threshold"))
                    .thenReturn(new BigDecimal("85.00"));
                when(rs.getLong("rejected_pending_count")).thenReturn(2L);
                when(rs.getTimestamp("updated_at")).thenReturn(Timestamp.from(Instant.now()));
              } else if (sql.contains("GROUP BY model_name")) {
                when(rs.getString("model_name")).thenReturn("yolo-v8");
                when(rs.getLong("total_rows")).thenReturn(10L);
                when(rs.getLong("confirmed_cnt")).thenReturn(6L);
                when(rs.getLong("pending_cnt")).thenReturn(2L);
                when(rs.getLong("removed_cnt")).thenReturn(2L);
                when(rs.getDouble("avg_conf")).thenReturn(78.4);
              } else {
                when(rs.getString("defect_label")).thenReturn("Crack");
                when(rs.getLong("total_rows")).thenReturn(5L);
                when(rs.getLong("confirmed_cnt")).thenReturn(3L);
                when(rs.getLong("pending_cnt")).thenReturn(1L);
                when(rs.getLong("removed_cnt")).thenReturn(1L);
                when(rs.getDouble("avg_conf")).thenReturn(82.2);
              }
              return List.of(invocation.<RowMapper<Object>>getArgument(1).mapRow(rs, 0));
            });

    when(jdbcTemplate.queryForObject(
            anyString(), ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class)))
        .thenAnswer(
            invocation -> {
              String sql = invocation.getArgument(0);
              ResultSet rs = mock(ResultSet.class);
              if (sql.contains("AS confirmed_ai")) {
                when(rs.getLong("confirmed_ai")).thenReturn(8L);
                when(rs.getLong("pending_ai")).thenReturn(2L);
                when(rs.getLong("removed_ai")).thenReturn(3L);
                when(rs.getLong("manual_active")).thenReturn(4L);
                when(rs.getDouble("avg_conf_pct")).thenReturn(81.5);
                when(rs.getDouble("avg_confirmed_conf_pct")).thenReturn(88.2);
                when(rs.getLong("threshold_eligible_confirmed")).thenReturn(5L);
              } else {
                when(rs.getLong("total_cnt")).thenReturn(4L);
                when(rs.getLong("confirmed_cnt")).thenReturn(3L);
                when(rs.getLong("pending_cnt")).thenReturn(1L);
                when(rs.getLong("removed_cnt")).thenReturn(0L);
              }
              return invocation.<RowMapper<Object>>getArgument(1).mapRow(rs, 0);
            });
  }
}

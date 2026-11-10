package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.request.UpdatePlatformAiSettingsRequest;
import com.smartdroneinspection.inspections.api.dto.response.PlatformAiAnalyticsResponse;
import com.smartdroneinspection.inspections.api.dto.response.PlatformAiAnalyticsResponse.ConfidenceBucketStat;
import com.smartdroneinspection.inspections.api.dto.response.PlatformAiAnalyticsResponse.DefectPerformanceStat;
import com.smartdroneinspection.inspections.api.dto.response.PlatformAiAnalyticsResponse.ModelPerformanceStat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAiSettingsService {

  private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("85.00");

  private final JdbcTemplate jdbcTemplate;

  public PlatformAiSettingsService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public record AiThresholdConfig(
      boolean autoConfirmEnabled,
      BigDecimal autoConfirmThreshold,
      long autoConfirmedCount,
      long rejectedPendingCount,
      Instant updatedAt) {}

  @Transactional(readOnly = true)
  public AiThresholdConfig getThresholdConfig() {
    List<AiThresholdConfig> rows =
        jdbcTemplate.query(
            """
            SELECT auto_confirm_enabled, auto_confirm_threshold,
                   auto_confirmed_count, rejected_pending_count, updated_at
            FROM platform_ai_settings
            WHERE id = 1
            """,
            (rs, rowNum) -> {
              Timestamp ts = rs.getTimestamp("updated_at");
              return new AiThresholdConfig(
                  rs.getBoolean("auto_confirm_enabled"),
                  rs.getBigDecimal("auto_confirm_threshold"),
                  rs.getLong("auto_confirmed_count"),
                  rs.getLong("rejected_pending_count"),
                  ts != null ? ts.toInstant() : Instant.now());
            });
    if (rows.isEmpty()) {
      return new AiThresholdConfig(true, DEFAULT_THRESHOLD, 0L, 0L, Instant.now());
    }
    return rows.getFirst();
  }

  public boolean shouldAutoConfirm(BigDecimal confidence) {
    if (confidence == null) {
      return false;
    }
    AiThresholdConfig config = getThresholdConfig();
    return shouldAutoConfirm(confidence, config);
  }

  public boolean shouldAutoConfirm(BigDecimal confidence, AiThresholdConfig config) {
    if (confidence == null || config == null || !config.autoConfirmEnabled()) {
      return false;
    }
    BigDecimal confidencePct =
        confidence.compareTo(BigDecimal.ONE) <= 0
            ? confidence.multiply(BigDecimal.valueOf(100))
            : confidence;
    return confidencePct.compareTo(config.autoConfirmThreshold()) >= 0;
  }

  @Transactional
  public void recordAutoConfirmed(int count) {
    if (count <= 0) {
      return;
    }
    jdbcTemplate.update(
        """
        UPDATE platform_ai_settings
        SET auto_confirmed_count = auto_confirmed_count + ?,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = 1
        """,
        count);
  }

  @Transactional
  public void recordRejectedPendingSuggestion() {
    jdbcTemplate.update(
        """
        UPDATE platform_ai_settings
        SET rejected_pending_count = rejected_pending_count + 1,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = 1
        """);
  }

  @Transactional
  public PlatformAiAnalyticsResponse updateSettings(
      UUID actorId, UpdatePlatformAiSettingsRequest request) {
    BigDecimal normalizedThreshold =
        request.autoConfirmThreshold().setScale(2, RoundingMode.HALF_UP);
    boolean enabled = Boolean.TRUE.equals(request.autoConfirmEnabled());

    jdbcTemplate.update(
        """
        INSERT INTO platform_ai_settings (id, auto_confirm_enabled, auto_confirm_threshold, updated_by, updated_at)
        VALUES (1, ?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT (id) DO UPDATE
        SET auto_confirm_enabled = EXCLUDED.auto_confirm_enabled,
            auto_confirm_threshold = EXCLUDED.auto_confirm_threshold,
            updated_by = EXCLUDED.updated_by,
            updated_at = CURRENT_TIMESTAMP
        """,
        enabled,
        normalizedThreshold,
        actorId);

    int newlyConfirmed = 0;
    boolean applyToPending = request.applyToPending() == null || request.applyToPending();
    if (enabled && applyToPending) {
      newlyConfirmed =
          jdbcTemplate.update(
              """
              UPDATE findings
              SET status = 'CONFIRMED'
              WHERE is_deleted = FALSE
                AND status = 'PENDING'
                AND model_name IS NOT NULL
                AND confidence IS NOT NULL
                AND (CASE WHEN confidence <= 1 THEN confidence * 100 ELSE confidence END) >= ?
              """,
              normalizedThreshold);
      if (newlyConfirmed > 0) {
        recordAutoConfirmed(newlyConfirmed);
      }
    }

    return buildAnalytics(newlyConfirmed);
  }

  @Transactional(readOnly = true)
  public PlatformAiAnalyticsResponse getAnalytics() {
    return buildAnalytics(0);
  }

  private PlatformAiAnalyticsResponse buildAnalytics(int newlyAutoConfirmedCount) {
    AiThresholdConfig config = getThresholdConfig();

    record AggregateRow(
        long confirmedAi,
        long pendingAi,
        long removedAi,
        long manualActive,
        double avgConfPct,
        double avgConfirmedConfPct,
        long thresholdEligibleConfirmed) {}

    AggregateRow agg =
        jdbcTemplate.queryForObject(
            """
            SELECT
                COUNT(*) FILTER (WHERE model_name IS NOT NULL AND is_deleted = FALSE AND status = 'CONFIRMED') AS confirmed_ai,
                COUNT(*) FILTER (WHERE model_name IS NOT NULL AND is_deleted = FALSE AND status = 'PENDING') AS pending_ai,
                COUNT(*) FILTER (WHERE model_name IS NOT NULL AND is_deleted = TRUE) AS removed_ai,
                COUNT(*) FILTER (WHERE model_name IS NULL AND is_deleted = FALSE) AS manual_active,
                COALESCE(AVG(CASE WHEN confidence <= 1 THEN confidence * 100 ELSE confidence END)
                    FILTER (WHERE model_name IS NOT NULL AND confidence IS NOT NULL), 0) AS avg_conf_pct,
                COALESCE(AVG(CASE WHEN confidence <= 1 THEN confidence * 100 ELSE confidence END)
                    FILTER (WHERE model_name IS NOT NULL AND is_deleted = FALSE AND status = 'CONFIRMED' AND confidence IS NOT NULL), 0) AS avg_confirmed_conf_pct,
                COUNT(*) FILTER (
                    WHERE model_name IS NOT NULL
                      AND is_deleted = FALSE
                      AND status = 'CONFIRMED'
                      AND confidence IS NOT NULL
                      AND (CASE WHEN confidence <= 1 THEN confidence * 100 ELSE confidence END) >= ?
                ) AS threshold_eligible_confirmed
            FROM findings
            """,
            (rs, rowNum) ->
                new AggregateRow(
                    rs.getLong("confirmed_ai"),
                    rs.getLong("pending_ai"),
                    rs.getLong("removed_ai"),
                    rs.getLong("manual_active"),
                    round1(rs.getDouble("avg_conf_pct")),
                    round1(rs.getDouble("avg_confirmed_conf_pct")),
                    rs.getLong("threshold_eligible_confirmed")),
            config.autoConfirmThreshold());

    long confirmedAi = agg != null ? agg.confirmedAi() : 0L;
    long pendingAi = agg != null ? agg.pendingAi() : 0L;
    long removedAi = agg != null ? agg.removedAi() : 0L;
    long manualCount = agg != null ? agg.manualActive() : 0L;
    long rejectedPending = config.rejectedPendingCount();
    long totalRejectedAi = removedAi + rejectedPending;
    long totalAiDetections = confirmedAi + pendingAi + totalRejectedAi;
    long autoConfirmed =
        Math.max(config.autoConfirmedCount(), agg != null ? agg.thresholdEligibleConfirmed() : 0L);

    long decidedAi = confirmedAi + totalRejectedAi;
    double acceptanceRate =
        decidedAi > 0 ? round1((confirmedAi * 100.0) / decidedAi) : (confirmedAi > 0 ? 100.0 : 0.0);
    double rejectionRate = decidedAi > 0 ? round1((totalRejectedAi * 100.0) / decidedAi) : 0.0;

    List<ModelPerformanceStat> modelBreakdown =
        jdbcTemplate.query(
            """
            SELECT
                model_name,
                COUNT(*) AS total_rows,
                COUNT(*) FILTER (WHERE is_deleted = FALSE AND status = 'CONFIRMED') AS confirmed_cnt,
                COUNT(*) FILTER (WHERE is_deleted = FALSE AND status = 'PENDING') AS pending_cnt,
                COUNT(*) FILTER (WHERE is_deleted = TRUE) AS removed_cnt,
                COALESCE(AVG(CASE WHEN confidence <= 1 THEN confidence * 100 ELSE confidence END), 0) AS avg_conf
            FROM findings
            WHERE model_name IS NOT NULL
            GROUP BY model_name
            ORDER BY total_rows DESC, model_name ASC
            """,
            (rs, rowNum) -> {
              long confirmed = rs.getLong("confirmed_cnt");
              long pending = rs.getLong("pending_cnt");
              long removed = rs.getLong("removed_cnt");
              long decided = confirmed + removed;
              double accRate = decided > 0 ? round1((confirmed * 100.0) / decided) : 100.0;
              return new ModelPerformanceStat(
                  rs.getString("model_name"),
                  rs.getLong("total_rows"),
                  confirmed,
                  pending,
                  removed,
                  accRate,
                  round1(rs.getDouble("avg_conf")));
            });

    List<DefectPerformanceStat> defectBreakdown =
        jdbcTemplate.query(
            """
            SELECT
                defect_label,
                COUNT(*) AS total_rows,
                COUNT(*) FILTER (WHERE is_deleted = FALSE AND status = 'CONFIRMED') AS confirmed_cnt,
                COUNT(*) FILTER (WHERE is_deleted = FALSE AND status = 'PENDING') AS pending_cnt,
                COUNT(*) FILTER (WHERE is_deleted = TRUE) AS removed_cnt,
                COALESCE(AVG(CASE WHEN confidence <= 1 THEN confidence * 100 ELSE confidence END), 0) AS avg_conf
            FROM findings
            WHERE model_name IS NOT NULL
            GROUP BY defect_label
            ORDER BY total_rows DESC, defect_label ASC
            LIMIT 10
            """,
            (rs, rowNum) ->
                new DefectPerformanceStat(
                    rs.getString("defect_label"),
                    rs.getLong("total_rows"),
                    rs.getLong("confirmed_cnt"),
                    rs.getLong("pending_cnt"),
                    rs.getLong("removed_cnt"),
                    round1(rs.getDouble("avg_conf"))));

    List<ConfidenceBucketStat> buckets = new ArrayList<>();
    int[][] ranges = {{0, 60}, {60, 75}, {75, 85}, {85, 95}, {95, 101}};
    String[] labels = {"< 60%", "60% – 75%", "75% – 85%", "85% – 95%", "≥ 95%"};
    for (int i = 0; i < ranges.length; i++) {
      int min = ranges[i][0];
      int max = ranges[i][1];
      String label = labels[i];
      ConfidenceBucketStat stat =
          jdbcTemplate.queryForObject(
              """
              SELECT
                  COUNT(*) AS total_cnt,
                  COUNT(*) FILTER (WHERE is_deleted = FALSE AND status = 'CONFIRMED') AS confirmed_cnt,
                  COUNT(*) FILTER (WHERE is_deleted = FALSE AND status = 'PENDING') AS pending_cnt,
                  COUNT(*) FILTER (WHERE is_deleted = TRUE) AS removed_cnt
              FROM findings
              WHERE model_name IS NOT NULL
                AND confidence IS NOT NULL
                AND (CASE WHEN confidence <= 1 THEN confidence * 100 ELSE confidence END) >= ?
                AND (CASE WHEN confidence <= 1 THEN confidence * 100 ELSE confidence END) < ?
              """,
              (rs, rowNum) ->
                  new ConfidenceBucketStat(
                      label,
                      min,
                      Math.min(max, 100),
                      rs.getLong("total_cnt"),
                      rs.getLong("confirmed_cnt"),
                      rs.getLong("pending_cnt"),
                      rs.getLong("removed_cnt")),
              min,
              max);
      if (stat != null) {
        buckets.add(stat);
      }
    }

    return new PlatformAiAnalyticsResponse(
        config.autoConfirmEnabled(),
        config.autoConfirmThreshold(),
        totalAiDetections,
        confirmedAi,
        autoConfirmed,
        pendingAi,
        totalRejectedAi,
        removedAi,
        rejectedPending,
        manualCount,
        acceptanceRate,
        rejectionRate,
        agg != null ? agg.avgConfPct() : 0.0,
        agg != null ? agg.avgConfirmedConfPct() : 0.0,
        config.updatedAt(),
        modelBreakdown,
        defectBreakdown,
        buckets,
        newlyAutoConfirmedCount);
  }

  private static double round1(double val) {
    return Math.round(val * 10.0) / 10.0;
  }
}

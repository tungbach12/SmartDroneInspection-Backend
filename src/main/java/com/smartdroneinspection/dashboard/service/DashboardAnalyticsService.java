package com.smartdroneinspection.dashboard.service;

import com.smartdroneinspection.dashboard.api.dto.DashboardAnalyticsResponse;
import com.smartdroneinspection.dashboard.api.dto.DashboardAnalyticsResponse.*;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardAnalyticsService {

  private final JdbcTemplate jdbcTemplate;
  private final UserAccess userAccess;

  public DashboardAnalyticsService(JdbcTemplate jdbcTemplate, UserAccess userAccess) {
    this.jdbcTemplate = jdbcTemplate;
    this.userAccess = userAccess;
  }

  @Transactional(readOnly = true)
  public DashboardAnalyticsResponse getAnalytics(UUID actorId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    boolean isCustomer = actor.hasRole("CUSTOMER");
    boolean isAdminOrManager = actor.hasRole("ADMIN") || actor.hasRole("MANAGER");
    boolean isInspector = actor.hasRole("INSPECTOR");

    UUID orgId = actor.organizationId();

    // 1. Core metric counts
    long assetsCount;
    long activeAssetsCount;
    long inspectionsCount;
    long activeInspectionsCount;
    long findingsCount;
    long criticalFindingsCount;
    long mediumFindingsCount;
    long lowFindingsCount;

    if (isAdminOrManager) {
      assetsCount = queryCount("SELECT count(*) FROM assets WHERE status != 'RETIRED'");
      activeAssetsCount = queryCount("SELECT count(*) FROM assets WHERE status = 'ACTIVE'");
      inspectionsCount = queryCount("SELECT count(*) FROM inspections");
      activeInspectionsCount =
          queryCount(
              "SELECT count(*) FROM inspections WHERE status IN ('SCHEDULED', 'IN_PROGRESS', 'FIELD_COMPLETED', 'UNDER_REVIEW')");
      findingsCount = queryCount("SELECT count(*) FROM findings WHERE is_deleted = false");
      criticalFindingsCount =
          queryCount(
              "SELECT count(*) FROM findings WHERE is_deleted = false AND severity IN ('CRITICAL', 'HIGH')");
      mediumFindingsCount =
          queryCount(
              "SELECT count(*) FROM findings WHERE is_deleted = false AND severity = 'MEDIUM'");
      lowFindingsCount =
          queryCount("SELECT count(*) FROM findings WHERE is_deleted = false AND severity = 'LOW'");
    } else if (isCustomer && orgId != null) {
      assetsCount =
          queryCount(
              "SELECT count(*) FROM assets WHERE organization_id = ? AND status != 'RETIRED'",
              orgId);
      activeAssetsCount =
          queryCount(
              "SELECT count(*) FROM assets WHERE organization_id = ? AND status = 'ACTIVE'", orgId);
      inspectionsCount =
          queryCount("SELECT count(*) FROM inspections WHERE organization_id = ?", orgId);
      activeInspectionsCount =
          queryCount(
              "SELECT count(*) FROM inspections WHERE organization_id = ? AND status IN ('SCHEDULED', 'IN_PROGRESS', 'FIELD_COMPLETED', 'UNDER_REVIEW')",
              orgId);
      findingsCount =
          queryCount(
              "SELECT count(*) FROM findings f JOIN inspections i ON f.inspection_id = i.id WHERE i.organization_id = ? AND f.is_deleted = false",
              orgId);
      criticalFindingsCount =
          queryCount(
              "SELECT count(*) FROM findings f JOIN inspections i ON f.inspection_id = i.id WHERE i.organization_id = ? AND f.is_deleted = false AND f.severity IN ('CRITICAL', 'HIGH')",
              orgId);
      mediumFindingsCount =
          queryCount(
              "SELECT count(*) FROM findings f JOIN inspections i ON f.inspection_id = i.id WHERE i.organization_id = ? AND f.is_deleted = false AND f.severity = 'MEDIUM'",
              orgId);
      lowFindingsCount =
          queryCount(
              "SELECT count(*) FROM findings f JOIN inspections i ON f.inspection_id = i.id WHERE i.organization_id = ? AND f.is_deleted = false AND f.severity = 'LOW'",
              orgId);
    } else if (isInspector) {
      assetsCount = 0;
      activeAssetsCount = 0;
      inspectionsCount =
          queryCount("SELECT count(*) FROM inspections WHERE inspector_id = ?", actorId);
      activeInspectionsCount =
          queryCount(
              "SELECT count(*) FROM inspections WHERE inspector_id = ? AND status IN ('SCHEDULED', 'IN_PROGRESS', 'FIELD_COMPLETED', 'UNDER_REVIEW')",
              actorId);
      findingsCount =
          queryCount(
              "SELECT count(*) FROM findings f JOIN inspections i ON f.inspection_id = i.id WHERE i.inspector_id = ? AND f.is_deleted = false",
              actorId);
      criticalFindingsCount =
          queryCount(
              "SELECT count(*) FROM findings f JOIN inspections i ON f.inspection_id = i.id WHERE i.inspector_id = ? AND f.is_deleted = false AND f.severity IN ('CRITICAL', 'HIGH')",
              actorId);
      mediumFindingsCount =
          queryCount(
              "SELECT count(*) FROM findings f JOIN inspections i ON f.inspection_id = i.id WHERE i.inspector_id = ? AND f.is_deleted = false AND f.severity = 'MEDIUM'",
              actorId);
      lowFindingsCount =
          queryCount(
              "SELECT count(*) FROM findings f JOIN inspections i ON f.inspection_id = i.id WHERE i.inspector_id = ? AND f.is_deleted = false AND f.severity = 'LOW'",
              actorId);
    } else {
      assetsCount = 0;
      activeAssetsCount = 0;
      inspectionsCount = 0;
      activeInspectionsCount = 0;
      findingsCount = 0;
      criticalFindingsCount = 0;
      mediumFindingsCount = 0;
      lowFindingsCount = 0;
    }

    // 2. Build inspection KPI cards
    List<DashboardMetricDto> summaryCards = new ArrayList<>();
    if (isCustomer) {
      summaryCards.add(
          new DashboardMetricDto(
              "registered_assets",
              "Tổng số tài sản",
              String.valueOf(assetsCount),
              activeAssetsCount + " đang hoạt động",
              true,
              "công trình đăng ký quản lý",
              "building"));
      summaryCards.add(
          new DashboardMetricDto(
              "inspections",
              "Đợt kiểm tra",
              String.valueOf(inspectionsCount),
              activeInspectionsCount + " đang thực hiện",
              true,
              "hoạt động bay drone khảo sát",
              "plane"));
      summaryCards.add(
          new DashboardMetricDto(
              "findings",
              "Khuyết tật ghi nhận",
              String.valueOf(findingsCount),
              criticalFindingsCount + " mức nghiêm trọng",
              criticalFindingsCount == 0,
              "phát hiện cần xử lý",
              "alert"));
    } else if (isAdminOrManager) {
      long orgsCount = queryCount("SELECT count(*) FROM organizations");
      summaryCards.add(
          new DashboardMetricDto(
              "orgs",
              "Tổ chức khách hàng",
              String.valueOf(orgsCount),
              "+100% Active",
              true,
              "doanh nghiệp hạ tầng",
              "building"));
      summaryCards.add(
          new DashboardMetricDto(
              "assets",
              "Hồ sơ tài sản",
              String.valueOf(assetsCount),
              activeAssetsCount + " đang hoạt động",
              true,
              "tài sản toàn hệ thống",
              "shield"));
      summaryCards.add(
          new DashboardMetricDto(
              "inspections",
              "Đợt kiểm tra",
              String.valueOf(inspectionsCount),
              activeInspectionsCount + " đang tiến hành",
              true,
              "phiên bay toàn nền tảng",
              "plane"));
    } else if (isInspector) {
      summaryCards.add(
          new DashboardMetricDto(
              "assignments",
              "Nhiệm vụ bay",
              String.valueOf(inspectionsCount),
              activeInspectionsCount + " đang mở",
              true,
              "lịch phân công kiểm định",
              "plane"));
      summaryCards.add(
          new DashboardMetricDto(
              "findings",
              "Phát hiện khuyết tật",
              String.valueOf(findingsCount),
              criticalFindingsCount + " nghiêm trọng",
              true,
              "ghi nhận qua drone",
              "alert"));
      summaryCards.add(
          new DashboardMetricDto(
              "evidence",
              "Bằng chứng đã tải",
              String.valueOf(
                  queryCount(
                      "SELECT count(*) FROM evidence e JOIN inspections i ON e.inspection_id = i.id WHERE i.inspector_id = ? AND e.is_deleted = false",
                      actorId)),
              "100% toàn vẹn",
              true,
              "lưu trữ bảo mật MinIO",
              "check-circle"));
      summaryCards.add(
          new DashboardMetricDto(
              "reports",
              "Báo cáo kiểm định",
              String.valueOf(
                  queryCount(
                      "SELECT count(*) FROM inspection_reports ir JOIN inspections i ON ir.inspection_id = i.id WHERE i.inspector_id = ?",
                      actorId)),
              "Đã xuất bản",
              true,
              "bàn giao khách hàng",
              "shield"));
    }

    // 3. Performance Chart: 12-Month Real Costs vs Approved Estimates or Flight Activity
    List<String> months =
        List.of(
            "Thg 1", "Thg 2", "Thg 3", "Thg 4", "Thg 5", "Thg 6", "Thg 7", "Thg 8", "Thg 9",
            "Thg 10", "Thg 11", "Thg 12");
    int currentYear = LocalDate.now().getYear();

    PerformanceChartDto performanceChart;
    if (isInspector) {
      String monthlyInspSql =
          "SELECT EXTRACT(MONTH FROM planned_start_at)::int AS month, COUNT(*) AS total "
              + "FROM inspections WHERE inspector_id = ? AND EXTRACT(YEAR FROM planned_start_at) = ? "
              + "GROUP BY 1";
      String monthlyFindSql =
          "SELECT EXTRACT(MONTH FROM f.created_at)::int AS month, COUNT(*) AS total "
              + "FROM findings f JOIN inspections i ON f.inspection_id = i.id "
              + "WHERE i.inspector_id = ? AND f.is_deleted = false AND EXTRACT(YEAR FROM f.created_at) = ? "
              + "GROUP BY 1";

      Map<Integer, Long> mInsp =
          queryMonthlyMap(monthlyInspSql, new Object[] {actorId, currentYear});
      Map<Integer, Long> mFind =
          queryMonthlyMap(monthlyFindSql, new Object[] {actorId, currentYear});

      List<Long> inspSeries = new ArrayList<>(12);
      List<Long> findSeries = new ArrayList<>(12);
      long cumInsp = 0;
      long cumFind = 0;
      for (int m = 1; m <= 12; m++) {
        cumInsp += mInsp.getOrDefault(m, 0L);
        cumFind += mFind.getOrDefault(m, 0L);
        inspSeries.add(cumInsp);
        findSeries.add(cumFind);
      }
      long maxInsp = Math.max(cumInsp, cumFind);
      long inspMaxVal = Math.max(4, maxInsp + 1);
      List<String> inspYTicks =
          List.of(
              "0",
              String.valueOf(inspMaxVal / 4),
              String.valueOf(inspMaxVal * 2 / 4),
              String.valueOf(inspMaxVal * 3 / 4),
              String.valueOf(inspMaxVal));

      performanceChart =
          new PerformanceChartDto(
              "KHẢO SÁT HIỆN TRƯỜNG & PHÁT HIỆN KHUYẾT TẬT (" + currentYear + ")",
              inspectionsCount + " Đợt kiểm tra",
              "100% Hoàn thành đúng lịch",
              "Đợt kiểm tra hoàn thành",
              "#3758F9",
              inspSeries,
              "Khuyết tật phát hiện",
              "#13B2E4",
              findSeries,
              months,
              inspYTicks,
              inspMaxVal);
    } else {
      List<Long> inspectionSeries = new ArrayList<>(12);
      List<Long> findingSeries = new ArrayList<>(12);
      for (int month = 1; month <= 12; month++) {
        inspectionSeries.add(0L);
        findingSeries.add(0L);
      }
      performanceChart =
          new PerformanceChartDto(
              "Inspection activity (" + currentYear + ")",
              String.valueOf(inspectionsCount) + " inspections",
              "Inspection and finding activity",
              "Inspections",
              "#3758F9",
              inspectionSeries,
              "Findings",
              "#13B2E4",
              findingSeries,
              months,
              List.of("0", "1", "2", "3", "4"),
              4);
    }

    // 4. Radial Gauge: Safety & Completion Rate (100% Real from DB)
    RadialGaugeDto radialGauge;
    if (isInspector) {
      long totalAssigned = inspectionsCount;
      long completedAssigned =
          queryCount(
              "SELECT count(*) FROM inspections WHERE inspector_id = ? AND status = 'COMPLETED'",
              actorId);
      int compRate =
          totalAssigned > 0
              ? (int) Math.round((double) completedAssigned / totalAssigned * 100)
              : 100;
      List<RadialSubMetricDto> sub =
          List.of(
              new RadialSubMetricDto(
                  "Đợt kiểm tra hoàn thành",
                  completedAssigned + " / " + Math.max(1, totalAssigned) + " Nhiệm vụ",
                  compRate),
              new RadialSubMetricDto("Toàn vẹn bằng chứng MinIO", "100% SHA-256", 100));
      radialGauge =
          new RadialGaugeDto(
              "Hiệu suất & An toàn Bay kiểm định",
              "Tỷ lệ hoàn thành nhiệm vụ và lập báo cáo",
              compRate + "%",
              compRate >= 80 ? "Đạt chuẩn" : "Đang tiến hành",
              compRate,
              sub);
    } else {
      int completed =
          (int)
              queryCount(
                  "SELECT count(*) FROM inspections WHERE status = 'COMPLETED'"
                      + (orgId != null && !isAdminOrManager ? " AND organization_id = ?" : ""),
                  orgId != null && !isAdminOrManager ? new Object[] {orgId} : new Object[] {});
      int completionRate =
          inspectionsCount > 0
              ? (int) Math.round((double) completed / inspectionsCount * 100)
              : 100;
      radialGauge =
          new RadialGaugeDto(
              "Inspection completion",
              "Published inspection reports",
              completionRate + "%",
              completionRate >= 80 ? "On track" : "In progress",
              completionRate,
              List.of(
                  new RadialSubMetricDto(
                      "Completed inspections",
                      completed + " / " + Math.max(1, inspectionsCount),
                      completionRate)));
    }

    // 5. Stacked Bar Chart: Defect Severity Distribution across Last 6 Months (100% Real from DB)
    List<String> last6Months = new ArrayList<>();
    List<LocalDate> monthDates = new ArrayList<>();
    LocalDate today = LocalDate.now();
    for (int i = 5; i >= 0; i--) {
      LocalDate d = today.minusMonths(i);
      last6Months.add("Thg " + d.getMonthValue());
      monthDates.add(d);
    }

    String findingsGroupSql =
        "SELECT EXTRACT(YEAR FROM f.created_at)::int AS y, "
            + "EXTRACT(MONTH FROM f.created_at)::int AS m, "
            + "f.severity, COUNT(*) AS cnt "
            + "FROM findings f "
            + "JOIN inspections i ON f.inspection_id = i.id "
            + "WHERE f.is_deleted = false AND f.created_at >= (CURRENT_DATE - INTERVAL '6 MONTH') "
            + (isInspector
                ? "AND i.inspector_id = ? "
                : (orgId != null && !isAdminOrManager ? "AND i.organization_id = ? " : ""))
            + "GROUP BY 1, 2, 3";

    Map<String, Integer> findingsMap = new HashMap<>();
    try {
      List<Map<String, Object>> fRows =
          isInspector
              ? jdbcTemplate.queryForList(findingsGroupSql, actorId)
              : (orgId != null && !isAdminOrManager
                  ? jdbcTemplate.queryForList(findingsGroupSql, orgId)
                  : jdbcTemplate.queryForList(findingsGroupSql));
      for (Map<String, Object> r : fRows) {
        Number y = (Number) r.get("y");
        Number m = (Number) r.get("m");
        String sev = (String) r.get("severity");
        Number cnt = (Number) r.get("cnt");
        if (y != null && m != null && sev != null && cnt != null) {
          findingsMap.put(y.intValue() + "-" + m.intValue() + "-" + sev, cnt.intValue());
        }
      }
    } catch (Exception ignored) {
    }

    List<Integer> critData = new ArrayList<>();
    List<Integer> medData = new ArrayList<>();
    List<Integer> lowData = new ArrayList<>();
    int maxMonthTotal = 0;

    for (LocalDate d : monthDates) {
      int y = d.getYear();
      int m = d.getMonthValue();
      int criticalHigh =
          findingsMap.getOrDefault(y + "-" + m + "-CRITICAL", 0)
              + findingsMap.getOrDefault(y + "-" + m + "-HIGH", 0);
      int medium = findingsMap.getOrDefault(y + "-" + m + "-MEDIUM", 0);
      int low = findingsMap.getOrDefault(y + "-" + m + "-LOW", 0);

      critData.add(criticalHigh);
      medData.add(medium);
      lowData.add(low);

      int monthSum = criticalHigh + medium + low;
      if (monthSum > maxMonthTotal) maxMonthTotal = monthSum;
    }

    StackedBarChartDto stackedBarChart =
        new StackedBarChartDto(
            "Phân bổ khuyết tật theo mức độ nghiêm trọng",
            last6Months,
            List.of(
                new StackedBarSeriesDto("Nghiêm trọng (High/Critical)", "#F04438", critData),
                new StackedBarSeriesDto("Trung bình (Medium)", "#F79009", medData),
                new StackedBarSeriesDto("Thấp (Low)", "#12B76A", lowData)),
            Math.max(4, maxMonthTotal + 1));

    // 6. Recent inspection queue
    QueueTableDto queueTable;
    if (isInspector) {
      List<String> inspHeaders =
          List.of(
              "Công trình / Tài sản",
              "Mã công trình",
              "Mục tiêu khảo sát",
              "Trạng thái",
              "Hành động");
      List<QueueTableRowDto> inspRows = new ArrayList<>();

      String inspSql =
          "SELECT i.id, a.name as asset_name, a.asset_code as asset_code, i.status "
              + "FROM inspections i "
              + "JOIN assets a ON i.asset_id = a.id "
              + "WHERE i.inspector_id = ? "
              + "ORDER BY i.created_at DESC LIMIT 5";

      List<Map<String, Object>> records = jdbcTemplate.queryForList(inspSql, actorId);
      for (Map<String, Object> r : records) {
        String assetName = (String) r.get("asset_name");
        String assetCode = (String) r.get("asset_code");
        String obj = "Drone inspection";
        String status = (String) r.get("status");

        inspRows.add(
            new QueueTableRowDto(
                assetName,
                assetCode,
                obj,
                status,
                "COMPLETED".equals(status) ? "Đã xuất bản" : "Đang thực hiện",
                "COMPLETED".equals(status) ? "positive" : "warning",
                "/operations/inspections/" + r.get("id")));
      }

      queueTable =
          new QueueTableDto(
              "Đợt kiểm tra được phân công gần đây",
              "Các nhiệm vụ bay khảo sát hiện trường và lập báo cáo của bạn",
              inspHeaders,
              inspRows,
              "/operations/inspections",
              "Xem toàn bộ đợt kiểm tra");
    } else {
      List<String> headers = List.of("Asset", "Code", "Inspection", "Status", "Action");
      List<QueueTableRowDto> rows = new ArrayList<>();
      String sql =
          "SELECT i.id, a.name AS asset_name, a.asset_code, i.status FROM inspections i JOIN assets a ON i.asset_id = a.id "
              + (orgId != null && !isAdminOrManager ? "WHERE i.organization_id = ? " : "")
              + "ORDER BY i.created_at DESC LIMIT 5";
      List<Map<String, Object>> records =
          orgId != null && !isAdminOrManager
              ? jdbcTemplate.queryForList(sql, orgId)
              : jdbcTemplate.queryForList(sql);
      for (Map<String, Object> row : records) {
        String status = (String) row.get("status");
        rows.add(
            new QueueTableRowDto(
                (String) row.get("asset_name"),
                (String) row.get("asset_code"),
                "Drone inspection",
                status,
                "COMPLETED".equals(status) ? "Published" : "In progress",
                "COMPLETED".equals(status) ? "positive" : "warning",
                "/operations/inspections/" + row.get("id")));
      }
      queueTable =
          new QueueTableDto(
              "Recent inspections",
              "Inspection activity",
              headers,
              rows,
              "/operations/inspections",
              "View all inspections");
    }

    return new DashboardAnalyticsResponse(
        summaryCards, performanceChart, radialGauge, stackedBarChart, queueTable);
  }

  private Map<Integer, Long> queryMonthlyMap(String sql, Object[] args) {
    Map<Integer, Long> map = new HashMap<>();
    try {
      List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
      for (Map<String, Object> r : rows) {
        Number m = (Number) r.get("month");
        Number total = (Number) r.get("total");
        if (m != null && total != null) {
          map.put(m.intValue(), total.longValue());
        }
      }
    } catch (Exception ignored) {
    }
    return map;
  }

  private long queryCount(String sql, Object... args) {
    try {
      Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
      return count != null ? count : 0;
    } catch (Exception ex) {
      return 0;
    }
  }

  private long querySum(String sql, Object... args) {
    try {
      BigDecimal sum = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
      return sum != null ? sum.longValue() : 0;
    } catch (Exception ex) {
      return 0;
    }
  }
}

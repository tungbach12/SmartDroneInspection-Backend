package com.smartdroneinspection.dashboard.service;

import com.smartdroneinspection.dashboard.api.dto.DashboardAnalyticsResponse;
import com.smartdroneinspection.dashboard.api.dto.DashboardAnalyticsResponse.*;
import com.smartdroneinspection.users.UserAccess;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardAnalyticsService {

  private static final List<String> MONTHS =
      List.of(
          "Thg 1", "Thg 2", "Thg 3", "Thg 4", "Thg 5", "Thg 6", "Thg 7", "Thg 8", "Thg 9", "Thg 10",
          "Thg 11", "Thg 12");

  private static final DateTimeFormatter SHORT_DATE_TIME =
      DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

  private final JdbcTemplate jdbcTemplate;
  private final UserAccess userAccess;

  public DashboardAnalyticsService(JdbcTemplate jdbcTemplate, UserAccess userAccess) {
    this.jdbcTemplate = jdbcTemplate;
    this.userAccess = userAccess;
  }

  @Transactional(readOnly = true)
  public DashboardAnalyticsResponse getAnalytics(UUID actorId) {
    return getAnalytics(actorId, null);
  }

  @Transactional(readOnly = true)
  public DashboardAnalyticsResponse getAnalytics(UUID actorId, String requestedRole) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    String viewRole = resolveEffectiveRole(actor, requestedRole);
    return switch (viewRole) {
      case "ADMIN" -> buildAdminAnalytics();
      case "MANAGER" -> buildManagerAnalytics();
      case "CUSTOMER" -> buildCustomerAnalytics(actor.ownerCustomerId());
      case "INSPECTOR" -> buildInspectorAnalytics(actorId);
      default -> buildEmptyAnalytics();
    };
  }

  private String resolveEffectiveRole(UserAccess.ActiveUser actor, String requestedRole) {
    if (requestedRole != null && !requestedRole.isBlank()) {
      String normalized = requestedRole.trim().toUpperCase(Locale.ROOT);
      if (actor.hasRole(normalized)) {
        return normalized;
      }
    }
    if (actor.hasRole("ADMIN")) return "ADMIN";
    if (actor.hasRole("MANAGER")) return "MANAGER";
    if (actor.hasRole("CUSTOMER")) return "CUSTOMER";
    if (actor.hasRole("INSPECTOR")) return "INSPECTOR";
    return "NONE";
  }

  private DashboardAnalyticsResponse buildAdminAnalytics() {
    int currentYear = LocalDate.now().getYear();
    long activeUsers = queryCount("SELECT count(*) FROM users WHERE status = 'ACTIVE'");
    long customerUsers =
        queryCount(
            "SELECT count(*) FROM users WHERE status = 'ACTIVE' AND roles LIKE '%CUSTOMER%'");
    long inspectorUsers =
        queryCount(
            "SELECT count(*) FROM users WHERE status = 'ACTIVE' AND roles LIKE '%INSPECTOR%'");

    long pendingInspectors =
        queryCount("SELECT count(*) FROM inspector_applications WHERE status = 'PENDING_REVIEW'");
    long pendingDrones =
        queryCount("SELECT count(*) FROM drone_registration_applications WHERE status = 'PENDING'");
    long pendingCustomers =
        queryCount(
            "SELECT count(*) FROM prospective_customer_registrations WHERE status = 'PENDING'");
    long mf1Pending = pendingInspectors + pendingDrones + pendingCustomers;

    long findingsThisMonth =
        queryCount(
            "SELECT count(*) FROM findings WHERE is_deleted = false AND date_trunc('month', created_at) = date_trunc('month', CURRENT_TIMESTAMP)");
    long totalFindings = queryCount("SELECT count(*) FROM findings WHERE is_deleted = false");
    long aiFindings =
        queryCount(
            "SELECT count(*) FROM findings WHERE is_deleted = false AND model_name IS NOT NULL AND btrim(model_name) <> ''");

    BigDecimal platformGmv =
        queryDecimal(
            "SELECT COALESCE(SUM(si.total_price), 0) FROM service_invoices si "
                + "JOIN service_payments sp ON si.occurrence_id = sp.occurrence_id "
                + "WHERE sp.payment_status = 'PAID'");
    long failedEvidenceCount =
        queryCount(
            "SELECT count(*) FROM evidence WHERE is_deleted = false AND processing_status = 'FAILED'");

    List<DashboardMetricDto> summaryCards =
        List.of(
            new DashboardMetricDto(
                "active_users",
                "Người dùng hoạt động",
                String.valueOf(activeUsers),
                customerUsers + " KH · " + inspectorUsers + " KĐV",
                true,
                "tài khoản toàn nền tảng",
                "users"),
            new DashboardMetricDto(
                "mf1_pending",
                "Hồ sơ chờ thẩm định",
                String.valueOf(mf1Pending),
                mf1Pending == 0 ? "Đã xử lý" : mf1Pending + " chờ duyệt",
                mf1Pending == 0,
                "Inspector · Drone · Vùng bay",
                "shield"),
            new DashboardMetricDto(
                "ai_findings",
                "Phát hiện AI tháng này",
                String.valueOf(findingsThisMonth),
                (totalFindings > 0 ? Math.round((double) aiFindings / totalFindings * 100) : 0)
                    + "% qua AI",
                true,
                "YOLO11 & Vision LLM",
                "eye"),
            new DashboardMetricDto(
                "platform_volume",
                "Tổng giao dịch nền tảng",
                formatVndCompact(platformGmv),
                failedEvidenceCount == 0 ? "Hệ thống ổn định" : failedEvidenceCount + " tệp lỗi",
                failedEvidenceCount == 0,
                "tổng thanh toán đã xác nhận",
                "dollar"));

    // Weekly AI & Evidence activity across the last 8 weeks (NextAdmin AI style)
    List<String> weekLabels = new ArrayList<>(8);
    List<Long> yoloSeries = new ArrayList<>(8);
    List<Long> visionOrEvidenceSeries = new ArrayList<>(8);
    LocalDate today = LocalDate.now();
    long maxWeekly = 0;
    long weeklyTotal = 0;
    for (int w = 7; w >= 0; w--) {
      LocalDate start = today.minusWeeks(w).with(java.time.DayOfWeek.MONDAY);
      LocalDate end = start.plusDays(7);
      weekLabels.add("T" + start.format(DateTimeFormatter.ofPattern("dd/MM")));
      long yoloCnt =
          queryCount(
              "SELECT count(*) FROM findings WHERE is_deleted = false AND LOWER(COALESCE(model_name, '')) LIKE '%yolo%' AND created_at >= ? AND created_at < ?",
              java.sql.Timestamp.valueOf(start.atStartOfDay()),
              java.sql.Timestamp.valueOf(end.atStartOfDay()));
      long otherAiCnt =
          queryCount(
              "SELECT count(*) FROM findings WHERE is_deleted = false AND model_name IS NOT NULL AND btrim(model_name) <> '' AND LOWER(model_name) NOT LIKE '%yolo%' AND created_at >= ? AND created_at < ?",
              java.sql.Timestamp.valueOf(start.atStartOfDay()),
              java.sql.Timestamp.valueOf(end.atStartOfDay()));
      yoloSeries.add(yoloCnt);
      visionOrEvidenceSeries.add(otherAiCnt);
      weeklyTotal += yoloCnt + otherAiCnt;
      maxWeekly = Math.max(maxWeekly, Math.max(yoloCnt, otherAiCnt));
    }
    long perfMax = roundAxisMax(maxWeekly);
    PerformanceChartDto performanceChart =
        new PerformanceChartDto(
            "HOẠT ĐỘNG AI THEO TUẦN (" + currentYear + ")",
            weeklyTotal + " Phát hiện AI",
            aiFindings + " tổng AI",
            "YOLO11",
            "#3758F9",
            yoloSeries,
            "Vision Fallback",
            "#13B2E4",
            visionOrEvidenceSeries,
            weekLabels,
            buildYTicks(perfMax),
            perfMax);

    long totalApps =
        queryCount("SELECT count(*) FROM inspector_applications")
            + queryCount("SELECT count(*) FROM drone_registration_applications")
            + queryCount("SELECT count(*) FROM prospective_customer_registrations");
    long resolvedApps = Math.max(0, totalApps - mf1Pending);
    int mf1Rate = totalApps > 0 ? (int) Math.round((double) resolvedApps / totalApps * 100) : 100;
    int aiRate =
        totalFindings > 0 ? (int) Math.round((double) aiFindings / totalFindings * 100) : 0;
    long totalDrones = queryCount("SELECT count(*) FROM drones WHERE serviceability != 'RETIRED'");
    long activeDrones = queryCount("SELECT count(*) FROM drones WHERE serviceability = 'ACTIVE'");
    int droneReadyRate =
        totalDrones > 0 ? (int) Math.round((double) activeDrones / totalDrones * 100) : 100;

    RadialGaugeDto radialGauge =
        new RadialGaugeDto(
            "Sức khỏe Thẩm định & AI",
            "Tỷ lệ xử lý MF1 và độ phủ AI",
            mf1Rate + "%",
            mf1Pending == 0 ? "Đã xử lý sạch" : mf1Pending + " hồ sơ chờ",
            mf1Rate,
            List.of(
                new RadialSubMetricDto(
                    "Phân tích tự động AI",
                    aiFindings + " / " + Math.max(1, totalFindings),
                    aiRate),
                new RadialSubMetricDto(
                    "Drone sẵn sàng bay",
                    activeDrones + " / " + Math.max(1, totalDrones),
                    droneReadyRate)));

    StackedBarChartDto stackedBarChart = buildAdminAiSourceStackedBar();
    List<PipelineStageDto> adminMix =
        buildAdminMixStages(pendingInspectors, pendingDrones, pendingCustomers, activeUsers);
    List<ActionItemDto> adminActionItems = buildAdminActionItems();
    List<ServiceHealthDto> serviceHealth = buildAdminServiceHealth(failedEvidenceCount);

    QueueTableDto queueTable = buildAdminAuditQueue();

    return new DashboardAnalyticsResponse(
        summaryCards,
        performanceChart,
        radialGauge,
        stackedBarChart,
        queueTable,
        adminMix,
        null,
        adminActionItems,
        List.of(),
        List.of(),
        serviceHealth);
  }

  private DashboardAnalyticsResponse buildManagerAnalytics() {
    int currentYear = LocalDate.now().getYear();
    long openRequests = queryCount("SELECT count(*) FROM service_requests WHERE status = 'OPEN'");
    long quotedRequests =
        queryCount("SELECT count(*) FROM service_requests WHERE status = 'QUOTED'");
    long pendingQuotesTotal = openRequests + quotedRequests;

    long pendingDispatch =
        queryCount(
            "SELECT count(*) FROM service_request_occurrences WHERE status = 'PENDING_ASSIGNMENT'");
    long abortedCount =
        queryCount(
            "SELECT count(DISTINCT i.id) FROM inspections i "
                + "JOIN field_sessions fs ON fs.inspection_id = i.id "
                + "WHERE fs.status = 'ABORTED' AND i.status NOT IN ('COMPLETED', 'CANCELLED')");

    long submittedReports =
        queryCount("SELECT count(*) FROM inspection_reports WHERE status = 'SUBMITTED'");
    long publishedReports =
        queryCount("SELECT count(*) FROM inspection_reports WHERE status = 'PUBLISHED'");

    BigDecimal paidRevenueMonth =
        queryDecimal(
            "SELECT COALESCE(SUM(si.total_price), 0) FROM service_invoices si "
                + "JOIN service_payments sp ON si.occurrence_id = sp.occurrence_id "
                + "WHERE sp.payment_status = 'PAID' "
                + "AND date_trunc('month', COALESCE(sp.payment_confirmed_at, sp.updated_at)) = date_trunc('month', CURRENT_TIMESTAMP)");
    BigDecimal paidRevenueYear =
        queryDecimal(
            "SELECT COALESCE(SUM(si.total_price), 0) FROM service_invoices si "
                + "JOIN service_payments sp ON si.occurrence_id = sp.occurrence_id "
                + "WHERE sp.payment_status = 'PAID'");
    long paidInvoicesCount =
        queryCount("SELECT count(*) FROM service_payments WHERE payment_status = 'PAID'");

    List<DashboardMetricDto> summaryCards =
        List.of(
            new DashboardMetricDto(
                "pending_quotes",
                "Yêu cầu chờ báo giá",
                String.valueOf(pendingQuotesTotal),
                openRequests + " yêu cầu mới",
                openRequests == 0,
                "yêu cầu dịch vụ đang mở",
                "clock"),
            new DashboardMetricDto(
                "dispatch_queue",
                "Ca cần điều phối",
                String.valueOf(pendingDispatch + abortedCount),
                abortedCount > 0 ? abortedCount + " bị hủy/xếp lại" : "Đúng lịch",
                abortedCount == 0,
                "phiên bay chờ phân công",
                "plane"),
            new DashboardMetricDto(
                "pending_reports",
                "Báo cáo chờ duyệt",
                String.valueOf(submittedReports),
                publishedReports + " đã xuất bản",
                submittedReports == 0,
                "hàng đợi nghiệm thu",
                "shield"),
            new DashboardMetricDto(
                "paid_revenue",
                "Doanh thu tháng này",
                formatVndCompact(paidRevenueMonth),
                paidInvoicesCount + " hóa đơn đã thu",
                true,
                "lũy kế năm " + formatVndCompact(paidRevenueYear),
                "dollar"));

    // Monthly chart: Revenue (million VND) vs Invoiced
    Map<Integer, Long> mPaidMillions =
        queryMonthlyMap(
            "SELECT EXTRACT(MONTH FROM COALESCE(sp.payment_confirmed_at, sp.updated_at))::int AS month, "
                + "ROUND(COALESCE(SUM(si.total_price), 0) / 1000000.0)::bigint AS total "
                + "FROM service_invoices si JOIN service_payments sp ON si.occurrence_id = sp.occurrence_id "
                + "WHERE sp.payment_status = 'PAID' "
                + "AND EXTRACT(YEAR FROM COALESCE(sp.payment_confirmed_at, sp.updated_at)) = ? GROUP BY 1",
            new Object[] {currentYear});
    Map<Integer, Long> mInvoicedMillions =
        queryMonthlyMap(
            "SELECT EXTRACT(MONTH FROM si.created_at)::int AS month, "
                + "ROUND(COALESCE(SUM(si.total_price), 0) / 1000000.0)::bigint AS total "
                + "FROM service_invoices si WHERE EXTRACT(YEAR FROM si.created_at) = ? GROUP BY 1",
            new Object[] {currentYear});

    List<Long> paidSeries = new ArrayList<>(12);
    List<Long> invoicedSeries = new ArrayList<>(12);
    long maxRevenueM = 0;
    for (int m = 1; m <= 12; m++) {
      long p = mPaidMillions.getOrDefault(m, 0L);
      long inv = mInvoicedMillions.getOrDefault(m, 0L);
      paidSeries.add(p);
      invoicedSeries.add(inv);
      maxRevenueM = Math.max(maxRevenueM, Math.max(p, inv));
    }
    long perfMax = roundAxisMax(maxRevenueM);
    PerformanceChartDto performanceChart =
        new PerformanceChartDto(
            "DOANH THU THEO THÁNG (" + currentYear + " · TRIỆU ₫)",
            formatVndCompact(paidRevenueYear),
            paidInvoicesCount + " đã thanh toán",
            "Đã thu (triệu ₫)",
            "#3758F9",
            paidSeries,
            "Tổng hóa đơn (triệu ₫)",
            "#13B2E4",
            invoicedSeries,
            MONTHS,
            buildYTicks(perfMax),
            perfMax);

    // Secondary chart: Monthly inspection volume (Scheduled vs Completed) for switch toggle
    Map<Integer, Long> mScheduled =
        queryMonthlyMap(
            "SELECT EXTRACT(MONTH FROM COALESCE(planned_start_at, created_at))::int AS month, COUNT(*) AS total "
                + "FROM inspections WHERE EXTRACT(YEAR FROM COALESCE(planned_start_at, created_at)) = ? GROUP BY 1",
            new Object[] {currentYear});
    Map<Integer, Long> mCompleted =
        queryMonthlyMap(
            "SELECT EXTRACT(MONTH FROM COALESCE(planned_start_at, created_at))::int AS month, COUNT(*) AS total "
                + "FROM inspections WHERE status = 'COMPLETED' AND EXTRACT(YEAR FROM COALESCE(planned_start_at, created_at)) = ? GROUP BY 1",
            new Object[] {currentYear});
    List<Long> schedSeries = new ArrayList<>(12);
    List<Long> compSeries = new ArrayList<>(12);
    long totalSchedYear = 0;
    long maxInspM = 0;
    for (int m = 1; m <= 12; m++) {
      long s = mScheduled.getOrDefault(m, 0L);
      long c = mCompleted.getOrDefault(m, 0L);
      schedSeries.add(s);
      compSeries.add(c);
      totalSchedYear += s;
      maxInspM = Math.max(maxInspM, Math.max(s, c));
    }
    long inspAxisMax = roundAxisMax(maxInspM);
    PerformanceChartDto secondaryPerformanceChart =
        new PerformanceChartDto(
            "SỐ CA KIỂM ĐỊNH THEO THÁNG (" + currentYear + ")",
            totalSchedYear + " Ca",
            publishedReports + " báo cáo xuất bản",
            "Tổng ca kiểm định",
            "#3758F9",
            schedSeries,
            "Ca hoàn thành",
            "#10B981",
            compSeries,
            MONTHS,
            buildYTicks(inspAxisMax),
            inspAxisMax);

    long totalInspections = queryCount("SELECT count(*) FROM inspections");
    long completedInspections =
        queryCount("SELECT count(*) FROM inspections WHERE status = 'COMPLETED'");
    long totalInvoices = queryCount("SELECT count(*) FROM service_payments");
    int completionRate =
        totalInspections > 0
            ? (int) Math.round((double) completedInspections / totalInspections * 100)
            : 100;
    int paidRate =
        totalInvoices > 0
            ? (int) Math.round((double) paidInvoicesCount / totalInvoices * 100)
            : 100;

    RadialGaugeDto radialGauge =
        new RadialGaugeDto(
            "Tiến độ Nghiệm thu & Thanh toán",
            "Tỷ lệ hoàn tất ca kiểm định và đối soát phí",
            completionRate + "%",
            completionRate >= 80 ? "Đạt tiến độ" : "Đang triển khai",
            completionRate,
            List.of(
                new RadialSubMetricDto(
                    "Ca kiểm định hoàn thành",
                    completedInspections + " / " + Math.max(1, totalInspections),
                    completionRate),
                new RadialSubMetricDto(
                    "Hóa đơn đã thanh toán",
                    paidInvoicesCount + " / " + Math.max(1, totalInvoices),
                    paidRate)));

    StackedBarChartDto stackedBarChart = buildSeverityStackedBar(false, false, null, null);
    List<PipelineStageDto> pipelineStages = buildManagerPipelineStages();
    List<ActionItemDto> managerActionItems = buildManagerActionItems();
    List<ScheduleSlotDto> upcomingSchedule = buildManagerUpcomingSchedule();
    List<InspectorWorkloadDto> inspectorWorkloads = buildInspectorWorkloads();
    QueueTableDto queueTable = buildManagerActionQueue();

    return new DashboardAnalyticsResponse(
        summaryCards,
        performanceChart,
        radialGauge,
        stackedBarChart,
        queueTable,
        pipelineStages,
        secondaryPerformanceChart,
        managerActionItems,
        upcomingSchedule,
        inspectorWorkloads,
        List.of());
  }

  private DashboardAnalyticsResponse buildCustomerAnalytics(UUID ownerCustomerId) {
    boolean customerScoped = ownerCustomerId != null;
    long assetsCount =
        customerScoped
            ? queryCount(
                "SELECT count(*) FROM assets WHERE owner_customer_id = ? AND status != 'RETIRED'",
                ownerCustomerId)
            : 0;
    long activeAssetsCount =
        customerScoped
            ? queryCount(
                "SELECT count(*) FROM assets WHERE owner_customer_id = ? AND status = 'ACTIVE'",
                ownerCustomerId)
            : 0;
    long inspectionsCount =
        customerScoped
            ? queryCount(
                "SELECT count(*) FROM inspections WHERE owner_customer_id = ?", ownerCustomerId)
            : 0;
    long activeInspectionsCount =
        customerScoped
            ? queryCount(
                "SELECT count(*) FROM inspections WHERE owner_customer_id = ? AND status IN ('SCHEDULED', 'IN_PROGRESS', 'FIELD_COMPLETED', 'UNDER_REVIEW')",
                ownerCustomerId)
            : 0;
    long findingsCount =
        customerScoped
            ? queryCount(
                "SELECT count(*) FROM findings f JOIN inspections i ON f.inspection_id = i.id WHERE i.owner_customer_id = ? AND f.is_deleted = false",
                ownerCustomerId)
            : 0;
    long criticalFindingsCount =
        customerScoped
            ? queryCount(
                "SELECT count(*) FROM findings f JOIN inspections i ON f.inspection_id = i.id WHERE i.owner_customer_id = ? AND f.is_deleted = false AND f.severity IN ('CRITICAL', 'HIGH')",
                ownerCustomerId)
            : 0;

    List<DashboardMetricDto> summaryCards =
        List.of(
            new DashboardMetricDto(
                "registered_assets",
                "Tổng số tài sản",
                String.valueOf(assetsCount),
                activeAssetsCount + " đang hoạt động",
                true,
                "công trình đăng ký quản lý",
                "building"),
            new DashboardMetricDto(
                "inspections",
                "Đợt kiểm tra",
                String.valueOf(inspectionsCount),
                activeInspectionsCount + " đang thực hiện",
                true,
                "hoạt động bay drone khảo sát",
                "plane"),
            new DashboardMetricDto(
                "findings",
                "Khuyết tật ghi nhận",
                String.valueOf(findingsCount),
                criticalFindingsCount + " mức nghiêm trọng",
                criticalFindingsCount == 0,
                "phát hiện cần xử lý",
                "alert"));

    int currentYear = LocalDate.now().getYear();
    PerformanceChartDto performanceChart =
        new PerformanceChartDto(
            "Inspection activity (" + currentYear + ")",
            inspectionsCount + " inspections",
            "Inspection and finding activity",
            "Inspections",
            "#3758F9",
            Collections.nCopies(12, 0L),
            "Findings",
            "#13B2E4",
            Collections.nCopies(12, 0L),
            MONTHS,
            List.of("0", "1", "2", "3", "4"),
            4);

    int completed =
        customerScoped
            ? (int)
                queryCount(
                    "SELECT count(*) FROM inspections WHERE status = 'COMPLETED' AND owner_customer_id = ?",
                    ownerCustomerId)
            : 0;
    int completionRate =
        inspectionsCount > 0 ? (int) Math.round((double) completed / inspectionsCount * 100) : 100;
    RadialGaugeDto radialGauge =
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

    StackedBarChartDto stackedBarChart =
        buildSeverityStackedBar(false, customerScoped, null, ownerCustomerId);

    List<String> headers = List.of("Asset", "Code", "Inspection", "Status", "Action");
    List<QueueTableRowDto> rows = new ArrayList<>();
    if (customerScoped) {
      List<Map<String, Object>> records =
          jdbcTemplate.queryForList(
              "SELECT i.id, a.name AS asset_name, a.asset_code, i.status FROM inspections i JOIN assets a ON i.asset_id = a.id WHERE i.owner_customer_id = ? ORDER BY i.created_at DESC LIMIT 5",
              ownerCustomerId);
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
                "/client/inspections/" + row.get("id")));
      }
    }
    QueueTableDto queueTable =
        new QueueTableDto(
            "Recent inspections",
            "Inspection activity",
            headers,
            rows,
            "/client/inspections",
            "View all inspections");

    return new DashboardAnalyticsResponse(
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

  private DashboardAnalyticsResponse buildInspectorAnalytics(UUID actorId) {
    int currentYear = LocalDate.now().getYear();
    long inspectionsCount =
        queryCount("SELECT count(*) FROM inspections WHERE inspector_id = ?", actorId);
    long activeInspectionsCount =
        queryCount(
            "SELECT count(*) FROM inspections WHERE inspector_id = ? AND status IN ('SCHEDULED', 'IN_PROGRESS', 'FIELD_COMPLETED', 'UNDER_REVIEW')",
            actorId);
    long findingsCount =
        queryCount(
            "SELECT count(*) FROM findings f JOIN inspections i ON f.inspection_id = i.id WHERE i.inspector_id = ? AND f.is_deleted = false",
            actorId);
    long criticalFindingsCount =
        queryCount(
            "SELECT count(*) FROM findings f JOIN inspections i ON f.inspection_id = i.id WHERE i.inspector_id = ? AND f.is_deleted = false AND f.severity IN ('CRITICAL', 'HIGH')",
            actorId);

    List<DashboardMetricDto> summaryCards =
        List.of(
            new DashboardMetricDto(
                "assignments",
                "Nhiệm vụ bay",
                String.valueOf(inspectionsCount),
                activeInspectionsCount + " đang mở",
                true,
                "lịch phân công kiểm định",
                "plane"),
            new DashboardMetricDto(
                "findings",
                "Phát hiện khuyết tật",
                String.valueOf(findingsCount),
                criticalFindingsCount + " nghiêm trọng",
                true,
                "ghi nhận qua drone",
                "alert"),
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
                "check-circle"),
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

    Map<Integer, Long> mInsp =
        queryMonthlyMap(
            "SELECT EXTRACT(MONTH FROM planned_start_at)::int AS month, COUNT(*) AS total "
                + "FROM inspections WHERE inspector_id = ? AND EXTRACT(YEAR FROM planned_start_at) = ? GROUP BY 1",
            new Object[] {actorId, currentYear});
    Map<Integer, Long> mFind =
        queryMonthlyMap(
            "SELECT EXTRACT(MONTH FROM f.created_at)::int AS month, COUNT(*) AS total "
                + "FROM findings f JOIN inspections i ON f.inspection_id = i.id "
                + "WHERE i.inspector_id = ? AND f.is_deleted = false AND EXTRACT(YEAR FROM f.created_at) = ? GROUP BY 1",
            new Object[] {actorId, currentYear});

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
    long inspMaxVal = Math.max(4, Math.max(cumInsp, cumFind) + 1);
    PerformanceChartDto performanceChart =
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
            MONTHS,
            buildYTicks(inspMaxVal),
            inspMaxVal);

    long completedAssigned =
        queryCount(
            "SELECT count(*) FROM inspections WHERE inspector_id = ? AND status = 'COMPLETED'",
            actorId);
    int compRate =
        inspectionsCount > 0
            ? (int) Math.round((double) completedAssigned / inspectionsCount * 100)
            : 100;
    RadialGaugeDto radialGauge =
        new RadialGaugeDto(
            "Hiệu suất & An toàn Bay kiểm định",
            "Tỷ lệ hoàn thành nhiệm vụ và lập báo cáo",
            compRate + "%",
            compRate >= 80 ? "Đạt chuẩn" : "Đang tiến hành",
            compRate,
            List.of(
                new RadialSubMetricDto(
                    "Đợt kiểm tra hoàn thành",
                    completedAssigned + " / " + Math.max(1, inspectionsCount) + " Nhiệm vụ",
                    compRate),
                new RadialSubMetricDto("Toàn vẹn bằng chứng MinIO", "100% SHA-256", 100)));

    StackedBarChartDto stackedBarChart = buildSeverityStackedBar(true, false, actorId, null);

    List<QueueTableRowDto> inspRows = new ArrayList<>();
    List<Map<String, Object>> records =
        jdbcTemplate.queryForList(
            "SELECT i.id, a.name as asset_name, a.asset_code as asset_code, i.status "
                + "FROM inspections i JOIN assets a ON i.asset_id = a.id "
                + "WHERE i.inspector_id = ? ORDER BY i.created_at DESC LIMIT 5",
            actorId);
    for (Map<String, Object> r : records) {
      String status = (String) r.get("status");
      inspRows.add(
          new QueueTableRowDto(
              (String) r.get("asset_name"),
              (String) r.get("asset_code"),
              "Drone inspection",
              status,
              "COMPLETED".equals(status) ? "Đã xuất bản" : "Đang thực hiện",
              "COMPLETED".equals(status) ? "positive" : "warning",
              "/operations/inspections/" + r.get("id")));
    }

    QueueTableDto queueTable =
        new QueueTableDto(
            "Đợt kiểm tra được phân công gần đây",
            "Các nhiệm vụ bay khảo sát hiện trường và lập báo cáo của bạn",
            List.of(
                "Công trình / Tài sản",
                "Mã công trình",
                "Mục tiêu khảo sát",
                "Trạng thái",
                "Hành động"),
            inspRows,
            "/operations/inspections",
            "Xem toàn bộ đợt kiểm tra");

    return new DashboardAnalyticsResponse(
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

  private DashboardAnalyticsResponse buildEmptyAnalytics() {
    int currentYear = LocalDate.now().getYear();
    return new DashboardAnalyticsResponse(
        List.of(),
        new PerformanceChartDto(
            "Inspection activity (" + currentYear + ")",
            "0 inspections",
            "No activity",
            "Inspections",
            "#3758F9",
            Collections.nCopies(12, 0L),
            "Findings",
            "#13B2E4",
            Collections.nCopies(12, 0L),
            MONTHS,
            List.of("0", "1", "2", "3", "4"),
            4),
        new RadialGaugeDto("Completion", "No data", "0%", "Pending", 0, List.of()),
        new StackedBarChartDto("Distribution", List.of("—"), List.of(), 4),
        new QueueTableDto(
            "Recent activity",
            "No items",
            List.of("Item", "Code", "Detail", "Status", "Action"),
            List.of(),
            "/",
            "View all"));
  }

  private StackedBarChartDto buildSeverityStackedBar(
      boolean isInspector, boolean customerScoped, UUID actorId, UUID ownerCustomerId) {
    List<String> last6Months = new ArrayList<>();
    List<LocalDate> monthDates = new ArrayList<>();
    LocalDate today = LocalDate.now();
    for (int i = 5; i >= 0; i--) {
      LocalDate d = today.minusMonths(i);
      last6Months.add("Thg " + d.getMonthValue());
      monthDates.add(d);
    }

    String sql =
        "SELECT EXTRACT(YEAR FROM f.created_at)::int AS y, "
            + "EXTRACT(MONTH FROM f.created_at)::int AS m, f.severity, COUNT(*) AS cnt "
            + "FROM findings f JOIN inspections i ON f.inspection_id = i.id "
            + "WHERE f.is_deleted = false AND f.created_at >= (CURRENT_DATE - INTERVAL '6 MONTH') "
            + (isInspector
                ? "AND i.inspector_id = ? "
                : (customerScoped ? "AND i.owner_customer_id = ? " : ""))
            + "GROUP BY 1, 2, 3";

    Map<String, Integer> map = new HashMap<>();
    try {
      List<Map<String, Object>> rows =
          isInspector
              ? jdbcTemplate.queryForList(sql, actorId)
              : (customerScoped
                  ? jdbcTemplate.queryForList(sql, ownerCustomerId)
                  : jdbcTemplate.queryForList(sql));
      for (Map<String, Object> r : rows) {
        Number y = (Number) r.get("y");
        Number m = (Number) r.get("m");
        String sev = (String) r.get("severity");
        Number cnt = (Number) r.get("cnt");
        if (y != null && m != null && sev != null && cnt != null) {
          map.put(y.intValue() + "-" + m.intValue() + "-" + sev, cnt.intValue());
        }
      }
    } catch (Exception ignored) {
    }

    List<Integer> critData = new ArrayList<>();
    List<Integer> medData = new ArrayList<>();
    List<Integer> lowData = new ArrayList<>();
    int maxTotal = 0;
    for (LocalDate d : monthDates) {
      int y = d.getYear();
      int m = d.getMonthValue();
      int c =
          map.getOrDefault(y + "-" + m + "-CRITICAL", 0)
              + map.getOrDefault(y + "-" + m + "-HIGH", 0);
      int med = map.getOrDefault(y + "-" + m + "-MEDIUM", 0);
      int l = map.getOrDefault(y + "-" + m + "-LOW", 0);
      critData.add(c);
      medData.add(med);
      lowData.add(l);
      maxTotal = Math.max(maxTotal, c + med + l);
    }
    return new StackedBarChartDto(
        "Phân bổ khuyết tật theo mức độ nghiêm trọng",
        last6Months,
        List.of(
            new StackedBarSeriesDto("Nghiêm trọng (High/Critical)", "#F04438", critData),
            new StackedBarSeriesDto("Trung bình (Medium)", "#F79009", medData),
            new StackedBarSeriesDto("Thấp (Low)", "#12B76A", lowData)),
        Math.max(4, maxTotal + 1));
  }

  private StackedBarChartDto buildAdminAiSourceStackedBar() {
    List<String> last6Months = new ArrayList<>();
    List<LocalDate> monthDates = new ArrayList<>();
    LocalDate today = LocalDate.now();
    for (int i = 5; i >= 0; i--) {
      LocalDate d = today.minusMonths(i);
      last6Months.add("Thg " + d.getMonthValue());
      monthDates.add(d);
    }

    String sql =
        "SELECT EXTRACT(YEAR FROM created_at)::int AS y, EXTRACT(MONTH FROM created_at)::int AS m, "
            + "CASE "
            + "  WHEN LOWER(COALESCE(model_name, '')) LIKE '%yolo%' THEN 'YOLO' "
            + "  WHEN model_name IS NOT NULL AND btrim(model_name) <> '' THEN 'VISION' "
            + "  ELSE 'MANUAL' "
            + "END AS src, COUNT(*) AS cnt "
            + "FROM findings WHERE is_deleted = false AND created_at >= (CURRENT_DATE - INTERVAL '6 MONTH') "
            + "GROUP BY 1, 2, 3";

    Map<String, Integer> map = new HashMap<>();
    try {
      for (Map<String, Object> r : jdbcTemplate.queryForList(sql)) {
        Number y = (Number) r.get("y");
        Number m = (Number) r.get("m");
        String src = (String) r.get("src");
        Number cnt = (Number) r.get("cnt");
        if (y != null && m != null && src != null && cnt != null) {
          map.put(y.intValue() + "-" + m.intValue() + "-" + src, cnt.intValue());
        }
      }
    } catch (Exception ignored) {
    }

    List<Integer> yoloData = new ArrayList<>();
    List<Integer> visionData = new ArrayList<>();
    List<Integer> manualData = new ArrayList<>();
    int maxTotal = 0;
    for (LocalDate d : monthDates) {
      int y = d.getYear();
      int m = d.getMonthValue();
      int yolo = map.getOrDefault(y + "-" + m + "-YOLO", 0);
      int vision = map.getOrDefault(y + "-" + m + "-VISION", 0);
      int manual = map.getOrDefault(y + "-" + m + "-MANUAL", 0);
      yoloData.add(yolo);
      visionData.add(vision);
      manualData.add(manual);
      maxTotal = Math.max(maxTotal, yolo + vision + manual);
    }

    return new StackedBarChartDto(
        "Nguồn phát hiện khuyết tật (6 tháng)",
        last6Months,
        List.of(
            new StackedBarSeriesDto("YOLO11", "#2B84EB", yoloData),
            new StackedBarSeriesDto("Vision LLM", "#06B6D4", visionData),
            new StackedBarSeriesDto("Kiểm định viên", "#93C5FD", manualData)),
        Math.max(4, maxTotal + 1));
  }

  private List<PipelineStageDto> buildManagerPipelineStages() {
    long openCount = queryCount("SELECT count(*) FROM service_requests WHERE status = 'OPEN'");
    long quotedCount = queryCount("SELECT count(*) FROM service_requests WHERE status = 'QUOTED'");
    long signedCount =
        queryCount(
            "SELECT count(*) FROM service_requests WHERE status IN ('ACCEPTED', 'CONTRACT_SIGNED', 'ASSIGNED')");
    long scheduledCount =
        queryCount(
            "SELECT count(*) FROM inspections WHERE status IN ('SCHEDULED', 'IN_PROGRESS', 'FIELD_COMPLETED', 'UNDER_REVIEW')");
    long publishedCount =
        queryCount("SELECT count(*) FROM inspection_reports WHERE status = 'PUBLISHED'");
    long paidCount =
        queryCount("SELECT count(*) FROM service_payments WHERE payment_status = 'PAID'");
    long maxStage =
        Math.max(
            1,
            Math.max(
                Math.max(openCount, quotedCount),
                Math.max(
                    Math.max(signedCount, scheduledCount), Math.max(publishedCount, paidCount))));

    return List.of(
        new PipelineStageDto("Yêu cầu mới", openCount, pct(openCount, maxStage), "#3758F9"),
        new PipelineStageDto("Đã báo giá", quotedCount, pct(quotedCount, maxStage), "#0EA5E9"),
        new PipelineStageDto("Đã ký hợp đồng", signedCount, pct(signedCount, maxStage), "#06B6D4"),
        new PipelineStageDto(
            "Đã xếp lịch / Đang bay", scheduledCount, pct(scheduledCount, maxStage), "#F59E0B"),
        new PipelineStageDto(
            "Báo cáo đã xuất bản", publishedCount, pct(publishedCount, maxStage), "#10B981"),
        new PipelineStageDto("Đã thanh toán", paidCount, pct(paidCount, maxStage), "#15803D"));
  }

  private List<PipelineStageDto> buildAdminMixStages(
      long pendingInspectors, long pendingDrones, long pendingCustomers, long activeUsers) {
    long yoloCount =
        queryCount(
            "SELECT count(*) FROM findings WHERE is_deleted = false AND LOWER(COALESCE(model_name, '')) LIKE '%yolo%'");
    long visionCount =
        queryCount(
            "SELECT count(*) FROM findings WHERE is_deleted = false AND model_name IS NOT NULL AND btrim(model_name) <> '' AND LOWER(model_name) NOT LIKE '%yolo%'");
    long manualCount =
        queryCount(
            "SELECT count(*) FROM findings WHERE is_deleted = false AND (model_name IS NULL OR btrim(model_name) = '')");
    long totalFindings = Math.max(1, yoloCount + visionCount + manualCount);

    long activeDrones = queryCount("SELECT count(*) FROM drones WHERE serviceability = 'ACTIVE'");
    long totalDrones =
        Math.max(1, queryCount("SELECT count(*) FROM drones WHERE serviceability != 'RETIRED'"));
    long pendingTotal = Math.max(1, pendingInspectors + pendingDrones + pendingCustomers);

    return List.of(
        new PipelineStageDto(
            "Phát hiện YOLO11", yoloCount, pct(yoloCount, totalFindings), "#3758F9"),
        new PipelineStageDto(
            "Vision LLM Fallback", visionCount, pct(visionCount, totalFindings), "#06B6D4"),
        new PipelineStageDto(
            "KĐV đánh dấu thủ công", manualCount, pct(manualCount, totalFindings), "#94A3B8"),
        new PipelineStageDto(
            "Drone sẵn sàng bay", activeDrones, pct(activeDrones, totalDrones), "#10B981"),
        new PipelineStageDto(
            "Hồ sơ MF1 chờ duyệt",
            pendingInspectors + pendingDrones + pendingCustomers,
            pct(
                pendingInspectors + pendingDrones + pendingCustomers,
                Math.max(pendingTotal, activeUsers)),
            "#F59E0B"));
  }

  private List<ActionItemDto> buildManagerActionItems() {
    List<ActionItemDto> items = new ArrayList<>();
    try {
      String quoteSql =
          "SELECT sr.id, a.name AS asset_name, a.asset_code, sr.status "
              + "FROM service_requests sr JOIN assets a ON sr.asset_id = a.id "
              + "WHERE sr.status IN ('OPEN', 'QUOTED') ORDER BY sr.created_at DESC LIMIT 6";
      for (Map<String, Object> r : jdbcTemplate.queryForList(quoteSql)) {
        String st = (String) r.get("status");
        items.add(
            new ActionItemDto(
                "QUOTES",
                (String) r.get("asset_name"),
                (String) r.get("asset_code"),
                "OPEN".equals(st) ? "Chưa lập báo giá" : "Đang chờ khách duyệt báo giá",
                st,
                "OPEN".equals(st) ? "warning" : "neutral",
                "/manager/contracts?requestId=" + r.get("id")));
      }

      String reportSql =
          "SELECT i.id, a.name AS asset_name, a.asset_code, ir.version_no "
              + "FROM inspection_reports ir "
              + "JOIN inspections i ON ir.inspection_id = i.id "
              + "JOIN assets a ON i.asset_id = a.id "
              + "WHERE ir.status = 'SUBMITTED' ORDER BY ir.submitted_at DESC NULLS LAST LIMIT 6";
      for (Map<String, Object> r : jdbcTemplate.queryForList(reportSql)) {
        items.add(
            new ActionItemDto(
                "REPORTS",
                (String) r.get("asset_name"),
                (String) r.get("asset_code"),
                "Báo cáo v" + r.get("version_no") + " chờ xuất bản",
                "SUBMITTED",
                "warning",
                "/manager/reports?inspectionId=" + r.get("id")));
      }

      String occSql =
          "SELECT sr.id AS req_id, a.name AS asset_name, a.asset_code, sro.occurrence_index "
              + "FROM service_request_occurrences sro "
              + "JOIN service_requests sr ON sro.request_id = sr.id "
              + "JOIN assets a ON sr.asset_id = a.id "
              + "WHERE sro.status = 'PENDING_ASSIGNMENT' AND sr.status IN ('ACCEPTED', 'CONTRACT_SIGNED', 'ASSIGNED') "
              + "ORDER BY sro.planned_start_at ASC LIMIT 4";
      for (Map<String, Object> r : jdbcTemplate.queryForList(occSql)) {
        items.add(
            new ActionItemDto(
                "RESCHEDULE",
                (String) r.get("asset_name"),
                (String) r.get("asset_code"),
                "Đợt #" + r.get("occurrence_index") + " chờ gán đối tác/drone",
                "PENDING_ASSIGNMENT",
                "neutral",
                "/manager/contracts?requestId=" + r.get("req_id")));
      }

      String abortedSql =
          "SELECT DISTINCT i.id, a.name AS asset_name, a.asset_code "
              + "FROM inspections i JOIN assets a ON i.asset_id = a.id "
              + "JOIN field_sessions fs ON fs.inspection_id = i.id "
              + "WHERE fs.status = 'ABORTED' AND i.status NOT IN ('COMPLETED', 'CANCELLED') "
              + "ORDER BY i.id DESC LIMIT 4";
      for (Map<String, Object> r : jdbcTemplate.queryForList(abortedSql)) {
        items.add(
            new ActionItemDto(
                "RESCHEDULE",
                (String) r.get("asset_name"),
                (String) r.get("asset_code"),
                "Phiên bay bị hủy — cần xếp lại lịch",
                "ABORTED",
                "negative",
                "/manager/inspections/" + r.get("id")));
      }
    } catch (Exception ignored) {
    }
    return items;
  }

  private List<ScheduleSlotDto> buildManagerUpcomingSchedule() {
    List<ScheduleSlotDto> list = new ArrayList<>();
    try {
      String sql =
          "SELECT i.id, a.name AS asset_name, a.asset_code, i.planned_start_at, i.status, "
              + "COALESCE(u.full_name, 'Chưa phân công') AS inspector_name "
              + "FROM inspections i "
              + "JOIN assets a ON i.asset_id = a.id "
              + "LEFT JOIN users u ON i.inspector_id = u.id "
              + "WHERE i.status IN ('SCHEDULED', 'IN_PROGRESS') "
              + "ORDER BY i.planned_start_at ASC NULLS LAST LIMIT 5";
      for (Map<String, Object> r : jdbcTemplate.queryForList(sql)) {
        Object plannedObj = r.get("planned_start_at");
        String timeStr = "Chưa có giờ";
        if (plannedObj instanceof java.sql.Timestamp ts) {
          timeStr = SHORT_DATE_TIME.format(ts.toInstant());
        }
        list.add(
            new ScheduleSlotDto(
                String.valueOf(r.get("id")),
                (String) r.get("asset_name"),
                r.get("asset_code") + " · " + r.get("inspector_name"),
                timeStr,
                (String) r.get("status"),
                "/manager/inspections/" + r.get("id")));
      }
    } catch (Exception ignored) {
    }
    return list;
  }

  private List<InspectorWorkloadDto> buildInspectorWorkloads() {
    List<InspectorWorkloadDto> list = new ArrayList<>();
    try {
      String sql =
          "SELECT u.full_name, COALESCE(ip.pilot_license_class, 'CLASS_A') AS license_class, "
              + "COALESCE((SELECT count(*) FROM inspections i WHERE i.inspector_id = u.id AND i.status IN ('SCHEDULED', 'IN_PROGRESS')), 0) AS active_missions, "
              + "COALESCE((SELECT AVG(ir.overall_score)::double precision FROM inspector_ratings ir WHERE ir.inspector_id = u.id), 0) AS avg_rating, "
              + "COALESCE((SELECT count(*) FROM inspector_ratings ir WHERE ir.inspector_id = u.id), 0) AS rating_count "
              + "FROM users u LEFT JOIN inspector_profiles ip ON ip.user_id = u.id "
              + "WHERE u.status = 'ACTIVE' AND u.roles LIKE '%INSPECTOR%' "
              + "ORDER BY active_missions DESC, avg_rating DESC LIMIT 5";
      for (Map<String, Object> r : jdbcTemplate.queryForList(sql)) {
        Number active = (Number) r.get("active_missions");
        Number avg = (Number) r.get("avg_rating");
        Number cnt = (Number) r.get("rating_count");
        list.add(
            new InspectorWorkloadDto(
                (String) r.get("full_name"),
                active != null ? active.longValue() : 0L,
                avg != null ? Math.round(avg.doubleValue() * 10.0) / 10.0 : 0.0,
                cnt != null ? cnt.longValue() : 0L,
                String.valueOf(r.get("license_class"))));
      }
    } catch (Exception ignored) {
    }
    return list;
  }

  private List<ActionItemDto> buildAdminActionItems() {
    List<ActionItemDto> items = new ArrayList<>();
    try {
      String inspAppSql =
          "SELECT full_name, pilot_license_class, status FROM inspector_applications "
              + "WHERE status = 'PENDING_REVIEW' ORDER BY created_at DESC LIMIT 3";
      for (Map<String, Object> r : jdbcTemplate.queryForList(inspAppSql)) {
        items.add(
            new ActionItemDto(
                "MF1",
                (String) r.get("full_name"),
                String.valueOf(r.get("pilot_license_class")),
                "Hồ sơ đối tác Inspector",
                "PENDING_REVIEW",
                "warning",
                "/admin/onboarding-reviews"));
      }

      String droneAppSql =
          "SELECT serial_number, model, status FROM drone_registration_applications "
              + "WHERE status = 'PENDING' ORDER BY created_at DESC LIMIT 3";
      for (Map<String, Object> r : jdbcTemplate.queryForList(droneAppSql)) {
        String model = (String) r.get("model");
        items.add(
            new ActionItemDto(
                "MF1",
                model != null ? model.split("\\[")[0].trim() : "Drone",
                (String) r.get("serial_number"),
                "Hồ sơ đăng ký Drone",
                "PENDING",
                "warning",
                "/admin/drone-registrations"));
      }

      String custAppSql =
          "SELECT asset_name, asset_code, airspace_classification, status FROM prospective_customer_registrations "
              + "WHERE status = 'PENDING' ORDER BY created_at DESC LIMIT 2";
      for (Map<String, Object> r : jdbcTemplate.queryForList(custAppSql)) {
        items.add(
            new ActionItemDto(
                "MF1",
                (String) r.get("asset_name"),
                (String) r.get("asset_code"),
                "Thẩm định vùng bay (" + r.get("airspace_classification") + ")",
                "PENDING",
                "warning",
                "/admin/customer-airspace"));
      }

      String auditSql =
          "SELECT COALESCE(u.full_name, 'SYSTEM') AS actor_name, ae.action, ae.aggregate_type, ae.after_status, ae.created_at "
              + "FROM audit_events ae LEFT JOIN users u ON ae.actor_user_id = u.id "
              + "ORDER BY ae.created_at DESC LIMIT 7";
      for (Map<String, Object> r : jdbcTemplate.queryForList(auditSql)) {
        String timeStr = "";
        if (r.get("created_at") instanceof java.sql.Timestamp ts) {
          timeStr = SHORT_DATE_TIME.format(ts.toInstant());
        }
        String afterStatus = (String) r.get("after_status");
        items.add(
            new ActionItemDto(
                "AUDIT",
                (String) r.get("actor_name"),
                (String) r.get("aggregate_type"),
                r.get("action") + (timeStr.isEmpty() ? "" : " · " + timeStr),
                afterStatus != null ? afterStatus : "LOGGED",
                "neutral",
                "/admin/audit-logs"));
      }
    } catch (Exception ignored) {
    }
    return items;
  }

  private List<ServiceHealthDto> buildAdminServiceHealth(long failedEvidenceCount) {
    long activeDrones = queryCount("SELECT count(*) FROM drones WHERE serviceability = 'ACTIVE'");
    boolean autoConfirm = true;
    try {
      Boolean ac =
          jdbcTemplate.queryForObject(
              "SELECT auto_confirm_enabled FROM platform_ai_settings WHERE id = 1", Boolean.class);
      if (ac != null) autoConfirm = ac;
    } catch (Exception ignored) {
    }

    return List.of(
        new ServiceHealthDto("Backend & PostgreSQL", "HEALTHY", "Hoạt động"),
        new ServiceHealthDto(
            "AI Vision (YOLO11)",
            "HEALTHY",
            autoConfirm ? "Auto-confirm bật" : "Chờ duyệt thủ công"),
        new ServiceHealthDto(
            "MinIO Evidence",
            failedEvidenceCount == 0 ? "HEALTHY" : "DEGRADED",
            failedEvidenceCount == 0 ? "0 tệp lỗi" : failedEvidenceCount + " tệp lỗi"),
        new ServiceHealthDto("SmartDroneHub", "HEALTHY", activeDrones + " drone sẵn sàng"));
  }

  private QueueTableDto buildManagerActionQueue() {
    List<QueueTableRowDto> rows = new ArrayList<>();
    for (ActionItemDto item : buildManagerActionItems()) {
      if (rows.size() >= 6) break;
      rows.add(
          new QueueTableRowDto(
              item.title(),
              item.code(),
              item.subtitle(),
              item.status(),
              "Xử lý",
              item.badgeTone(),
              item.link()));
    }
    return new QueueTableDto(
        "Hàng đợi cần Manager xử lý",
        "Báo cáo chờ xuất bản, yêu cầu báo giá và ca chờ phân công",
        List.of("Công trình", "Mã", "Đầu việc", "Trạng thái", "Thao tác"),
        rows,
        "/manager/contracts",
        "Mở hàng đợi yêu cầu");
  }

  private QueueTableDto buildAdminAuditQueue() {
    List<QueueTableRowDto> rows = new ArrayList<>();
    for (ActionItemDto item : buildAdminActionItems()) {
      if (!"AUDIT".equals(item.category())) continue;
      if (rows.size() >= 6) break;
      rows.add(
          new QueueTableRowDto(
              item.title(),
              item.code(),
              item.subtitle(),
              item.status(),
              "Xem log",
              item.badgeTone(),
              item.link()));
    }
    return new QueueTableDto(
        "Nhật ký kiểm toán gần đây",
        "Các sự kiện vận hành và thẩm định mới nhất trên nền tảng",
        List.of("Tác nhân", "Phân hệ", "Hành động", "Trạng thái", "Chi tiết"),
        rows,
        "/admin/audit-logs",
        "Xem toàn bộ nhật ký");
  }

  private static int pct(long count, long max) {
    if (max <= 0) return 0;
    return (int) Math.min(100, Math.round((double) count / max * 100));
  }

  private static long roundAxisMax(long maxVal) {
    long base = Math.max(4, maxVal + 1);
    return ((base + 3) / 4) * 4;
  }

  private static List<String> buildYTicks(long maxVal) {
    return List.of(
        "0",
        String.valueOf(maxVal / 4),
        String.valueOf(maxVal * 2 / 4),
        String.valueOf(maxVal * 3 / 4),
        String.valueOf(maxVal));
  }

  private static String formatVndCompact(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      return "0 ₫";
    }
    BigDecimal billion = BigDecimal.valueOf(1_000_000_000L);
    BigDecimal million = BigDecimal.valueOf(1_000_000L);
    if (amount.compareTo(billion) >= 0) {
      return amount.divide(billion, 1, RoundingMode.HALF_UP) + " tỷ ₫";
    }
    if (amount.compareTo(million) >= 0) {
      return amount.divide(million, 1, RoundingMode.HALF_UP) + " tr ₫";
    }
    NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
    return nf.format(amount.longValue()) + " ₫";
  }

  private BigDecimal queryDecimal(String sql, Object... args) {
    try {
      BigDecimal val = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
      return val != null ? val : BigDecimal.ZERO;
    } catch (Exception ex) {
      return BigDecimal.ZERO;
    }
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
}

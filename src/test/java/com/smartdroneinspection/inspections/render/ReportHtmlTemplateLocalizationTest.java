package com.smartdroneinspection.inspections.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.inspections.spi.ReportRenderPort;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ReportHtmlTemplateLocalizationTest {

  @Test
  void localizesSystemLabelsButPreservesAuthoredReportText() {
    String authoredSummary = "Inspector-authored North span assessment";
    var context =
        new ReportRenderPort.RenderContext(
            "SDI-RPT-TEST-V1",
            1,
            "SUBMITTED",
            "Inspect the bridge",
            "Bridge 01",
            "Organization",
            "Inspector",
            null,
            null,
            new ReportRenderPort.ReportContext(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "permit.pdf",
                List.of("Mố cầu M1"),
                List.of("Phiên 1 · Ghi chú danh sách kiểm tra trước chuyến bay: Normal wear"),
                null),
            null,
            null,
            null,
            new ReportRenderPort.Sections(authoredSummary, null, null, null, null, null, null),
            List.of(),
            List.of(),
            Locale.forLanguageTag("vi"));

    String html = ReportHtmlTemplate.render(context, true);

    assertThat(html)
        .contains("Báo cáo kiểm tra công trình hạ tầng")
        .contains("1. Thông tin nhận dạng")
        .contains("3. Phương pháp, thiết bị và điều kiện")
        .contains("Ghi chú danh sách kiểm tra trước chuyến bay")
        .contains("Normal wear")
        .doesNotContain("Infrastructure Asset Inspection Report")
        .doesNotContain("Executive summary")
        .doesNotContain("Pre-flight checklist note:");
  }

  @Test
  void rendersThreeTierEvidenceWithVideoSourceLinkAndSummaryOnlyClearCaptures() {
    byte[] defectJpg = new byte[] {1, 1, 1}; // Base64 AQEB
    byte[] clearJpg = new byte[] {9, 9, 9}; // Base64 CQkJ

    var context =
        new ReportRenderPort.RenderContext(
            "SDI-RPT-TIER-V1",
            1,
            "SUBMITTED",
            "Inspect commercial tower facade",
            "Tower 01",
            "Organization",
            "Inspector",
            null,
            null,
            null,
            null,
            null,
            null,
            new ReportRenderPort.Sections("Summary", null, null, null, null, null, null),
            List.of(
                new ReportRenderPort.FindingView(
                    "F-1",
                    "ev-defect",
                    "orbit_frame_08s.jpg",
                    "abscission",
                    "HIGH",
                    "Spalling detected from orbital frame · Patch concrete",
                    "CONFIRMED",
                    "2026-10-08T09:00:00Z",
                    true)),
            List.of(
                new ReportRenderPort.EvidenceView(
                    "ev-defect",
                    "orbit_frame_08s.jpg",
                    "image/jpeg",
                    "2026-10-08T08:15:08Z",
                    "East facade",
                    "session-1",
                    defectJpg,
                    "tower_orbit.mp4",
                    8.0),
                new ReportRenderPort.EvidenceView(
                    "ev-video",
                    "tower_orbit.mp4",
                    "video/mp4",
                    "2026-10-08T08:15:00Z",
                    "Full perimeter",
                    "session-1",
                    null,
                    null,
                    null),
                new ReportRenderPort.EvidenceView(
                    "ev-clear",
                    "clear_north_facade.jpg",
                    "image/jpeg",
                    "2026-10-08T08:17:00Z",
                    "North facade",
                    "session-1",
                    clearJpg,
                    null,
                    null)),
            Locale.ENGLISH);

    String html = ReportHtmlTemplate.render(context, false);

    assertThat(html)
        .contains("Defect Evidence")
        .contains("Flight Video")
        .contains("Clear / Reference Capture")
        .contains("Source video: tower_orbit.mp4 @ 00:08")
        .contains("Flight video captures")
        .contains("Clear / reference captures summary")
        .contains(
            "1 clean captures recorded with zero defects (full plates omitted from PDF for clarity).")
        .contains("clear_north_facade.jpg")
        .contains("data:image/jpeg;base64,AQEB")
        .doesNotContain("CQkJ");
  }
}

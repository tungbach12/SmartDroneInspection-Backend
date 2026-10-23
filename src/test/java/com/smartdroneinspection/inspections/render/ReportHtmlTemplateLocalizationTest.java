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
            "DRAFT",
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
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                    "Phản hồi danh sách kiểm tra: Surface condition — PASS (Ghi chú: Normal wear)"),
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
        .contains("Tóm tắt")
        .contains("Phản hồi danh sách kiểm tra")
        .contains("Ghi chú danh sách kiểm tra trước chuyến bay")
        .contains("Normal wear")
        .contains(authoredSummary)
        .doesNotContain("Infrastructure Asset Inspection Report")
        .doesNotContain("Executive summary")
        .doesNotContain("Pre-flight checklist note:");
  }
}

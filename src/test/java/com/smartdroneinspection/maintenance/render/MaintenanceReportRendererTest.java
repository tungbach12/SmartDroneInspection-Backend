package com.smartdroneinspection.maintenance.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MaintenanceReportRendererTest {

  private final MaintenanceReportRenderer renderer = new MaintenanceReportRenderer();

  private MaintenanceReportRenderContext sampleContext() {
    return new MaintenanceReportRenderContext(
        "SDI-MNT-WO-12345678",
        1,
        "APPROVED",
        "Test Organization",
        "Asset Bridge #4",
        "FND-001",
        "Crack on support beam",
        "Replace steel bracket and weld joint",
        "Defect resolved and load test passed",
        "John Doe (Author)",
        Instant.now().toString(),
        "Lead Engineer",
        "Reviewer Admin",
        "Approver Admin",
        List.of(
            new MaintenanceReportRenderContext.TeamMemberView(
                "Jane Smith", "MEMBER", "Welding specialist")),
        List.of(
            new MaintenanceReportRenderContext.TaskView(
                1, "Weld joint", "MIG welding", "COMPLETED", "Jane Smith")),
        List.of(
            new MaintenanceReportRenderContext.WorkLogView(
                1,
                "Jane Smith",
                Instant.now().minusSeconds(7200).toString(),
                Instant.now().toString(),
                "2.0",
                "Clean weld completed",
                "Ultrasonic test 100% pass")),
        List.of(
            new MaintenanceReportRenderContext.ChangeOrderView(
                1, "Extra welding gas needed", "50.00", "APPROVED", Instant.now().toString())),
        new MaintenanceReportRenderContext.CostStatementView(
            "USD", "1000.00", "50.00", "1050.00", "1020.00", "-30.00", "-2.86%"),
        "{}",
        new MaintenanceReportRenderContext.AcceptanceView(
            "ACCEPTED", "Reviewer Admin", Instant.now().toString(), "All acceptance criteria met"));
  }

  @Test
  void rendersPdfDocumentSuccessfully() throws IOException {
    MaintenanceReportRenderContext context = sampleContext();
    byte[] pdfBytes = renderer.render(context, false);

    assertThat(pdfBytes).isNotNull();
    assertThat(pdfBytes.length).isGreaterThan(100);
    String header =
        new String(pdfBytes, 0, Math.min(10, pdfBytes.length), StandardCharsets.US_ASCII);
    assertThat(header).startsWith("%PDF-");
  }

  @Test
  void rendersPreviewPdfWithDraftMark() throws IOException {
    MaintenanceReportRenderContext context = sampleContext();
    byte[] previewBytes = renderer.render(context, true);

    assertThat(previewBytes).isNotNull();
    assertThat(previewBytes.length).isGreaterThan(100);
    String header =
        new String(previewBytes, 0, Math.min(10, previewBytes.length), StandardCharsets.US_ASCII);
    assertThat(header).startsWith("%PDF-");
  }
}

package com.smartdroneinspection.maintenance.render;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.stereotype.Component;

/** Pure-Java renderer that builds the maintenance completion report PDF from HTML. */
@Component
public class MaintenanceReportRenderer {

  public byte[] render(MaintenanceReportRenderContext context, boolean preview) throws IOException {
    String html = MaintenanceReportHtmlTemplate.render(context, preview);
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.useFastMode();
      builder.withHtmlContent(html, null);
      builder.toStream(out);
      builder.run();
      return out.toByteArray();
    }
  }
}

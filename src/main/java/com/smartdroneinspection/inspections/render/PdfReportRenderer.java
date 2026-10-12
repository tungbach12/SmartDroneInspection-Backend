package com.smartdroneinspection.inspections.render;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.smartdroneinspection.inspections.spi.ReportRenderPort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * Renders the report document to PDF with a pure-Java renderer.
 *
 * <p>No browser runtime is added to the deployment image. The renderer implements CSS 2.1 with
 * table layout, which is what {@link ReportHtmlTemplate} targets.
 */
@Component
public class PdfReportRenderer implements ReportRenderPort {

  @Override
  public byte[] render(RenderContext context) throws IOException {
    return render(context, false);
  }

  byte[] render(RenderContext context, boolean preview) throws IOException {
    String html = ReportHtmlTemplate.render(context, preview);
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

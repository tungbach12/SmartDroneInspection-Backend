package com.smartdroneinspection.inspections.render;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Renders the customer payment notice and the paid receipt.
 *
 * <p>These are internal payment documents, not electronic VAT/tax invoices: the platform records a
 * service amount and its VAT breakdown for the customer's information, but it does not issue a tax
 * invoice through this endpoint. Every value printed here is read from the stored occurrence and
 * report payment state, never from a client-supplied payload.
 */
@Component
public class PaymentDocumentRenderer {

  private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

  /** Whether the customer is being asked to pay, or is being given proof of payment. */
  public enum Kind {
    NOTICE,
    RECEIPT
  }

  /**
   * Everything the document prints.
   *
   * @param kind notice or receipt; decides the heading and the status wording
   * @param documentRef the reference printed on the document, derived from the occurrence
   * @param reportReference the published report version reference the payment belongs to
   * @param assetLabel the asset under inspection
   * @param organizationLabel the customer organization billed
   * @param serviceAmount the service base amount before VAT
   * @param vatRate the VAT rate in percent, when recorded
   * @param vatAmount the VAT amount, when recorded
   * @param totalAmount the amount the customer must pay or has paid
   * @param paymentStatus the occurrence payment status
   * @param paymentMethod the gateway or method that settled the payment, when paid
   * @param paymentReference the gateway transaction reference, when paid
   * @param paidAt when the payment was confirmed, when paid
   * @param acceptedAt when the customer accepted the report
   * @param dueAt the payment deadline, when recorded
   */
  public record Details(
      Kind kind,
      String documentRef,
      String reportReference,
      String assetLabel,
      String organizationLabel,
      BigDecimal serviceAmount,
      BigDecimal vatRate,
      BigDecimal vatAmount,
      BigDecimal totalAmount,
      String paymentStatus,
      String paymentMethod,
      String paymentReference,
      Instant paidAt,
      Instant acceptedAt,
      Instant dueAt,
      boolean overdue) {}

  public byte[] render(Details details) throws IOException {
    String html = toHtml(details);
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.useFastMode();
      builder.withHtmlContent(html, null);
      builder.toStream(out);
      builder.run();
      return out.toByteArray();
    }
  }

  String toHtml(Details details) {
    String title = details.kind() == Kind.RECEIPT ? "Payment receipt" : "Payment notice";
    String subtitle =
        details.kind() == Kind.RECEIPT
            ? "This document confirms a verified payment. It is not an electronic VAT invoice."
            : "This document states the amount due. It is not an electronic VAT invoice.";
    StringBuilder body = new StringBuilder();
    body.append("<h1>").append(escape(title)).append("</h1>");
    body.append("<p class=\"sub\">").append(escape(subtitle)).append("</p>");
    body.append("<table class=\"meta\">");
    row(body, "Document reference", details.documentRef());
    row(body, "Report reference", details.reportReference());
    row(body, "Asset", details.assetLabel());
    row(body, "Billed to", details.organizationLabel());
    body.append("</table>");

    body.append("<h2>Amounts</h2><table class=\"meta\">");
    row(body, "Service amount", money(details.serviceAmount()));
    if (details.vatRate() != null) {
      row(body, "VAT rate", details.vatRate().stripTrailingZeros().toPlainString() + "%");
    }
    row(body, "VAT amount", money(details.vatAmount()));
    row(body, "Total", money(details.totalAmount()));
    body.append("</table>");

    body.append("<h2>Status</h2><table class=\"meta\">");
    row(body, "Payment status", details.paymentStatus());
    if (details.acceptedAt() != null) {
      row(body, "Report accepted", format(details.acceptedAt()));
    }
    if (details.kind() == Kind.RECEIPT) {
      row(body, "Paid at", format(details.paidAt()));
      row(body, "Method", details.paymentMethod());
      row(body, "Transaction reference", details.paymentReference());
    } else {
      if (details.dueAt() != null) {
        row(body, "Payment due by", format(details.dueAt()));
      }
      if (details.overdue()) {
        row(
            body,
            "Notice",
            "The payment deadline has passed. Please settle the outstanding amount or contact your"
                + " service manager.");
      }
    }
    body.append("</table>");

    return """
        <!DOCTYPE html>
        <html><head><meta charset="utf-8"/><title>%s</title>
        <style>
          @page { size: A4; margin: 22mm 18mm; }
          body { font-family: sans-serif; font-size: 10.5pt; color: #1c1c1c; }
          h1 { font-size: 18pt; margin: 0 0 2mm 0; color: #12324f; }
          h2 { font-size: 12pt; margin: 8mm 0 2mm 0; color: #12324f; }
          .sub { margin: 0 0 6mm 0; color: #555; }
          table.meta { width: 100%%; border-collapse: collapse; }
          table.meta td { border-bottom: 0.4pt solid #cfcfcf; padding: 2mm 1mm; vertical-align: top; }
          td.label { width: 42%%; color: #555; }
          td.value { font-weight: 600; }
        </style></head><body>%s</body></html>
        """
        .formatted(escape(title), body);
  }

  private static void row(StringBuilder body, String label, String value) {
    body.append("<tr><td class=\"label\">")
        .append(escape(label))
        .append("</td><td class=\"value\">")
        .append(escape(value == null || value.isBlank() ? "Not recorded" : value))
        .append("</td></tr>");
  }

  private static String money(BigDecimal amount) {
    if (amount == null) {
      return "Not recorded";
    }
    NumberFormat format = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    format.setMaximumFractionDigits(0);
    format.setMinimumFractionDigits(0);
    return format.format(amount.setScale(0, RoundingMode.HALF_UP)) + " VND";
  }

  private static String format(Instant instant) {
    return instant == null ? "Not recorded" : DATE.format(instant.atZone(VN_ZONE));
  }

  static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}

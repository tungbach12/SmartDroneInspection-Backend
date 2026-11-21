package com.smartdroneinspection.inspections.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentDocumentRendererTest {

  private static final Instant ACCEPTED = Instant.parse("2026-11-10T03:00:00Z");
  private static final Instant DUE = Instant.parse("2026-11-25T03:00:00Z");

  @Test
  void noticePrintsTheDueDateAndDisclaimsTaxInvoiceStatus() {
    PaymentDocumentRenderer renderer = new PaymentDocumentRenderer();

    String html =
        renderer.toHtml(
            details(PaymentDocumentRenderer.Kind.NOTICE, "PAYMENT_DUE", null, DUE, false));

    assertThat(html).contains("Payment notice");
    assertThat(html).contains("SDI-PAY-ABCD1234");
    assertThat(html).contains("25/11/2026");
    assertThat(html).contains("VAT amount");
    assertThat(html).contains("not an electronic VAT invoice");
    assertThat(html).doesNotContain("Transaction reference");
  }

  @Test
  void overdueNoticeSaysTheDeadlinePassed() {
    PaymentDocumentRenderer renderer = new PaymentDocumentRenderer();

    String html =
        renderer.toHtml(
            details(PaymentDocumentRenderer.Kind.NOTICE, "PAYMENT_DUE", null, DUE, true));

    assertThat(html).contains("The payment deadline has passed");
  }

  @Test
  void receiptPrintsTheVerifiedTransactionReference() {
    PaymentDocumentRenderer renderer = new PaymentDocumentRenderer();

    String html =
        renderer.toHtml(
            details(PaymentDocumentRenderer.Kind.RECEIPT, "PAID", "SEPAY #998877", DUE, false));

    assertThat(html).contains("Payment receipt");
    assertThat(html).contains("SEPAY #998877");
    assertThat(html).contains("not an electronic VAT invoice");
    assertThat(html).doesNotContain("Payment due by");
  }

  @Test
  void rendersSelectablePdfBytes() throws Exception {
    PaymentDocumentRenderer renderer = new PaymentDocumentRenderer();

    byte[] pdf =
        renderer.render(
            details(PaymentDocumentRenderer.Kind.RECEIPT, "PAID", "SEPAY #1", DUE, false));

    assertThat(pdf).isNotEmpty();
    assertThat(
            new String(
                pdf, 0, Math.min(pdf.length, 5), java.nio.charset.StandardCharsets.ISO_8859_1))
        .isEqualTo("%PDF-");
  }

  @Test
  void amountsAndLabelsAreEscaped() {
    PaymentDocumentRenderer renderer = new PaymentDocumentRenderer();

    String html =
        renderer.toHtml(
            new PaymentDocumentRenderer.Details(
                PaymentDocumentRenderer.Kind.NOTICE,
                "SDI-PAY-ABCD1234",
                null,
                "Asset <script>",
                "Org & Co",
                new BigDecimal("1000000"),
                new BigDecimal("10"),
                new BigDecimal("100000"),
                new BigDecimal("1000000"),
                "PAYMENT_DUE",
                null,
                null,
                null,
                ACCEPTED,
                DUE,
                false));

    assertThat(html).contains("Asset &lt;script&gt;");
    assertThat(html).contains("Org &amp; Co");
    assertThat(html).doesNotContain("<script>");
  }

  private static PaymentDocumentRenderer.Details details(
      PaymentDocumentRenderer.Kind kind,
      String status,
      String reference,
      Instant dueAt,
      boolean overdue) {
    return new PaymentDocumentRenderer.Details(
        kind,
        "SDI-PAY-ABCD1234",
        "SDI-RPT-EFGH5678-V1",
        "AST-001 · Riverside Tower",
        "Acme Inspection Client",
        new BigDecimal("900000"),
        new BigDecimal("10"),
        new BigDecimal("90000"),
        new BigDecimal("990000"),
        status,
        "SEPAY",
        reference,
        kind == PaymentDocumentRenderer.Kind.RECEIPT ? Instant.parse("2026-11-12T02:00:00Z") : null,
        ACCEPTED,
        dueAt,
        overdue);
  }
}

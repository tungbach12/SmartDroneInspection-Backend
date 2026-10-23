package com.smartdroneinspection.inspections.render;

import com.smartdroneinspection.inspections.spi.ReportRenderPort;

/**
 * Builds the delivered report document.
 *
 * <p>This is plain HTML and CSS 2.1 with table-based layout, because the renderer in use implements
 * CSS 2.1 and not flexbox or grid. It is a formal document, not an authoring surface: there is no
 * navigation, no controls and no application styling here, and none should be added.
 */
final class ReportHtmlTemplate {

  private ReportHtmlTemplate() {}

  static String render(ReportRenderPort.RenderContext context, boolean preview) {
    StringBuilder body = new StringBuilder();
    header(body, context, preview);
    identification(body, context);
    executiveSummary(body, context);
    purposeScopeCriteria(body, context);
    methodEquipmentConditions(body, context);
    evidenceRegister(body, context);
    confirmedFindings(body, context);
    conclusions(body, context);
    appendices(body, context);
    if (!preview) {
      approval(body, context);
    }
    footer(body, context);
    return document(body.toString());
  }

  private static void header(
      StringBuilder body, ReportRenderPort.RenderContext context, boolean preview) {
    body.append("<div class=\"doc-header\">")
        .append("<table class=\"hdr\"><tr>")
        .append(
            "<td class=\"hdr-brand\">SmartDroneInspection<br/><span>Civil &amp; Industrial Infrastructure</span></td>")
        .append("<td class=\"hdr-ref\">REF: ")
        .append(escape(context.documentRef()))
        .append("</td>")
        .append("</tr></table></div>");
    if (preview) {
      body.append("<div class=\"draft-mark\">DRAFT PREVIEW — NOT AN OFFICIAL RECORD</div>");
    }
    body.append("<h1>Infrastructure Asset Inspection Report</h1>")
        .append("<table class=\"banner\"><tr><td>Report reference</td><td><b>")
        .append(escape(context.documentRef()))
        .append("</b></td><td>Version</td><td><b>v")
        .append(context.versionNo())
        .append("</b></td></tr><tr><td>Status</td><td><b>")
        .append(escape(context.status()))
        .append("</b></td><td>Issued</td><td><b>")
        .append(escape(orNotSupplied(context.publishedAt())))
        .append("</b></td></tr></table>");
  }

  private static void identification(StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, "1. Identification");
    body.append("<table class=\"kv\">");
    row(body, "Organization", context.organizationLabel());
    row(body, "Asset", context.assetLabel());
    if (context.reportContext() != null) {
      row(body, "Asset code", context.reportContext().assetCode());
      row(body, "Asset location", context.reportContext().assetLocation());
      row(body, "Asset category", context.reportContext().assetCategory());
    }
    row(body, "Inspection objective", context.objective());
    row(body, "Report reference", context.documentRef());
    row(body, "Version / status", "v" + context.versionNo() + " · " + context.status());
    row(body, "Inspector author", context.authorName());
    row(body, "Submitted", context.submittedAt());
    row(body, "Qualified reviewer", context.reviewerName());
    row(body, "Review decision time", context.reviewedAt());
    body.append("</table>");
  }

  private static void executiveSummary(StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, "2. Executive summary");
    ReportRenderPort.Sections sections = context.sections();
    body.append("<p class=\"summary\">");
    if (sections.overallCondition() != null && !sections.overallCondition().isBlank()) {
      body.append("<b>Overall condition: ")
          .append(escape(sections.overallCondition()))
          .append(".</b> ");
    }
    body.append(escape(orNotSupplied(sections.executiveSummary()))).append("</p>");
    if (hasText(sections.omissionDisclosure())) {
      body.append("<p class=\"disclosure\"><b>Limitations and omissions.</b> ")
          .append(escape(sections.omissionDisclosure()))
          .append("</p>");
    }
  }

  private static void purposeScopeCriteria(
      StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, "3. Purpose, scope and criteria");
    paragraph(body, context.sections().purposeScopeCriteria());
    if (hasText(context.sections().inspectorCoverageAssessment())) {
      body.append("<p><b>Inspector's coverage assessment.</b> ")
          .append(escape(context.sections().inspectorCoverageAssessment()))
          .append("</p>");
    }
  }

  private static void methodEquipmentConditions(
      StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, "4. Method, equipment and conditions");
    paragraph(body, context.sections().methodEquipmentConditions());
    ReportRenderPort.ReportContext report = context.reportContext();
    if (report != null) {
      body.append("<table class=\"kv\">");
      row(body, "Assigned Drone", droneLabel(report));
      row(body, "Preparation status", report.preparationStatus());
      row(body, "Planned shot list", report.shotList());
      row(body, "Evidence types requested", report.evidenceTypes());
      row(body, "Access constraints", report.accessConstraints());
      row(body, "Safety observations", report.safetyObservations());
      row(body, "Permit document references", report.permitDocumentReferences());
      row(body, "Readiness decision", report.readinessDecision());
      row(body, "Readiness decision date", report.readinessDecisionDate());
      row(body, "Readiness reason", report.readinessReason());
      if (!report.fieldSessionSummaries().isEmpty()) {
        row(body, "Recorded field sessions", String.join("; ", report.fieldSessionSummaries()));
      }
      row(body, "Reviewer qualification disclosure", report.reviewerQualificationDisclosure());
      body.append("</table>");
      if (!report.checklistResponseSummaries().isEmpty()) {
        body.append("<h3>Recorded checklist responses</h3><ul>");
        for (String response : report.checklistResponseSummaries()) {
          body.append("<li>").append(escape(response)).append("</li>");
        }
        body.append("</ul>");
      }
      if (report.fieldSessionSummaries().stream()
          .anyMatch(summary -> summary.contains("Pre-flight checklist note:"))) {
        body.append(
            "<p class=\"scope\">Pre-flight checklist content above is an Inspector-entered"
                + " note, not item-level checklist verification or proof of flight.</p>");
      }
    }
  }

  private static void evidenceRegister(StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, "5. Evidence register");
    if (context.evidence().isEmpty()) {
      body.append("<p class=\"none\">No evidence was linked to this version.</p>");
      return;
    }
    body.append("<table class=\"grid\">")
        .append("<thead><tr><th>Evidence ID</th><th>File</th><th>Capture time</th>")
        .append("<th>Location</th></tr></thead><tbody>");
    for (ReportRenderPort.EvidenceView item : context.evidence()) {
      body.append("<tr><td>")
          .append(escape(item.id()))
          .append("</td><td>")
          .append(escape(item.fileName()))
          .append("</td><td>")
          .append(escape(orNotSupplied(item.captureTime())))
          .append("</td><td>")
          .append(escape(orNotSupplied(item.componentLocation())))
          .append("</td></tr>");
    }
    body.append("</tbody></table>");
  }

  private static void confirmedFindings(
      StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, "6. Confirmed findings");
    if (context.findings().isEmpty()) {
      body.append("<p class=\"none\">No confirmed findings were recorded for this version.</p>");
      return;
    }
    for (ReportRenderPort.FindingView finding : context.findings()) {
      body.append("<div class=\"finding\">")
          .append("<table class=\"finding-head\"><tr>")
          .append("<td class=\"finding-id\">")
          .append(escape(finding.id()))
          .append("</td>")
          .append("<td class=\"finding-sev sev-")
          .append(severityClass(finding.severity()))
          .append("\">")
          .append(escape(finding.severity()))
          .append("</td>")
          .append("<td class=\"finding-dec\">")
          .append(escape(finding.decision()))
          .append("</td>")
          .append("</tr></table>")
          .append("<table>");
      row(body, "Defect", finding.defectLabel());
      row(body, "Technical notes", finding.technicalNotes());
      row(body, "Recommended action", finding.recommendedAction());
      row(body, "Decision", finding.decision() + " at " + orNotSupplied(finding.decidedAt()));

      ReportRenderPort.EvidenceView attachedEvidence = null;
      if (finding.evidenceId() != null) {
        attachedEvidence =
            context.evidence().stream()
                .filter(ev -> finding.evidenceId().equals(ev.id()))
                .findFirst()
                .orElse(null);
      }

      if (attachedEvidence != null) {
        body.append("<tr><th>Evidence</th><td>")
            .append(escape(attachedEvidence.fileName()))
            .append(" (")
            .append(escape(attachedEvidence.id()))
            .append(")</td></tr>");
      } else if (finding.evidenceId() != null) {
        body.append("<tr><th>Evidence</th><td>")
            .append(
                escape(
                    finding.evidenceFileName() == null
                        ? finding.evidenceId()
                        : finding.evidenceFileName()))
            .append(" (")
            .append(escape(finding.evidenceId()))
            .append(")</td></tr>");
      }
      body.append("</table>");

      if (attachedEvidence != null
          && isRenderableImage(attachedEvidence.contentType())
          && attachedEvidence.content() != null
          && attachedEvidence.content().length > 0) {
        body.append("<div class=\"plate\" style=\"margin-top: 6pt;\">")
            .append("<div class=\"plate-img\"><img src=\"data:")
            .append(escape(attachedEvidence.contentType()))
            .append(";base64,")
            .append(java.util.Base64.getEncoder().encodeToString(attachedEvidence.content()))
            .append("\" alt=\"")
            .append(escape(attachedEvidence.fileName()))
            .append("\"/></div>")
            .append("<div class=\"plate-cap\"><b>Defect evidence photograph:</b> ")
            .append(escape(attachedEvidence.fileName()))
            .append(" · ")
            .append(escape(attachedEvidence.id()))
            .append("</div></div>");
      }
      body.append("</div>");
    }
  }

  private static void conclusions(StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, "7. Conclusions and recommendations");
    paragraph(body, context.sections().conclusionsRecommendations());
    body.append(
        "<p class=\"scope\">This application report records the inspection evidence and"
            + " findings shown above. It is not a statement of whole-asset safety, a certified"
            + " non-destructive or load-capacity test, or a repair-completion certificate.</p>");
  }

  private static void appendices(StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, "8. Appendices");
    body.append(
        "<p>Evidence associated with confirmed findings is shown in section 6. The evidence"
            + " register and recorded inspection context appear in sections 4 and 5.</p>");

    java.util.Set<String> findingEvidenceIds = new java.util.HashSet<>();
    for (ReportRenderPort.FindingView finding : context.findings()) {
      if (finding.evidenceId() != null) {
        findingEvidenceIds.add(finding.evidenceId());
      }
      if (finding.evidenceFileName() != null) {
        findingEvidenceIds.add(finding.evidenceFileName());
      }
    }

    java.util.List<ReportRenderPort.EvidenceView> additionalEvidence =
        context.evidence().stream()
            .filter(
                item ->
                    !findingEvidenceIds.contains(item.id())
                        && !findingEvidenceIds.contains(item.fileName()))
            .filter(item -> isRenderableImage(item.contentType()))
            .filter(item -> item.content() != null && item.content().length > 0)
            .toList();

    if (!additionalEvidence.isEmpty()) {
      body.append("<h3>Additional survey photographs</h3>");
      for (ReportRenderPort.EvidenceView item : additionalEvidence) {
        body.append("<div class=\"plate\">")
            .append("<div class=\"plate-img\"><img src=\"data:")
            .append(escape(item.contentType()))
            .append(";base64,")
            .append(java.util.Base64.getEncoder().encodeToString(item.content()))
            .append("\" alt=\"")
            .append(escape(item.fileName()))
            .append("\"/></div>")
            .append("<div class=\"plate-cap\">")
            .append(escape(item.fileName()))
            .append(" · ")
            .append(escape(item.id()))
            .append("</div></div>");
      }
    }
  }

  private static void approval(StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, "9. Approval and revisions");
    body.append("<table class=\"kv\">")
        .append("<tr><th>Author</th><td>")
        .append(escape(orNotSupplied(context.authorName())))
        .append("</td><th>Submitted</th><td>")
        .append(escape(orNotSupplied(context.submittedAt())))
        .append("</td></tr>")
        .append("<tr><th>Reviewer</th><td>")
        .append(escape(orNotSupplied(context.reviewerName())))
        .append("</td><th>Review decision</th><td>")
        .append(escape(orNotSupplied(context.reviewedAt())))
        .append("</td></tr>")
        .append("<tr><th>Published</th><td>")
        .append(escape(orNotSupplied(context.publishedAt())))
        .append("</td><th></th><td></td></tr>")
        .append("</table>")
        .append(
            "<p class=\"scope\">A correction to this report is issued as a new linked version; this"
                + " version is not altered. The approval recorded above is an application approval"
                + " and is not a certified digital signature.</p>");
  }

  private static void footer(StringBuilder body, ReportRenderPort.RenderContext context) {
    body.append("<div class=\"doc-footer\">")
        .append(escape(context.documentRef()))
        .append(" · Page ")
        .append("<span class=\"page-number\"></span>")
        .append(" of ")
        .append("<span class=\"page-count\"></span>")
        .append("</div>");
  }

  private static String droneLabel(ReportRenderPort.ReportContext report) {
    StringBuilder label = new StringBuilder();
    appendLabel(label, report.assignedDroneManufacturer());
    appendLabel(label, report.assignedDroneModel());
    appendLabel(label, report.assignedDroneSerialNumber());
    return label.isEmpty() ? null : label.toString();
  }

  private static void appendLabel(StringBuilder label, String value) {
    if (hasText(value)) {
      if (!label.isEmpty()) {
        label.append(" · ");
      }
      label.append(value);
    }
  }

  private static void section(StringBuilder body, String title) {
    body.append("<h2>").append(escape(title)).append("</h2>");
  }

  private static void paragraph(StringBuilder body, String value) {
    body.append("<p>").append(escape(orNotSupplied(value))).append("</p>");
  }

  private static void row(StringBuilder body, String label, String value) {
    body.append("<tr><th>")
        .append(escape(label))
        .append("</th><td>")
        .append(escape(orNotSupplied(value)))
        .append("</td></tr>");
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String orNotSupplied(String value) {
    return hasText(value) ? value : "Not supplied";
  }

  private static boolean isRenderableImage(String contentType) {
    return contentType != null
        && (contentType.startsWith("image/png")
            || contentType.startsWith("image/jpeg")
            || contentType.startsWith("image/gif")
            || contentType.startsWith("image/webp"));
  }

  private static String severityClass(String severity) {
    if (severity == null) {
      return "none";
    }
    return switch (severity.toUpperCase()) {
      case "CRITICAL", "HIGH" -> "high";
      case "MEDIUM" -> "medium";
      case "LOW" -> "low";
      default -> "none";
    };
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private static String document(String body) {
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="utf-8"/>
        <title>Infrastructure Asset Inspection Report</title>
        <style>
        @page {
          size: A4 portrait;
          margin: 25.4mm 20mm 22mm 20mm;
          @top-center {
            content: "Infrastructure Asset Inspection Report";
            font-family: Helvetica, Arial, sans-serif;
            font-size: 8pt;
            color: #475569;
          }
          @bottom-center {
            content: "Page " counter(page) " of " counter(pages);
            font-family: Helvetica, Arial, sans-serif;
            font-size: 8pt;
            color: #475569;
          }
        }
        body {
          font-family: Helvetica, Arial, sans-serif;
          font-size: 10pt;
          line-height: 1.5;
          color: #1e293b;
          margin: 0;
        }
        h1 {
          font-size: 18pt;
          color: #0f172a;
          text-transform: uppercase;
          letter-spacing: 0.02em;
          margin: 8pt 0 10pt 0;
        }
        h2 {
          font-size: 12pt;
          color: #1e3a8a;
          border-bottom: 1.5pt solid #2563eb;
          padding-bottom: 3pt;
          margin: 16pt 0 8pt 0;
        }
        h3 { font-size: 10.5pt; color: #334155; margin: 12pt 0 6pt 0; }
        table { border-collapse: collapse; width: 100%; }
        .hdr td { padding: 0 0 4pt 0; }
        .hdr-brand { font-size: 9.5pt; font-weight: bold; color: #0f172a; }
        .hdr-brand span { font-size: 8pt; font-weight: normal; color: #475569; }
        .hdr-ref { font-size: 8.5pt; color: #475569; text-align: right; }
        .draft-mark {
          background: #fef3c7; border: 1pt solid #f59e0b; color: #92400e;
          font-size: 9pt; font-weight: bold; text-align: center;
          padding: 4pt; margin: 8pt 0;
        }
        .banner td {
          border: 0.5pt solid #cbd5e1; padding: 4pt 6pt; font-size: 9pt;
        }
        .kv th {
          text-align: left; vertical-align: top; width: 30%;
          background: #f1f5f9; border: 0.5pt solid #cbd5e1;
          padding: 4pt 6pt; font-size: 9pt; color: #334155;
        }
        .kv td { border: 0.5pt solid #cbd5e1; padding: 4pt 6pt; font-size: 9pt; }
        .grid th {
          background: #f1f5f9; border: 0.5pt solid #cbd5e1;
          padding: 4pt 6pt; font-size: 8.5pt; text-align: left; color: #334155;
        }
        .grid td { border: 0.5pt solid #cbd5e1; padding: 4pt 6pt; font-size: 8.5pt; }
        .finding { margin: 0 0 12pt 0; page-break-inside: avoid; }
        .finding-head td { padding: 0; }
        .finding-id { font-size: 9.5pt; font-weight: bold; color: #0f172a; padding-bottom: 3pt; }
        .finding-sev { font-size: 8.5pt; font-weight: bold; padding-bottom: 3pt; }
        .finding-dec { font-size: 8.5pt; color: #475569; text-align: right; padding-bottom: 3pt; }
        .finding table { border-collapse: collapse; }
        .finding th {
          text-align: left; vertical-align: top; width: 26%;
          background: #f8fafc; border: 0.5pt solid #cbd5e1;
          padding: 4pt 6pt; font-size: 8.5pt; color: #475569;
        }
        .finding td { border: 0.5pt solid #cbd5e1; padding: 4pt 6pt; font-size: 8.5pt; }
        .sev-high { color: #b91c1c; }
        .sev-medium { color: #b45309; }
        .sev-low { color: #15803d; }
        .sev-none { color: #475569; }
        .mono { font-family: "Courier New", Courier, monospace; }
        .small { font-size: 7.5pt; }
        .summary { margin: 0 0 8pt 0; }
        .disclosure { background: #fffbeb; border-left: 2.5pt solid #f59e0b; padding: 6pt 8pt; font-size: 9pt; }
        .scope { font-size: 8.5pt; color: #475569; font-style: italic; }
        .none { font-style: italic; color: #475569; }
        .plate { margin: 0 0 10pt 0; page-break-inside: avoid; }
        .plate-img { border: 0.5pt solid #cbd5e1; text-align: center; padding: 4pt; }
        .plate-img img { max-width: 100%; max-height: 150pt; }
        .plate-cap { font-size: 7.5pt; color: #475569; margin-top: 3pt; }
        .doc-footer { display: none; }
        </style>
        </head>
        <body>
        """
        + body
        + "</body></html>";
  }
}

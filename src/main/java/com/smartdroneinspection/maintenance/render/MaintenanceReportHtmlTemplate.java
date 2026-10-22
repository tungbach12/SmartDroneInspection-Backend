package com.smartdroneinspection.maintenance.render;

/**
 * Builds the delivered maintenance completion report document (MF4-16/17).
 *
 * <p>Uses plain HTML and CSS 2.1 with table-based layout compatible with OpenHTMLtoPDF.
 */
final class MaintenanceReportHtmlTemplate {

  private MaintenanceReportHtmlTemplate() {}

  static String render(MaintenanceReportRenderContext context, boolean preview) {
    StringBuilder body = new StringBuilder();
    header(body, context, preview);
    identification(body, context);
    accountability(body, context);
    scopeAndCriteria(body, context);
    planAndChanges(body, context);
    executionAndLogs(body, context);
    costStatement(body, context);
    authorDeclaration(body, context);
    if (!preview && context.acceptanceDecision() != null) {
      acceptanceRecord(body, context);
    }
    footer(body, context);
    return document(body.toString());
  }

  private static void header(
      StringBuilder body, MaintenanceReportRenderContext context, boolean preview) {
    body.append("<div class=\"doc-header\">")
        .append("<table class=\"hdr\"><tr>")
        .append(
            "<td class=\"hdr-brand\">SmartDroneInspection<br/><span>Civil &amp; Industrial"
                + " Infrastructure — Maintenance</span></td>")
        .append("<td class=\"hdr-ref\">REF: ")
        .append(escape(context.documentRef()))
        .append("</td>")
        .append("</tr></table></div>");
    if (preview) {
      body.append("<div class=\"draft-mark\">DRAFT PREVIEW — NOT AN OFFICIAL RECORD</div>");
    }
    body.append("<h1>Maintenance Completion Report</h1>")
        .append("<table class=\"banner\"><tr><td>Work order reference</td><td><b>")
        .append(escape(context.documentRef()))
        .append("</b></td><td>Version</td><td><b>v")
        .append(context.versionNo())
        .append("</b></td></tr><tr><td>Status</td><td><b>")
        .append(escape(context.status()))
        .append("</b></td><td>Organization</td><td><b>")
        .append(escape(context.organizationLabel()))
        .append("</b></td></tr></table>");
  }

  private static void identification(StringBuilder body, MaintenanceReportRenderContext context) {
    section(body, "1. Identification and Source Finding");
    body.append("<table class=\"kv\">");
    row(body, "Asset", context.assetLabel());
    row(body, "Source finding code", context.findingCode());
    row(body, "Defect label", context.defectLabel());
    body.append("</table>");
  }

  private static void accountability(StringBuilder body, MaintenanceReportRenderContext context) {
    section(body, "2. Accountability and Assigned Team");
    body.append("<table class=\"kv\">");
    row(body, "Budget approver", context.budgetApproverName());
    row(body, "Repair team lead", context.leadName());
    row(body, "Report author", context.authorName());
    row(body, "Independent reviewer", context.reviewerName());
    body.append("</table>");

    if (context.teamMembers() != null && !context.teamMembers().isEmpty()) {
      body.append("<h3>Assigned Team Roster</h3>")
          .append(
              "<table class=\"grid\"><tr><th>Name</th><th>Role</th><th>Designation reason</th></tr>");
      for (MaintenanceReportRenderContext.TeamMemberView m : context.teamMembers()) {
        body.append("<tr><td>")
            .append(escape(m.name()))
            .append("</td><td>")
            .append(escape(m.role()))
            .append("</td><td>")
            .append(escape(orNotSupplied(m.reason())))
            .append("</td></tr>");
      }
      body.append("</table>");
    }
  }

  private static void scopeAndCriteria(StringBuilder body, MaintenanceReportRenderContext context) {
    section(body, "3. Approved Corrective Scope and Criteria");
    body.append("<table class=\"kv\">");
    row(body, "Corrective scope", context.correctiveScope());
    row(body, "Acceptance criteria", context.acceptanceCriteria());
    body.append("</table>");
  }

  private static void planAndChanges(StringBuilder body, MaintenanceReportRenderContext context) {
    section(body, "4. Plan, Estimates and Change Orders");
    if (context.changeOrders() != null && !context.changeOrders().isEmpty()) {
      body.append(
          "<table class=\"grid\"><tr><th>#</th><th>Reason</th><th>Proposed delta</th><th>Status</th><th>Decided at</th></tr>");
      for (MaintenanceReportRenderContext.ChangeOrderView c : context.changeOrders()) {
        body.append("<tr><td>")
            .append(c.changeNumber())
            .append("</td><td>")
            .append(escape(c.reason()))
            .append("</td><td>")
            .append(escape(c.proposedDelta()))
            .append("</td><td>")
            .append(escape(c.status()))
            .append("</td><td>")
            .append(escape(orNotSupplied(c.decidedAt())))
            .append("</td></tr>");
      }
      body.append("</table>");
    } else {
      paragraph(
          body, "No change orders were requested. Work executed under initial approved baseline.");
    }
  }

  private static void executionAndLogs(StringBuilder body, MaintenanceReportRenderContext context) {
    section(body, "5. Execution Records and Work Logs");
    if (context.tasks() != null && !context.tasks().isEmpty()) {
      body.append("<h3>Tasks</h3>")
          .append(
              "<table class=\"grid\"><tr><th>Task #</th><th>Name</th><th>Method</th><th>Assignee</th><th>Status</th></tr>");
      for (MaintenanceReportRenderContext.TaskView t : context.tasks()) {
        body.append("<tr><td>")
            .append(t.taskNumber())
            .append("</td><td>")
            .append(escape(t.name()))
            .append("</td><td>")
            .append(escape(orNotSupplied(t.method())))
            .append("</td><td>")
            .append(escape(orNotSupplied(t.assigneeName())))
            .append("</td><td>")
            .append(escape(t.status()))
            .append("</td></tr>");
      }
      body.append("</table>");
    }

    if (context.workLogs() != null && !context.workLogs().isEmpty()) {
      body.append("<h3>Verified Work Logs</h3>")
          .append(
              "<table class=\"grid\"><tr><th>Task #</th><th>Engineer</th><th>Hours</th><th>As-left condition</th><th>Test readings</th></tr>");
      for (MaintenanceReportRenderContext.WorkLogView wl : context.workLogs()) {
        body.append("<tr><td>")
            .append(wl.taskNumber())
            .append("</td><td>")
            .append(escape(wl.engineerName()))
            .append("</td><td>")
            .append(escape(wl.hours()))
            .append("</td><td>")
            .append(escape(orNotSupplied(wl.asLeftCondition())))
            .append("</td><td>")
            .append(escape(orNotSupplied(wl.testReadings())))
            .append("</td></tr>");
      }
      body.append("</table>");
    }
  }

  private static void costStatement(StringBuilder body, MaintenanceReportRenderContext context) {
    section(body, "6. Cost Reconciliation and Variance Statement");
    if (context.costStatement() != null) {
      MaintenanceReportRenderContext.CostStatementView cs = context.costStatement();
      body.append("<table class=\"kv\">");
      row(body, "Currency", cs.currency());
      row(body, "Initial approved baseline (E0)", cs.baselineTotal());
      row(body, "Approved changes total", cs.approvedChangesTotal());
      row(body, "Revised authorized budget (B)", cs.authorizedTotal());
      row(body, "Reconciled actual total (A)", cs.actualTotal());
      row(body, "Variance (V = A - B)", cs.variance());
      row(body, "Variance percentage", cs.variancePercent());
      body.append("</table>");
    } else {
      paragraph(body, "Cost statement pending reconciliation.");
    }
  }

  private static void authorDeclaration(
      StringBuilder body, MaintenanceReportRenderContext context) {
    section(body, "7. Author Verification and Declaration");
    body.append("<table class=\"kv\">");
    row(body, "Designated report author", context.authorName());
    row(body, "Author verified at", context.authorVerifiedAt());
    body.append("</table>");
    body.append(
        "<p class=\"scope\">The author confirms that this completion report reflects the verified"
            + " work logs, actual costs, and technical observations recorded by the executing team.</p>");
  }

  private static void acceptanceRecord(StringBuilder body, MaintenanceReportRenderContext context) {
    section(body, "8. Independent Acceptance Record");
    MaintenanceReportRenderContext.AcceptanceView av = context.acceptanceDecision();
    body.append("<table class=\"kv\">");
    row(body, "Independent reviewer", av.reviewerName());
    row(body, "Acceptance decision", av.decision());
    row(body, "Decided at", av.decidedAt());
    row(body, "Technical comments", av.technicalComments());
    row(body, "Signature reference", av.signatureReference());
    body.append("</table>");
    body.append(
        "<p class=\"scope\">Technical acceptance is rendered by an independent Organization Admin"
            + " who is not on the executing team. Financial reconciliation is recorded separately.</p>");
  }

  private static void footer(StringBuilder body, MaintenanceReportRenderContext context) {
    body.append("<div class=\"doc-footer\">")
        .append(escape(context.documentRef()))
        .append(" · Page ")
        .append("<span class=\"page-number\"></span>")
        .append(" of ")
        .append("<span class=\"page-count\"></span>")
        .append("</div>");
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
        <title>Maintenance Completion Report</title>
        <style>
        @page {
          size: A4 portrait;
          margin: 25.4mm 20mm 22mm 20mm;
          @top-center {
            content: "Maintenance Completion Report";
            font-family: Helvetica, Arial, sans-serif;
            font-size: 8pt;
            color: #475569;
          }
          @bottom-center {
            content: counter(page) " / " counter(pages);
            font-family: Helvetica, Arial, sans-serif;
            font-size: 8pt;
            color: #64748b;
          }
        }
        body {
          font-family: Helvetica, Arial, sans-serif;
          font-size: 9.5pt;
          line-height: 1.45;
          color: #0f172a;
          margin: 0;
          padding: 0;
        }
        h1 {
          font-size: 16pt;
          color: #0f172a;
          margin: 12pt 0 8pt 0;
          padding-bottom: 4pt;
          border-bottom: 2pt solid #0284c7;
        }
        h2 {
          font-size: 11pt;
          color: #0369a1;
          margin: 14pt 0 6pt 0;
          padding-bottom: 2pt;
          border-bottom: 0.5pt solid #cbd5e1;
        }
        h3 {
          font-size: 10pt;
          color: #334155;
          margin: 10pt 0 4pt 0;
        }
        p {
          margin: 4pt 0 6pt 0;
        }
        .doc-header {
          border-bottom: 1pt solid #cbd5e1;
          padding-bottom: 6pt;
          margin-bottom: 10pt;
        }
        table.hdr {
          width: 100%;
          border-collapse: collapse;
        }
        table.hdr td.hdr-brand {
          font-size: 11pt;
          font-weight: bold;
          color: #0369a1;
        }
        table.hdr td.hdr-brand span {
          font-size: 8pt;
          font-weight: normal;
          color: #64748b;
        }
        table.hdr td.hdr-ref {
          text-align: right;
          font-size: 8.5pt;
          color: #64748b;
        }
        .draft-mark {
          background: #fef3c7;
          border: 1pt solid #f59e0b;
          color: #92400e;
          font-weight: bold;
          font-size: 9pt;
          text-align: center;
          padding: 6pt;
          margin: 8pt 0;
        }
        table.banner {
          width: 100%;
          border-collapse: collapse;
          background: #f8fafc;
          border: 0.5pt solid #cbd5e1;
          margin-bottom: 12pt;
        }
        table.banner td {
          padding: 4pt 8pt;
          font-size: 8.5pt;
        }
        table.kv {
          width: 100%;
          border-collapse: collapse;
          margin-bottom: 8pt;
        }
        table.kv th {
          width: 28%;
          text-align: left;
          background: #f1f5f9;
          padding: 3.5pt 6pt;
          font-size: 8.5pt;
          color: #334155;
          border: 0.5pt solid #cbd5e1;
          vertical-align: top;
        }
        table.kv td {
          padding: 3.5pt 6pt;
          font-size: 8.5pt;
          border: 0.5pt solid #cbd5e1;
          vertical-align: top;
        }
        table.grid {
          width: 100%;
          border-collapse: collapse;
          margin-bottom: 8pt;
        }
        table.grid th {
          background: #0369a1;
          color: #ffffff;
          padding: 4pt 6pt;
          font-size: 8.5pt;
          text-align: left;
          border: 0.5pt solid #0369a1;
        }
        table.grid td {
          padding: 3.5pt 6pt;
          font-size: 8.5pt;
          border: 0.5pt solid #cbd5e1;
        }
        .scope {
          font-size: 8pt;
          color: #64748b;
          font-style: italic;
          margin-top: 4pt;
        }
        .doc-footer {
          margin-top: 14pt;
          font-size: 7.5pt;
          color: #94a3b8;
          text-align: center;
          border-top: 0.5pt solid #e2e8f0;
          padding-top: 4pt;
        }
        </style>
        </head>
        <body>
        """
        + body
        + """
        </body>
        </html>
        """;
  }
}

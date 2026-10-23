package com.smartdroneinspection.inspections.render;

import com.smartdroneinspection.inspections.spi.ReportRenderPort;
import java.util.Locale;

/**
 * Builds the delivered report document.
 *
 * <p>This is plain HTML and CSS 2.1 with table-based layout, because the renderer in use implements
 * CSS 2.1 and not flexbox or grid. It is a formal document, not an authoring surface: there is no
 * navigation, no controls and no application styling here, and none should be added.
 */
public final class ReportHtmlTemplate {

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
    return document(body.toString(), context.locale());
  }

  private static void header(
      StringBuilder body, ReportRenderPort.RenderContext context, boolean preview) {
    body.append("<div class=\"doc-header\">")
        .append("<table class=\"hdr\"><tr>")
        .append("<td class=\"hdr-brand\">SmartDroneInspection<br/><span>")
        .append(escape(text(context, "Civil & Industrial Infrastructure")))
        .append("</span></td>")
        .append("<td class=\"hdr-ref\">")
        .append(text(context, "REF"))
        .append(": ")
        .append(escape(context.documentRef()))
        .append("</td>")
        .append("</tr></table></div>");
    if (preview) {
      body.append("<div class=\"draft-mark\">")
          .append(text(context, "DRAFT PREVIEW — NOT AN OFFICIAL RECORD"))
          .append("</div>");
    }
    body.append("<h1>")
        .append(text(context, "Infrastructure Asset Inspection Report"))
        .append("</h1><table class=\"banner\"><tr><td>")
        .append(text(context, "Report reference"))
        .append("</td><td><b>")
        .append(escape(context.documentRef()))
        .append("</b></td><td>")
        .append(text(context, "Version"))
        .append("</td><td><b>v")
        .append(context.versionNo())
        .append("</b></td></tr><tr><td>")
        .append(text(context, "Status"))
        .append("</td><td><b>")
        .append(escape(context.status()))
        .append("</b></td><td>")
        .append(text(context, "Issued"))
        .append("</td><td><b>")
        .append(escape(orNotSupplied(context.publishedAt(), context)))
        .append("</b></td></tr></table>");
  }

  private static void identification(StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, context, "1. Identification");
    body.append("<table class=\"kv\">");
    row(body, context, "Organization", context.organizationLabel());
    row(body, context, "Asset", context.assetLabel());
    if (context.reportContext() != null) {
      row(body, context, "Asset code", context.reportContext().assetCode());
      row(body, context, "Asset location", context.reportContext().assetLocation());
      row(body, context, "Asset category", context.reportContext().assetCategory());
    }
    row(body, context, "Inspection objective", context.objective());
    row(body, context, "Report reference", context.documentRef());
    row(body, context, "Version / status", "v" + context.versionNo() + " · " + context.status());
    row(body, context, "Inspector author", context.authorName());
    row(body, context, "Submitted", context.submittedAt());
    row(body, context, "Qualified reviewer", context.reviewerName());
    row(body, context, "Review decision time", context.reviewedAt());
    body.append("</table>");
  }

  private static void executiveSummary(StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, context, "2. Executive summary");
    ReportRenderPort.Sections sections = context.sections();
    body.append("<p class=\"summary\">");
    if (sections.overallCondition() != null && !sections.overallCondition().isBlank()) {
      body.append("<b>")
          .append(text(context, "Overall condition"))
          .append(": ")
          .append(escape(text(context, sections.overallCondition())))
          .append(".</b> ");
    }
    body.append(escape(orNotSupplied(sections.executiveSummary(), context))).append("</p>");
    if (hasText(sections.omissionDisclosure())) {
      body.append("<p class=\"disclosure\"><b>")
          .append(text(context, "Limitations and omissions"))
          .append(".</b> ")
          .append(escape(sections.omissionDisclosure()))
          .append("</p>");
    }
  }

  private static void purposeScopeCriteria(
      StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, context, "3. Purpose, scope and criteria");
    paragraph(body, context, context.sections().purposeScopeCriteria());
    if (hasText(context.sections().inspectorCoverageAssessment())) {
      body.append("<p><b>")
          .append(text(context, "Inspector's coverage assessment"))
          .append(".</b> ")
          .append(escape(context.sections().inspectorCoverageAssessment()))
          .append("</p>");
    }
  }

  private static void methodEquipmentConditions(
      StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, context, "4. Method, equipment and conditions");
    paragraph(body, context, context.sections().methodEquipmentConditions());
    ReportRenderPort.ReportContext report = context.reportContext();
    if (report != null) {
      body.append("<table class=\"kv\">");
      row(body, context, "Assigned Drone", droneLabel(report));
      row(body, context, "Preparation status", report.preparationStatus());
      row(body, context, "Planned shot list", report.shotList());
      row(body, context, "Evidence types requested", report.evidenceTypes());
      row(body, context, "Access constraints", report.accessConstraints());
      row(body, context, "Safety observations", report.safetyObservations());
      row(body, context, "Permit document references", report.permitDocumentReferences());
      row(body, context, "Readiness decision", report.readinessDecision());
      row(body, context, "Readiness decision date", report.readinessDecisionDate());
      row(body, context, "Readiness reason", report.readinessReason());
      if (!report.fieldSessionSummaries().isEmpty()) {
        row(
            body,
            context,
            "Recorded field sessions",
            String.join("; ", report.fieldSessionSummaries()));
      }
      row(
          body,
          context,
          "Reviewer qualification disclosure",
          report.reviewerQualificationDisclosure());
      body.append("</table>");
      if (!report.checklistResponseSummaries().isEmpty()) {
        body.append("<h3>")
            .append(text(context, "Recorded checklist responses"))
            .append("</h3><ul>");
        for (String response : report.checklistResponseSummaries()) {
          body.append("<li>").append(escape(response)).append("</li>");
        }
        body.append("</ul>");
      }
      if (report.fieldSessionSummaries().stream()
          .anyMatch(
              summary -> summary.contains(text(context, "Pre-flight checklist note") + ":"))) {
        body.append("<p class=\"scope\">")
            .append(
                text(
                    context,
                    "Pre-flight checklist content above is an Inspector-entered note, not item-level checklist verification or proof of flight."))
            .append("</p>");
      }
    }
  }

  private static void evidenceRegister(StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, context, "5. Evidence register");
    if (context.evidence().isEmpty()) {
      body.append("<p class=\"none\">")
          .append(text(context, "No evidence was linked to this version."))
          .append("</p>");
      return;
    }
    body.append("<table class=\"grid\">")
        .append("<thead><tr><th>")
        .append(text(context, "Evidence ID"))
        .append("</th><th>")
        .append(text(context, "File"))
        .append("</th><th>")
        .append(text(context, "Capture time"))
        .append("</th><th>")
        .append(text(context, "Location"))
        .append("</th></tr></thead><tbody>");
    for (ReportRenderPort.EvidenceView item : context.evidence()) {
      body.append("<tr><td>")
          .append(escape(item.id()))
          .append("</td><td>")
          .append(escape(item.fileName()))
          .append("</td><td>")
          .append(escape(orNotSupplied(item.captureTime(), context)))
          .append("</td><td>")
          .append(escape(orNotSupplied(item.componentLocation(), context)))
          .append("</td></tr>");
    }
    body.append("</tbody></table>");
  }

  private static void confirmedFindings(
      StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, context, "6. Confirmed findings");
    if (context.findings().isEmpty()) {
      body.append("<p class=\"none\">")
          .append(text(context, "No confirmed findings were recorded for this version."))
          .append("</p>");
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
      row(body, context, "Defect", finding.defectLabel());
      row(body, context, "Technical notes", finding.technicalNotes());
      row(body, context, "Recommended action", finding.recommendedAction());
      row(
          body,
          context,
          "Decision",
          finding.decision()
              + " at "
              + orNotSupplied(finding.decidedAt(), context));

      ReportRenderPort.EvidenceView attachedEvidence = null;
      if (finding.evidenceId() != null) {
        attachedEvidence =
            context.evidence().stream()
                .filter(ev -> finding.evidenceId().equals(ev.id()))
                .findFirst()
                .orElse(null);
      }

      if (attachedEvidence != null) {
        body.append("<tr><th>")
            .append(text(context, "Evidence"))
            .append("</th><td>")
            .append(escape(attachedEvidence.fileName()))
            .append(" (")
            .append(escape(attachedEvidence.id()))
            .append(")</td></tr>");
      } else if (finding.evidenceId() != null) {
        body.append("<tr><th>")
            .append(text(context, "Evidence"))
            .append("</th><td>")
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
            .append("<div class=\"plate-cap\"><b>")
            .append(text(context, "Defect evidence photograph:"))
            .append("</b> ")
            .append(escape(attachedEvidence.fileName()))
            .append(" · ")
            .append(escape(attachedEvidence.id()))
            .append("</div></div>");
      }
      body.append("</div>");
    }
  }

  private static void conclusions(StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, context, "7. Conclusions and recommendations");
    paragraph(body, context, context.sections().conclusionsRecommendations());
    body.append("<p class=\"scope\">")
        .append(
            text(
                context,
                "This application report records the inspection evidence and findings shown above. It is not a statement of whole-asset safety, a certified non-destructive or load-capacity test, or a repair-completion certificate."))
        .append("</p>");
  }

  private static void appendices(StringBuilder body, ReportRenderPort.RenderContext context) {
    section(body, context, "8. Appendices");
    body.append("<p>")
        .append(
            text(
                context,
                "Evidence associated with confirmed findings is shown in section 6. The evidence register and recorded inspection context appear in sections 4 and 5."))
        .append("</p>");

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
      body.append("<h3>").append(text(context, "Additional survey photographs")).append("</h3>");
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
    section(body, context, "9. Approval and revisions");
    body.append("<table class=\"kv\">")
        .append("<tr><th>")
        .append(text(context, "Author"))
        .append("</th><td>")
        .append(escape(orNotSupplied(context.authorName(), context)))
        .append("</td><th>")
        .append(text(context, "Submitted"))
        .append("</th><td>")
        .append(escape(orNotSupplied(context.submittedAt(), context)))
        .append("</td></tr>")
        .append("<tr><th>")
        .append(text(context, "Reviewer"))
        .append("</th><td>")
        .append(escape(orNotSupplied(context.reviewerName(), context)))
        .append("</td><th>")
        .append(text(context, "Review decision"))
        .append("</th><td>")
        .append(escape(orNotSupplied(context.reviewedAt(), context)))
        .append("</td></tr>")
        .append("<tr><th>")
        .append(text(context, "Published"))
        .append("</th><td>")
        .append(escape(orNotSupplied(context.publishedAt(), context)))
        .append("</td><th></th><td></td></tr>")
        .append("</table>")
        .append("<p class=\"scope\">")
        .append(
            text(
                context,
                "A correction to this report is issued as a new linked version; this version is not altered. The approval recorded above is an application approval and is not a certified digital signature."))
        .append("</p>");
  }

  private static void footer(StringBuilder body, ReportRenderPort.RenderContext context) {
    body.append("<div class=\"doc-footer\">")
        .append(escape(context.documentRef()))
        .append(" · ")
        .append(text(context, "Page"))
        .append(" ")
        .append("<span class=\"page-number\"></span>")
        .append(" ")
        .append(text(context, "of"))
        .append(" ")
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

  private static void section(
      StringBuilder body, ReportRenderPort.RenderContext context, String title) {
    body.append("<h2>").append(escape(text(context, title))).append("</h2>");
  }

  private static void paragraph(
      StringBuilder body, ReportRenderPort.RenderContext context, String value) {
    body.append("<p>").append(escape(orNotSupplied(value, context))).append("</p>");
  }

  private static void row(
      StringBuilder body, ReportRenderPort.RenderContext context, String label, String value) {
    body.append("<tr><th>")
        .append(escape(text(context, label)))
        .append("</th><td>")
        .append(escape(orNotSupplied(value, context)))
        .append("</td></tr>");
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String orNotSupplied(String value, ReportRenderPort.RenderContext context) {
    return hasText(value) ? value : text(context, "Not supplied");
  }

  private static String text(ReportRenderPort.RenderContext context, String english) {
    return localized(context.locale(), english);
  }

  public static String localized(Locale locale, String english) {
    if (locale == null || !"vi".equalsIgnoreCase(locale.getLanguage())) {
      return english;
    }
    return switch (english) {
      case "Civil & Industrial Infrastructure" -> "Hạ tầng dân dụng và công nghiệp";
      case "REF" -> "MÃ";
      case "DRAFT PREVIEW — NOT AN OFFICIAL RECORD" ->
          "BẢN XEM TRƯỚC — KHÔNG PHẢI HỒ SƠ CHÍNH THỨC";
      case "Infrastructure Asset Inspection Report" -> "Báo cáo kiểm tra công trình hạ tầng";
      case "Report reference" -> "Mã báo cáo";
      case "Version" -> "Phiên bản";
      case "Status" -> "Trạng thái";
      case "Issued" -> "Ngày phát hành";
      case "Not supplied" -> "Chưa được cung cấp";
      case "1. Identification" -> "1. Thông tin nhận dạng";
      case "2. Executive summary" -> "2. Tóm tắt điều hành";
      case "3. Purpose, scope and criteria" -> "3. Mục đích, phạm vi và tiêu chí";
      case "4. Method, equipment and conditions" -> "4. Phương pháp, thiết bị và điều kiện";
      case "5. Evidence register" -> "5. Danh mục bằng chứng";
      case "6. Confirmed findings" -> "6. Phát hiện đã xác nhận";
      case "7. Conclusions and recommendations" -> "7. Kết luận và khuyến nghị";
      case "8. Appendices" -> "8. Phụ lục";
      case "9. Approval and revisions" -> "9. Phê duyệt và các lần chỉnh sửa";
      case "Organization" -> "Tổ chức";
      case "Asset" -> "Tài sản";
      case "Asset code" -> "Mã tài sản";
      case "Asset location" -> "Vị trí tài sản";
      case "Asset category" -> "Danh mục tài sản";
      case "Inspection objective" -> "Mục tiêu kiểm tra";
      case "Version / status" -> "Phiên bản / trạng thái";
      case "Inspector author" -> "Thanh tra viên lập báo cáo";
      case "Author verification" -> "Tác giả xác minh";
      case "Qualified reviewer" -> "Người đánh giá";
      case "Review decision time" -> "Thời điểm đánh giá";
      case "Evidence snapshot hash" -> "Mã băm ảnh chụp bằng chứng";
      case "Executive summary" -> "Tóm tắt điều hành";
      case "Overall condition" -> "Tình trạng tổng thể";
      case "GOOD" -> "TỐT";
      case "FAIR" -> "ĐẠT YÊU CẦU";
      case "POOR" -> "KÉM";
      case "CRITICAL" -> "NGHIÊM TRỌNG";
      case "Limitations and omissions" -> "Hạn chế và nội dung còn thiếu";
      case "Inspector's coverage assessment" -> "Đánh giá phạm vi bao quát của thanh tra viên";
      case "Assigned Drone" -> "Thiết bị bay được phân công";
      case "Preparation status" -> "Trạng thái chuẩn bị";
      case "Planned shot list" -> "Danh sách ảnh dự kiến";
      case "Evidence types requested" -> "Loại bằng chứng được yêu cầu";
      case "Access constraints" -> "Hạn chế tiếp cận";
      case "Safety observations" -> "Quan sát an toàn";
      case "Permit document references" -> "Tham chiếu tài liệu giấy phép";
      case "Readiness decision" -> "Quyết định sẵn sàng";
      case "Readiness decision date" -> "Ngày quyết định sẵn sàng";
      case "Readiness reason" -> "Lý do quyết định sẵn sàng";
      case "Evidence quality decision" -> "Quyết định chất lượng bằng chứng";
      case "Evidence quality comparison" -> "Nội dung đối chiếu chất lượng bằng chứng";
      case "Evidence quality limitation" -> "Hạn chế về chất lượng bằng chứng";
      case "Recorded field sessions" -> "Các phiên hiện trường đã ghi nhận";
      case "Reviewer qualification disclosure" ->
          "Thông tin về việc thẩm định năng lực người đánh giá";
      case "Recorded checklist responses" -> "Các câu trả lời danh sách kiểm tra đã ghi nhận";
      case "Pre-flight checklist content above is an Inspector-entered note, not item-level checklist verification or proof of flight." ->
          "Nội dung danh sách kiểm tra trước chuyến bay ở trên là ghi chú do thanh tra viên nhập, không phải xác minh từng mục hay bằng chứng chuyến bay.";
      case "No evidence was linked to this version." ->
          "Không có bằng chứng nào được liên kết với phiên bản này.";
      case "Evidence ID" -> "Mã bằng chứng";
      case "File" -> "Tệp";
      case "Capture time" -> "Thời điểm ghi nhận";
      case "Component / location" -> "Bộ phận / vị trí";
      case "No confirmed findings were recorded for this version." ->
          "Không có phát hiện đã xác nhận nào được ghi nhận cho phiên bản này.";
      case "Component" -> "Bộ phận";
      case "Defect" -> "Khuyết tật";
      case "Location" -> "Vị trí";
      case "Observed condition" -> "Tình trạng quan sát được";
      case "Description" -> "Mô tả";
      case "Measurement (method and unit)" -> "Phép đo (phương pháp và đơn vị)";
      case "Technical notes" -> "Ghi chú kỹ thuật";
      case "Recommended action" -> "Hành động khuyến nghị";
      case "Decision" -> "Quyết định";
      case "Corrective work required" -> "Yêu cầu công việc khắc phục";
      case "Evidence" -> "Bằng chứng";
      case "Defect evidence photograph:" -> "Ảnh bằng chứng khuyết tật:";
      case "No" -> "Không";
      case "Yes" -> "Có";
      case "This application report records the inspection evidence and findings shown above. It is not a statement of whole-asset safety, a certified non-destructive or load-capacity test, or a repair-completion certificate." ->
          "Báo cáo ứng dụng này ghi lại bằng chứng và phát hiện kiểm tra nêu trên. Báo cáo không xác nhận an toàn cho toàn bộ tài sản, không thay thế thử nghiệm không phá hủy hoặc thử tải được chứng nhận, và không phải chứng nhận hoàn tất sửa chữa.";
      case "Evidence associated with confirmed findings is shown in section 6. The evidence register and recorded inspection context appear in sections 4 and 5." ->
          "Bằng chứng liên quan đến các phát hiện đã xác nhận được trình bày tại mục 6. Danh mục bằng chứng và bối cảnh kiểm tra đã ghi nhận nằm ở mục 4 và 5.";
      case "Additional survey photographs" -> "Ảnh khảo sát bổ sung";
      case "Author" -> "Tác giả";
      case "Reviewer" -> "Người đánh giá";
      case "Review decision" -> "Quyết định đánh giá";
      case "Published" -> "Đã phát hành";
      case "A correction to this report is issued as a new linked version; this version is not altered. The approval recorded above is an application approval and is not a certified digital signature." ->
          "Mọi chỉnh sửa báo cáo được phát hành thành phiên bản mới có liên kết; phiên bản này không bị thay đổi. Phê duyệt ở trên là phê duyệt trong ứng dụng, không phải chữ ký số được chứng nhận.";
      case "Objective" -> "Mục tiêu";
      case "Scope" -> "Phạm vi";
      case "Included components" -> "Bộ phận bao gồm";
      case "Acceptance criteria" -> "Tiêu chí chấp nhận";
      case "Planned shot-list" -> "Danh sách ảnh dự kiến";
      case " (required)" -> " (bắt buộc)";
      case " by " -> " bởi ";
      case " at " -> " lúc ";
      case "Session " -> "Phiên ";
      case "Started" -> "Bắt đầu";
      case "Ended" -> "Kết thúc";
      case "Checklist response" -> "Phản hồi danh sách kiểm tra";
      case "Notes" -> "Ghi chú";
      case "Pre-flight checklist note" -> "Ghi chú danh sách kiểm tra trước chuyến bay";
      case "Postponement reason" -> "Lý do hoãn";
      case "Abort reason" -> "Lý do hủy";
      case "Field-session outcome" -> "Kết quả phiên hiện trường";
      case "Recorded limitations" -> "Hạn chế đã ghi nhận";
      case "Application role/organization/separation checks ran; professional qualification was not independently validated." ->
          "Ứng dụng đã kiểm tra vai trò, tổ chức và phân tách nhiệm vụ; năng lực chuyên môn chưa được xác minh độc lập.";
      case "Reviewer role and separation were recorded; professional credential qualification was not validated by the application." ->
          "Vai trò và tính độc lập của người đánh giá đã được ghi nhận; ứng dụng chưa xác minh năng lực chuyên môn của họ.";
      case "Completed; zero detections" -> "Đã hoàn tất; không phát hiện bất thường";
      case "Completed; findings detected" -> "Đã hoàn tất; đã phát hiện bất thường";
      case "Page" -> "Trang";
      case "of" -> "trên";
      default -> english;
    };
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

  private static String document(String body, Locale locale) {
    boolean vietnamese = locale != null && "vi".equalsIgnoreCase(locale.getLanguage());
    String language = vietnamese ? "vi" : "en";
    String title =
        vietnamese
            ? "Báo cáo kiểm tra công trình hạ tầng"
            : "Infrastructure Asset Inspection Report";
    String page = vietnamese ? "Trang" : "Page";
    String of = vietnamese ? "trên" : "of";
    String template =
        """
        <!DOCTYPE html>
        <html lang="$LANG">
        <head>
        <meta charset="utf-8"/>
        <title>$TITLE</title>
        <style>
        @page {
          size: A4 portrait;
          margin: 25.4mm 20mm 22mm 20mm;
          @top-center {
            content: "$TITLE";
            font-family: Helvetica, Arial, sans-serif;
            font-size: 8pt;
            color: #475569;
          }
          @bottom-center {
            content: "$PAGE " counter(page) " $OF " counter(pages);
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
        """;
    return template
            .replace("$LANG", language)
            .replace("$TITLE", escape(title))
            .replace("$PAGE", escape(page))
            .replace("$OF", escape(of))
        + body
        + "</body></html>";
  }
}

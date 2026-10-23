package com.smartdroneinspection.maintenance.render;

import java.util.List;

/** Context data required to render a complete Maintenance Completion Report (MF4-16/17). */
public record MaintenanceReportRenderContext(
    String documentRef,
    int versionNo,
    String status,
    String organizationLabel,
    String assetLabel,
    String findingCode,
    String defectLabel,
    String correctiveScope,
    String acceptanceCriteria,
    String authorName,
    String authorVerifiedAt,
    String leadName,
    String reviewerName,
    String budgetApproverName,
    List<TeamMemberView> teamMembers,
    List<TaskView> tasks,
    List<WorkLogView> workLogs,
    List<ChangeOrderView> changeOrders,
    CostStatementView costStatement,
    String contentSnapshot,
    AcceptanceView acceptanceDecision) {

  public record TeamMemberView(String name, String role, String reason) {}

  public record TaskView(
      int taskNumber, String name, String method, String status, String assigneeName) {}

  public record WorkLogView(
      int taskNumber,
      String engineerName,
      String startedAt,
      String endedAt,
      String hours,
      String asLeftCondition,
      String testReadings) {}

  public record ChangeOrderView(
      int changeNumber, String reason, String proposedDelta, String status, String decidedAt) {}

  public record CostStatementView(
      String currency,
      String baselineTotal,
      String approvedChangesTotal,
      String authorizedTotal,
      String actualTotal,
      String variance,
      String variancePercent) {}

  public record AcceptanceView(
      String decision,
      String reviewerName,
      String decidedAt,
      String technicalComments,
      String signatureReference) {}
}

namespace MinimalClean.Architecture.Web.Features.Reports;

public sealed record InspectionReportDto(
    Guid Id,
    Guid InspectionRequestId,
    Guid InspectorId,
    string Title,
    string? ReportNumber,
    string Findings,
    string? Summary,
    string? Recommendations,
    string Status,
    DateTime CreatedAt);

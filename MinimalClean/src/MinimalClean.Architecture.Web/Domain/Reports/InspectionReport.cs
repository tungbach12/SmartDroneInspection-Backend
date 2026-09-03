using MinimalClean.Architecture.Web.Domain.Common;
using MinimalClean.Architecture.Web.Domain.Reports.Enums;
using Ardalis.GuardClauses;

namespace MinimalClean.Architecture.Web.Domain.Reports;

public class InspectionReport : EntityBase<InspectionReport, InspectionReportId>, IAuditable, ISoftDelete, IHasVersion, IAggregateRoot
{
    private InspectionReport() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public InspectionReport(
        Guid organizationId = default,
        Guid inspectionRequestId = default,
        Guid inspectorId = default,
        string title = default!,
        string findings = default!,
        Guid? missionId = default!,
        string? reportNumber = default!,
        string? summary = default!,
        DateTime? summaryGeneratedAt = default!,
        string? summaryModelVersion = default!,
        string? recommendations = default!,
        ReportStatus status = default!,
        DateTime? submittedAt = default!,
        Guid? rejectedByUserId = default!,
        DateTime? rejectedAt = default!,
        string? rejectReason = default!,
        Guid? reviewedByUserId = default!,
        DateTime? reviewedAt = default!,
        string? reviewComment = default!)  
    {
        OrganizationId = Guard.Against.Default(organizationId, nameof(organizationId));
        InspectionRequestId = Guard.Against.Default(inspectionRequestId, nameof(inspectionRequestId));
        InspectorId = Guard.Against.Default(inspectorId, nameof(inspectorId));
        Title = Guard.Against.NullOrWhiteSpace(title, nameof(title));
        Findings = Guard.Against.NullOrWhiteSpace(findings, nameof(findings));
        MissionId = missionId;
        ReportNumber = reportNumber;
        Summary = summary;
        SummaryGeneratedAt = summaryGeneratedAt;
        SummaryModelVersion = summaryModelVersion;
        Recommendations = recommendations;
        Status = status;
        SubmittedAt = submittedAt;
        RejectedByUserId = rejectedByUserId;
        RejectedAt = rejectedAt;
        RejectReason = rejectReason;
        ReviewedByUserId = reviewedByUserId;
        ReviewedAt = reviewedAt;
        ReviewComment = reviewComment;
    }

    public Guid OrganizationId { get; private set; }
    public Guid InspectionRequestId { get; private set; }
    public Guid? MissionId { get; private set; }
    public Guid InspectorId { get; private set; }
    public string? ReportNumber { get; private set; }
    public string Title { get; private set; } = string.Empty;
    public string? Summary { get; private set; }
    public DateTime? SummaryGeneratedAt { get; private set; }
    public string? SummaryModelVersion { get; private set; }
    public string Findings { get; private set; } = string.Empty;
    public string? Recommendations { get; private set; }
    public ReportStatus Status { get; private set; } = ReportStatus.Draft;
    public DateTime? SubmittedAt { get; private set; }
    public Guid? RejectedByUserId { get; private set; }
    public DateTime? RejectedAt { get; private set; }
    public string? RejectReason { get; private set; }
    public Guid? ReviewedByUserId { get; private set; }
    public DateTime? ReviewedAt { get; private set; }
    public string? ReviewComment { get; private set; }
    public int Version { get; set; } = 1;
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
    public bool IsDeleted { get; set; }
    public DateTime? DeletedAt { get; set; }
    public Guid? DeletedBy { get; set; }

    public InspectionReport UpdateOrganizationId(Guid newOrganizationId)
    {
        OrganizationId = newOrganizationId;
        return this;
    }

    public InspectionReport UpdateInspectionRequestId(Guid newInspectionRequestId)
    {
        InspectionRequestId = newInspectionRequestId;
        return this;
    }

    public InspectionReport UpdateMissionId(Guid? newMissionId)
    {
        MissionId = newMissionId;
        return this;
    }

    public InspectionReport UpdateInspectorId(Guid newInspectorId)
    {
        InspectorId = newInspectorId;
        return this;
    }

    public InspectionReport UpdateReportNumber(string? newReportNumber)
    {
        ReportNumber = newReportNumber;
        return this;
    }

    public InspectionReport UpdateTitle(string newTitle)
    {
        Title = Guard.Against.NullOrWhiteSpace(newTitle, nameof(newTitle));
        return this;
    }

    public InspectionReport UpdateSummary(string? newSummary)
    {
        Summary = newSummary;
        return this;
    }

    public InspectionReport UpdateSummaryGeneratedAt(DateTime? newSummaryGeneratedAt)
    {
        SummaryGeneratedAt = newSummaryGeneratedAt;
        return this;
    }

    public InspectionReport UpdateSummaryModelVersion(string? newSummaryModelVersion)
    {
        SummaryModelVersion = newSummaryModelVersion;
        return this;
    }

    public InspectionReport UpdateFindings(string newFindings)
    {
        Findings = Guard.Against.NullOrWhiteSpace(newFindings, nameof(newFindings));
        return this;
    }

    public InspectionReport UpdateRecommendations(string? newRecommendations)
    {
        Recommendations = newRecommendations;
        return this;
    }

    public InspectionReport UpdateStatus(ReportStatus newStatus)
    {
        Status = newStatus;
        return this;
    }

    public InspectionReport UpdateSubmittedAt(DateTime? newSubmittedAt)
    {
        SubmittedAt = newSubmittedAt;
        return this;
    }

    public InspectionReport UpdateRejectedByUserId(Guid? newRejectedByUserId)
    {
        RejectedByUserId = newRejectedByUserId;
        return this;
    }

    public InspectionReport UpdateRejectedAt(DateTime? newRejectedAt)
    {
        RejectedAt = newRejectedAt;
        return this;
    }

    public InspectionReport UpdateRejectReason(string? newRejectReason)
    {
        RejectReason = newRejectReason;
        return this;
    }

    public InspectionReport UpdateReviewedByUserId(Guid? newReviewedByUserId)
    {
        ReviewedByUserId = newReviewedByUserId;
        return this;
    }

    public InspectionReport UpdateReviewedAt(DateTime? newReviewedAt)
    {
        ReviewedAt = newReviewedAt;
        return this;
    }

    public InspectionReport UpdateReviewComment(string? newReviewComment)
    {
        ReviewComment = newReviewComment;
        return this;
    }

}

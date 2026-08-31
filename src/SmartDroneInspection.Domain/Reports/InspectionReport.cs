using System.Text.Json;
using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Reports;

public class InspectionReport : BaseEntity, IAuditable, ISoftDelete, IHasVersion
{
    public Guid OrganizationId { get; set; }
    public Guid InspectionRequestId { get; set; }
    public Guid? MissionId { get; set; }
    public Guid InspectorId { get; set; }
    public string? ReportNumber { get; set; }
    public string Title { get; set; } = string.Empty;
    public string? Summary { get; set; }
    public DateTime? SummaryGeneratedAt { get; set; }
    public string? SummaryModelVersion { get; set; }
    public string Findings { get; set; } = string.Empty;
    public string? Recommendations { get; set; }
    public ReportStatus Status { get; set; } = ReportStatus.Draft;
    public DateTime? SubmittedAt { get; set; }
    public Guid? RejectedByUserId { get; set; }
    public DateTime? RejectedAt { get; set; }
    public string? RejectReason { get; set; }
    public Guid? ReviewedByUserId { get; set; }
    public DateTime? ReviewedAt { get; set; }
    public string? ReviewComment { get; set; }
    public int Version { get; set; } = 1;
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
    public bool IsDeleted { get; set; }
    public DateTime? DeletedAt { get; set; }
    public Guid? DeletedBy { get; set; }
}

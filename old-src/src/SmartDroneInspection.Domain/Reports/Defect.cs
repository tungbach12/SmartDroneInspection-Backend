using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Reports;

public class Defect : BaseEntity, IAuditable
{
    public Guid OrganizationId { get; set; }
    public Guid? FindingId { get; set; }
    public Guid ReportId { get; set; }
    public Guid AssetId { get; set; }
    public string? DefectNumber { get; set; }
    public string Title { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public DefectSeverity Severity { get; set; }
    public DefectCategory Category { get; set; }
    public string? RepairRecommendation { get; set; }
    public RepairPriority RepairPriority { get; set; }
    public decimal? EstimatedRepairCost { get; set; }
    public int? EstimatedRepairHours { get; set; }
    public DefectStatus Status { get; set; } = DefectStatus.Open;
    public DateTime DetectedAt { get; set; }
    public DateTime? ConfirmedAt { get; set; }
    public Guid? ConfirmedByUserId { get; set; }
    public DateTime? ResolvedAt { get; set; }
    public Guid? ResolvedByUserId { get; set; }
    public string? ResolutionNotes { get; set; }
    public DateTime? ClosedAt { get; set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
}

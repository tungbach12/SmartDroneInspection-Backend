using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Missions;

public class InspectionRequest : BaseEntity, IAuditable
{
    public Guid OrganizationId { get; set; }
    public Guid AssetId { get; set; }
    public Guid RequestedByUserId { get; set; }
    public Guid? InspectorId { get; set; }
    public Guid? PlanId { get; set; }
    public string Title { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public InspectionRequestPriority Priority { get; set; }
    public InspectionRequestStatus Status { get; set; } = InspectionRequestStatus.Pending;
    public Guid? DecidedByUserId { get; set; }
    public DateTime? DecidedAt { get; set; }
    public string? RejectReason { get; set; }
    public double? Latitude { get; set; }
    public double? Longitude { get; set; }
    public string? LocationOverride { get; set; }
    public DateTime? RequestedCompletionDate { get; set; }
    public DateTime? ActualCompletionDate { get; set; }
    public Guid? MissionCreationKey { get; set; }
    public int? EstimatedDurationMinutes { get; set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
}

using System.Text.Json;
using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Planning;

public class InspectionPlan : BaseEntity, IAuditable, ISoftDelete
{
    public Guid OrganizationId { get; set; }
    public Guid CreatedByUserId { get; set; }
    public string Title { get; set; } = string.Empty;
    public string? Description { get; set; }
    public FrequencyType FrequencyType { get; set; }
    public int FrequencyInterval { get; set; } = 1;
    public InspectionPlanPriority Priority { get; set; }
    public DateTime StartDate { get; set; }
    public DateTime? EndDate { get; set; }
    public DateTime NextRunDate { get; set; }
    public DateTime? LastRunDate { get; set; }
    public InspectionPlanStatus Status { get; set; } = InspectionPlanStatus.Draft;
    public DateTime? ActivatedAt { get; set; }
    public Guid? ActivatedByUserId { get; set; }
    public DateTime? PausedAt { get; set; }
    public string? PausedReason { get; set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
    public bool IsDeleted { get; set; }
    public DateTime? DeletedAt { get; set; }
    public Guid? DeletedBy { get; set; }
}

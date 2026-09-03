using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Planning;

public class InspectionSchedule : BaseEntity
{
    public Guid PlanId { get; set; }
    public Guid AssetId { get; set; }
    public DateTime ScheduledDate { get; set; }
    public DateTime? ScheduledEndDate { get; set; }
    public Guid? InspectorId { get; set; }
    public DateTime? AssignedAt { get; set; }
    public Guid? AssignedByUserId { get; set; }
    public ScheduleStatus Status { get; set; } = ScheduleStatus.Pending;
    public DateTime? CompletedAt { get; set; }
    public DateTime? CancelledAt { get; set; }
    public string? CancelledReason { get; set; }
    public Guid? RescheduledFromId { get; set; }
}

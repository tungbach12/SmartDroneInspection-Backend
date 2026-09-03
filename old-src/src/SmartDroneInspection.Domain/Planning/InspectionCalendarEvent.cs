using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Planning;

public class InspectionCalendarEvent : BaseEntity
{
    public Guid? PlanId { get; set; }
    public Guid? RequestId { get; set; }
    public Guid? ScheduleId { get; set; }
    public string Title { get; set; } = string.Empty;
    public string? Description { get; set; }
    public DateTime EventDate { get; set; }
    public DateTime? EndDate { get; set; }
    public bool AllDay { get; set; }
    public string? Location { get; set; }
    public string? RecurrenceRule { get; set; }
    public Guid? RecurrenceParentId { get; set; }
    public Guid CreatedByUserId { get; set; }
}

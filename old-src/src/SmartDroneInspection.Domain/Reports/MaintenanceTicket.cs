using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Reports;

public class MaintenanceTicket : BaseEntity, IAuditable
{
    public Guid OrganizationId { get; set; }
    public Guid? DefectId { get; set; }
    public Guid? RequestId { get; set; }
    public Guid? AssignedToUserId { get; set; }
    public DateTime? AssignedAt { get; set; }
    public Guid? AssignedByUserId { get; set; }
    public Guid CreatedByUserId { get; set; }
    public string? TicketNumber { get; set; }
    public string Title { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public TicketPriority Priority { get; set; }
    public TicketStatus Status { get; set; } = TicketStatus.Open;
    public DateTime? DueDate { get; set; }
    public DateTime? StartedAt { get; set; }
    public DateTime? ResolvedAt { get; set; }
    public DateTime? ClosedAt { get; set; }
    public string? ResolutionNotes { get; set; }
    public decimal? EstimatedCost { get; set; }
    public decimal? ActualCost { get; set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
}

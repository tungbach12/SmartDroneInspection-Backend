using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Reports;

public class TicketHistory : BaseEntity
{
    public Guid TicketId { get; set; }
    public TicketStatus? FromStatus { get; set; }
    public TicketStatus ToStatus { get; set; }
    public Guid ChangedByUserId { get; set; }
    public DateTime ChangedAt { get; set; }
    public string? Comment { get; set; }
    public int? TimeSpentMinutes { get; set; }
}

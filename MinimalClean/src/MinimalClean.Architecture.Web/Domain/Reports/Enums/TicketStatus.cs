using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Reports.Enums;

public sealed class TicketStatus : SmartEnum<TicketStatus>
{
    public static readonly TicketStatus Open = new(nameof(Open), 0);
    public static readonly TicketStatus Assigned = new(nameof(Assigned), 1);
    public static readonly TicketStatus InProgress = new(nameof(InProgress), 2);
    public static readonly TicketStatus Blocked = new(nameof(Blocked), 3);
    public static readonly TicketStatus Resolved = new(nameof(Resolved), 4);
    public static readonly TicketStatus Closed = new(nameof(Closed), 5);
    public static readonly TicketStatus Cancelled = new(nameof(Cancelled), 6);

    private TicketStatus(string name, int value) : base(name, value) { }
}

using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Reports.Enums;

public sealed class TicketPriority : SmartEnum<TicketPriority>
{
    public static readonly TicketPriority Low = new(nameof(Low), 0);
    public static readonly TicketPriority Medium = new(nameof(Medium), 1);
    public static readonly TicketPriority High = new(nameof(High), 2);
    public static readonly TicketPriority Urgent = new(nameof(Urgent), 3);

    private TicketPriority(string name, int value) : base(name, value) { }
}

using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Reports.Enums;

public sealed class RepairPriority : SmartEnum<RepairPriority>
{
    public static readonly RepairPriority Low = new(nameof(Low), 0);
    public static readonly RepairPriority Medium = new(nameof(Medium), 1);
    public static readonly RepairPriority High = new(nameof(High), 2);
    public static readonly RepairPriority Urgent = new(nameof(Urgent), 3);

    private RepairPriority(string name, int value) : base(name, value) { }
}

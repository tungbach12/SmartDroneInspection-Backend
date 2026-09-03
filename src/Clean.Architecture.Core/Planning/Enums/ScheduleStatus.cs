using Ardalis.SmartEnum;

namespace Clean.Architecture.Core.Planning.Enums;

public sealed class ScheduleStatus : SmartEnum<ScheduleStatus>
{
    public static readonly ScheduleStatus Pending = new(nameof(Pending), 0);
    public static readonly ScheduleStatus Confirmed = new(nameof(Confirmed), 1);
    public static readonly ScheduleStatus InProgress = new(nameof(InProgress), 2);
    public static readonly ScheduleStatus Completed = new(nameof(Completed), 3);
    public static readonly ScheduleStatus Missed = new(nameof(Missed), 4);
    public static readonly ScheduleStatus Cancelled = new(nameof(Cancelled), 5);
    public static readonly ScheduleStatus Rescheduled = new(nameof(Rescheduled), 6);

    private ScheduleStatus(string name, int value) : base(name, value) { }
}

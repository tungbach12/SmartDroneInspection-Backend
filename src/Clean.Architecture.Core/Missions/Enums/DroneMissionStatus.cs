using Ardalis.SmartEnum;

namespace Clean.Architecture.Core.Missions.Enums;

public sealed class DroneMissionStatus : SmartEnum<DroneMissionStatus>
{
    public static readonly DroneMissionStatus Created = new(nameof(Created), 0);
    public static readonly DroneMissionStatus Queued = new(nameof(Queued), 1);
    public static readonly DroneMissionStatus InFlight = new(nameof(InFlight), 2);
    public static readonly DroneMissionStatus Paused = new(nameof(Paused), 3);
    public static readonly DroneMissionStatus Completed = new(nameof(Completed), 4);
    public static readonly DroneMissionStatus Failed = new(nameof(Failed), 5);
    public static readonly DroneMissionStatus Aborted = new(nameof(Aborted), 6);
    public static readonly DroneMissionStatus Cancelled = new(nameof(Cancelled), 7);

    private DroneMissionStatus(string name, int value) : base(name, value) { }
}

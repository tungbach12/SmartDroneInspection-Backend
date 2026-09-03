using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Missions.Enums;

public sealed class MissionCreatedVia : SmartEnum<MissionCreatedVia>
{
    public static readonly MissionCreatedVia Api = new(nameof(Api), 0);
    public static readonly MissionCreatedVia Scheduled = new(nameof(Scheduled), 1);
    public static readonly MissionCreatedVia Manual = new(nameof(Manual), 2);

    private MissionCreatedVia(string name, int value) : base(name, value) { }
}

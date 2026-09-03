using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Missions.Enums;

public sealed class FlightLogType : SmartEnum<FlightLogType>
{
    public static readonly FlightLogType Info = new(nameof(Info), 0);
    public static readonly FlightLogType Warning = new(nameof(Warning), 1);
    public static readonly FlightLogType Error = new(nameof(Error), 2);
    public static readonly FlightLogType Critical = new(nameof(Critical), 3);
    public static readonly FlightLogType System = new(nameof(System), 4);
    public static readonly FlightLogType Operator = new(nameof(Operator), 5);

    private FlightLogType(string name, int value) : base(name, value) { }
}

using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Missions.Enums;

public sealed class InspectionRequestPriority : SmartEnum<InspectionRequestPriority>
{
    public static readonly InspectionRequestPriority Low = new(nameof(Low), 0);
    public static readonly InspectionRequestPriority Medium = new(nameof(Medium), 1);
    public static readonly InspectionRequestPriority High = new(nameof(High), 2);
    public static readonly InspectionRequestPriority Critical = new(nameof(Critical), 3);
    public static readonly InspectionRequestPriority Emergency = new(nameof(Emergency), 4);

    private InspectionRequestPriority(string name, int value) : base(name, value) { }
}

using Ardalis.SmartEnum;

namespace Clean.Architecture.Core.Planning.Enums;

public sealed class InspectionPlanPriority : SmartEnum<InspectionPlanPriority>
{
    public static readonly InspectionPlanPriority Low = new(nameof(Low), 0);
    public static readonly InspectionPlanPriority Medium = new(nameof(Medium), 1);
    public static readonly InspectionPlanPriority High = new(nameof(High), 2);
    public static readonly InspectionPlanPriority Critical = new(nameof(Critical), 3);

    private InspectionPlanPriority(string name, int value) : base(name, value) { }
}

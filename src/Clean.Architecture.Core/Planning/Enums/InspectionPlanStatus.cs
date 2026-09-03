using Ardalis.SmartEnum;

namespace Clean.Architecture.Core.Planning.Enums;

public sealed class InspectionPlanStatus : SmartEnum<InspectionPlanStatus>
{
    public static readonly InspectionPlanStatus Draft = new(nameof(Draft), 0);
    public static readonly InspectionPlanStatus Active = new(nameof(Active), 1);
    public static readonly InspectionPlanStatus Paused = new(nameof(Paused), 2);
    public static readonly InspectionPlanStatus Completed = new(nameof(Completed), 3);
    public static readonly InspectionPlanStatus Cancelled = new(nameof(Cancelled), 4);
    public static readonly InspectionPlanStatus Archived = new(nameof(Archived), 5);

    private InspectionPlanStatus(string name, int value) : base(name, value) { }
}

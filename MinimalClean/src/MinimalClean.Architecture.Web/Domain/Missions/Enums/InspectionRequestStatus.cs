using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Missions.Enums;

public sealed class InspectionRequestStatus : SmartEnum<InspectionRequestStatus>
{
    public static readonly InspectionRequestStatus Pending = new(nameof(Pending), 0);
    public static readonly InspectionRequestStatus Approved = new(nameof(Approved), 1);
    public static readonly InspectionRequestStatus Rejected = new(nameof(Rejected), 2);
    public static readonly InspectionRequestStatus Cancelled = new(nameof(Cancelled), 3);
    public static readonly InspectionRequestStatus InProgress = new(nameof(InProgress), 4);
    public static readonly InspectionRequestStatus Completed = new(nameof(Completed), 5);

    private InspectionRequestStatus(string name, int value) : base(name, value) { }
}

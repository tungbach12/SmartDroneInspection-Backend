using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Reports.Enums;

public sealed class DefectStatus : SmartEnum<DefectStatus>
{
    public static readonly DefectStatus Open = new(nameof(Open), 0);
    public static readonly DefectStatus Confirmed = new(nameof(Confirmed), 1);
    public static readonly DefectStatus InRepair = new(nameof(InRepair), 2);
    public static readonly DefectStatus Resolved = new(nameof(Resolved), 3);
    public static readonly DefectStatus Closed = new(nameof(Closed), 4);
    public static readonly DefectStatus WontFix = new(nameof(WontFix), 5);
    public static readonly DefectStatus Reopened = new(nameof(Reopened), 6);

    private DefectStatus(string name, int value) : base(name, value) { }
}

using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Reports.Enums;

public sealed class ReportStatus : SmartEnum<ReportStatus>
{
    public static readonly ReportStatus Draft = new(nameof(Draft), 0);
    public static readonly ReportStatus Submitted = new(nameof(Submitted), 1);
    public static readonly ReportStatus Approved = new(nameof(Approved), 2);
    public static readonly ReportStatus Rejected = new(nameof(Rejected), 3);
    public static readonly ReportStatus Archived = new(nameof(Archived), 4);

    private ReportStatus(string name, int value) : base(name, value) { }
}

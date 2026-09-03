using Ardalis.SmartEnum;

namespace Clean.Architecture.Core.Reports.Enums;

public sealed class DefectSeverity : SmartEnum<DefectSeverity>
{
    public static readonly DefectSeverity Low = new(nameof(Low), 0);
    public static readonly DefectSeverity Medium = new(nameof(Medium), 1);
    public static readonly DefectSeverity High = new(nameof(High), 2);
    public static readonly DefectSeverity Critical = new(nameof(Critical), 3);

    private DefectSeverity(string name, int value) : base(name, value) { }
}

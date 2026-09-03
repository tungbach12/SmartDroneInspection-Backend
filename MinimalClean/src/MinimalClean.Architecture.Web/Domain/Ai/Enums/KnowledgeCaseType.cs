using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Ai.Enums;

public sealed class KnowledgeCaseType : SmartEnum<KnowledgeCaseType>
{
    public static readonly KnowledgeCaseType DefectPattern = new(nameof(DefectPattern), 0);
    public static readonly KnowledgeCaseType RepairProcedure = new(nameof(RepairProcedure), 1);
    public static readonly KnowledgeCaseType MaintenanceGuide = new(nameof(MaintenanceGuide), 2);
    public static readonly KnowledgeCaseType RegulatoryRequirement = new(nameof(RegulatoryRequirement), 3);

    private KnowledgeCaseType(string name, int value) : base(name, value) { }
}

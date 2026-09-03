using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Ai.Enums;

public sealed class AiJobType : SmartEnum<AiJobType>
{
    public static readonly AiJobType VisionAnalysis = new(nameof(VisionAnalysis), 0);
    public static readonly AiJobType ReportSummary = new(nameof(ReportSummary), 1);
    public static readonly AiJobType CaseRecommendation = new(nameof(CaseRecommendation), 2);
    public static readonly AiJobType DefectClassification = new(nameof(DefectClassification), 3);

    private AiJobType(string name, int value) : base(name, value) { }
}

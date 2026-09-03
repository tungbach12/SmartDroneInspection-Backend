using Vogen;

namespace Clean.Architecture.Core.Ai;

[ValueObject<Guid>]
public readonly partial struct AiAnalysisJobId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("AiAnalysisJobId cannot be empty.");
}

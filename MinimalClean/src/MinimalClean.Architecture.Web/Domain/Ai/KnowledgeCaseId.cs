using Vogen;

namespace MinimalClean.Architecture.Web.Domain.Ai;

[ValueObject<Guid>]
public readonly partial struct KnowledgeCaseId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("KnowledgeCaseId cannot be empty.");
}

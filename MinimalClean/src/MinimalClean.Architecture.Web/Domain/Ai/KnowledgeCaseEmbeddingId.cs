using Vogen;

namespace MinimalClean.Architecture.Web.Domain.Ai;

[ValueObject<Guid>]
public readonly partial struct KnowledgeCaseEmbeddingId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("KnowledgeCaseEmbeddingId cannot be empty.");
}

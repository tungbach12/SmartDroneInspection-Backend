using Ardalis.GuardClauses;

namespace MinimalClean.Architecture.Web.Domain.Ai;

public class KnowledgeCaseEmbedding : EntityBase<KnowledgeCaseEmbedding, KnowledgeCaseEmbeddingId>, IAggregateRoot
{
    private KnowledgeCaseEmbedding() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public KnowledgeCaseEmbedding(
        Guid knowledgeCaseId = default,
        string modelName = default!,
        DateTime embeddedAt = default,
        EmbeddingVector embedding = default!,
        string? modelVersion = default!)  
    {
        KnowledgeCaseId = Guard.Against.Default(knowledgeCaseId, nameof(knowledgeCaseId));
        ModelName = Guard.Against.NullOrWhiteSpace(modelName, nameof(modelName));
        EmbeddedAt = embeddedAt;
        Embedding = embedding;
        ModelVersion = modelVersion;
    }

    public Guid KnowledgeCaseId { get; private set; }
    public EmbeddingVector Embedding { get; private set; } = new(new float[EmbeddingVector.Dimension]);
    public string ModelName { get; private set; } = string.Empty;
    public string? ModelVersion { get; private set; }
    public DateTime EmbeddedAt { get; private set; }

    public KnowledgeCaseEmbedding UpdateKnowledgeCaseId(Guid newKnowledgeCaseId)
    {
        KnowledgeCaseId = newKnowledgeCaseId;
        return this;
    }

    public KnowledgeCaseEmbedding UpdateEmbedding(EmbeddingVector newEmbedding)
    {
        Embedding = newEmbedding;
        return this;
    }

    public KnowledgeCaseEmbedding UpdateModelName(string newModelName)
    {
        ModelName = Guard.Against.NullOrWhiteSpace(newModelName, nameof(newModelName));
        return this;
    }

    public KnowledgeCaseEmbedding UpdateModelVersion(string? newModelVersion)
    {
        ModelVersion = newModelVersion;
        return this;
    }

    public KnowledgeCaseEmbedding UpdateEmbeddedAt(DateTime newEmbeddedAt)
    {
        EmbeddedAt = newEmbeddedAt;
        return this;
    }

}

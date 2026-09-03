using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Ai;

public class KnowledgeCaseEmbedding : BaseEntity
{
    public Guid KnowledgeCaseId { get; set; }
    public EmbeddingVector Embedding { get; set; } = new(new float[EmbeddingVector.Dimension]);
    public string ModelName { get; set; } = string.Empty;
    public string? ModelVersion { get; set; }
    public DateTime EmbeddedAt { get; set; }
}

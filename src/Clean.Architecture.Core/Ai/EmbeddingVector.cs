namespace Clean.Architecture.Core.Ai;

/// <summary>
/// Framework-independent embedding value object. Infrastructure maps it to
/// PostgreSQL vector(1536) through an EF Core value converter.
/// </summary>
public sealed class EmbeddingVector
{
    public const int Dimension = 1536;

    public IReadOnlyList<float> Values { get; }

    public EmbeddingVector(IEnumerable<float> values)
    {
        var materialized = values.ToArray();
        if (materialized.Length != Dimension)
        {
            throw new ArgumentException($"An embedding must have exactly {Dimension} dimensions.", nameof(values));
        }

        Values = materialized;
    }
}

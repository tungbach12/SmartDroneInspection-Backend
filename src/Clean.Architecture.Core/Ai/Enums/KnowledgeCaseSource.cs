using Ardalis.SmartEnum;

namespace Clean.Architecture.Core.Ai.Enums;

public sealed class KnowledgeCaseSource : SmartEnum<KnowledgeCaseSource>
{
    public static readonly KnowledgeCaseSource Curated = new(nameof(Curated), 0);
    public static readonly KnowledgeCaseSource Learned = new(nameof(Learned), 1);
    public static readonly KnowledgeCaseSource Imported = new(nameof(Imported), 2);

    private KnowledgeCaseSource(string name, int value) : base(name, value) { }
}

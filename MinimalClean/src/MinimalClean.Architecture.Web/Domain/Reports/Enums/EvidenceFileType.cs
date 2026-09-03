using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Reports.Enums;

public sealed class EvidenceFileType : SmartEnum<EvidenceFileType>
{
    public static readonly EvidenceFileType Image = new(nameof(Image), 0);
    public static readonly EvidenceFileType Video = new(nameof(Video), 1);
    public static readonly EvidenceFileType Document = new(nameof(Document), 2);
    public static readonly EvidenceFileType Audio = new(nameof(Audio), 3);
    public static readonly EvidenceFileType Other = new(nameof(Other), 4);

    private EvidenceFileType(string name, int value) : base(name, value) { }
}

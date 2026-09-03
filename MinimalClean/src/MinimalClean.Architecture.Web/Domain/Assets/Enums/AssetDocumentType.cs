using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Assets.Enums;

public sealed class AssetDocumentType : SmartEnum<AssetDocumentType>
{
    public static readonly AssetDocumentType Manual = new(nameof(Manual), 0);
    public static readonly AssetDocumentType Certificate = new(nameof(Certificate), 1);
    public static readonly AssetDocumentType Warranty = new(nameof(Warranty), 2);
    public static readonly AssetDocumentType Drawing = new(nameof(Drawing), 3);
    public static readonly AssetDocumentType Photo = new(nameof(Photo), 4);
    public static readonly AssetDocumentType Other = new(nameof(Other), 5);

    private AssetDocumentType(string name, int value) : base(name, value) { }
}

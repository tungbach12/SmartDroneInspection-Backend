using Ardalis.SmartEnum;

namespace Clean.Architecture.Core.Assets.Enums;

public sealed class AssetStatus : SmartEnum<AssetStatus>
{
    public static readonly AssetStatus Active = new(nameof(Active), 0);
    public static readonly AssetStatus UnderMaintenance = new(nameof(UnderMaintenance), 1);
    public static readonly AssetStatus Retired = new(nameof(Retired), 2);

    private AssetStatus(string name, int value) : base(name, value) { }
}

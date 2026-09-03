using Ardalis.SmartEnum;

namespace Clean.Architecture.Core.Missions.Enums;

public sealed class MissionType : SmartEnum<MissionType>
{
    public static readonly MissionType Visual = new(nameof(Visual), 0);
    public static readonly MissionType Thermal = new(nameof(Thermal), 1);
    public static readonly MissionType Multispectral = new(nameof(Multispectral), 2);
    public static readonly MissionType Lidar = new(nameof(Lidar), 3);
    public static readonly MissionType Custom = new(nameof(Custom), 4);

    private MissionType(string name, int value) : base(name, value) { }
}

using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Reports.Enums;

public sealed class DefectCategory : SmartEnum<DefectCategory>
{
    public static readonly DefectCategory Crack = new(nameof(Crack), 0);
    public static readonly DefectCategory Corrosion = new(nameof(Corrosion), 1);
    public static readonly DefectCategory Deformation = new(nameof(Deformation), 2);
    public static readonly DefectCategory WaterDamage = new(nameof(WaterDamage), 3);
    public static readonly DefectCategory Vegetation = new(nameof(Vegetation), 4);
    public static readonly DefectCategory Equipment = new(nameof(Equipment), 5);
    public static readonly DefectCategory Other = new(nameof(Other), 6);

    private DefectCategory(string name, int value) : base(name, value) { }
}

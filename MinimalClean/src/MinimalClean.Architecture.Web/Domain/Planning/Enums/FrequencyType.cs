using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Planning.Enums;

public sealed class FrequencyType : SmartEnum<FrequencyType>
{
    public static readonly FrequencyType Once = new(nameof(Once), 0);
    public static readonly FrequencyType Weekly = new(nameof(Weekly), 1);
    public static readonly FrequencyType Monthly = new(nameof(Monthly), 2);
    public static readonly FrequencyType Quarterly = new(nameof(Quarterly), 3);
    public static readonly FrequencyType Yearly = new(nameof(Yearly), 4);
    public static readonly FrequencyType Custom = new(nameof(Custom), 5);

    private FrequencyType(string name, int value) : base(name, value) { }
}

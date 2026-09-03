using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Planning.Enums;

public sealed class DeliveryChannel : SmartEnum<DeliveryChannel>
{
    public static readonly DeliveryChannel InApp = new(nameof(InApp), 0);
    public static readonly DeliveryChannel Email = new(nameof(Email), 1);
    public static readonly DeliveryChannel Push = new(nameof(Push), 2);
    public static readonly DeliveryChannel All = new(nameof(All), 3);

    private DeliveryChannel(string name, int value) : base(name, value) { }
}

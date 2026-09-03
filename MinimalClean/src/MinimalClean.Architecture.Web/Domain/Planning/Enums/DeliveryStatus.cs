using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Planning.Enums;

public sealed class DeliveryStatus : SmartEnum<DeliveryStatus>
{
    public static readonly DeliveryStatus Pending = new(nameof(Pending), 0);
    public static readonly DeliveryStatus Sent = new(nameof(Sent), 1);
    public static readonly DeliveryStatus Failed = new(nameof(Failed), 2);
    public static readonly DeliveryStatus Bounced = new(nameof(Bounced), 3);

    private DeliveryStatus(string name, int value) : base(name, value) { }
}

using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Planning.Enums;

public sealed class NotificationType : SmartEnum<NotificationType>
{
    public static readonly NotificationType Info = new(nameof(Info), 0);
    public static readonly NotificationType Warning = new(nameof(Warning), 1);
    public static readonly NotificationType Success = new(nameof(Success), 2);
    public static readonly NotificationType Error = new(nameof(Error), 3);
    public static readonly NotificationType ActionRequired = new(nameof(ActionRequired), 4);

    private NotificationType(string name, int value) : base(name, value) { }
}

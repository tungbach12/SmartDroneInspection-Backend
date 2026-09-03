using Vogen;

namespace Clean.Architecture.Core.Planning;

[ValueObject<Guid>]
public readonly partial struct NotificationId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("NotificationId cannot be empty.");
}

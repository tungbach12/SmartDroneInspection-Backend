using Vogen;

namespace Clean.Architecture.Core.Users;

[ValueObject<Guid>]
public readonly partial struct UserId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("UserId cannot be empty.");
}

using Vogen;

namespace MinimalClean.Architecture.Web.Domain.Users;

[ValueObject<Guid>]
public readonly partial struct RefreshTokenId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("RefreshTokenId cannot be empty.");
}

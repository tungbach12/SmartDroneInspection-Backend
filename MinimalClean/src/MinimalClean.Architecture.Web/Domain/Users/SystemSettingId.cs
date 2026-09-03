using Vogen;

namespace MinimalClean.Architecture.Web.Domain.Users;

[ValueObject<Guid>]
public readonly partial struct SystemSettingId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("SystemSettingId cannot be empty.");
}

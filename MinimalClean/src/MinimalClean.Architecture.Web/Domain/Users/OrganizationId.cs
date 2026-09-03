using Vogen;

namespace MinimalClean.Architecture.Web.Domain.Users;

[ValueObject<Guid>]
public readonly partial struct OrganizationId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("OrganizationId cannot be empty.");
}

using Vogen;

namespace Clean.Architecture.Core.Users;

[ValueObject<Guid>]
public readonly partial struct AuditLogId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("AuditLogId cannot be empty.");
}

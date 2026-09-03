using Vogen;

namespace Clean.Architecture.Core.Reports;

[ValueObject<Guid>]
public readonly partial struct MaintenanceTicketId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("MaintenanceTicketId cannot be empty.");
}

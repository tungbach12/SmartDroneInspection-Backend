using Vogen;

namespace MinimalClean.Architecture.Web.Domain.Missions;

[ValueObject<Guid>]
public readonly partial struct DroneMissionId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("DroneMissionId cannot be empty.");
}

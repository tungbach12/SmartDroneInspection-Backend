using Vogen;

namespace MinimalClean.Architecture.Web.Domain.Missions;

[ValueObject<Guid>]
public readonly partial struct MissionFlightLogId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("MissionFlightLogId cannot be empty.");
}

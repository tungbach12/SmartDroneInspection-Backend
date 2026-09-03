using Vogen;

namespace Clean.Architecture.Core.Missions;

[ValueObject<Guid>]
public readonly partial struct MissionFlightLogId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("MissionFlightLogId cannot be empty.");
}

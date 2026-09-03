using Vogen;

namespace Clean.Architecture.Core.Missions;

[ValueObject<Guid>]
public readonly partial struct MissionTelemetryId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("MissionTelemetryId cannot be empty.");
}

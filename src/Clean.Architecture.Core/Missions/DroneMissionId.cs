using Vogen;

namespace Clean.Architecture.Core.Missions;

[ValueObject<Guid>]
public readonly partial struct DroneMissionId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("DroneMissionId cannot be empty.");
}

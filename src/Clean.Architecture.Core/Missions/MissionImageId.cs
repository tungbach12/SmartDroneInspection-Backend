using Vogen;

namespace Clean.Architecture.Core.Missions;

[ValueObject<Guid>]
public readonly partial struct MissionImageId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("MissionImageId cannot be empty.");
}

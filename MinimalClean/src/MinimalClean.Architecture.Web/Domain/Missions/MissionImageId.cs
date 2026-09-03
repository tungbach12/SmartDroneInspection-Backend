using Vogen;

namespace MinimalClean.Architecture.Web.Domain.Missions;

[ValueObject<Guid>]
public readonly partial struct MissionImageId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("MissionImageId cannot be empty.");
}

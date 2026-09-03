using Vogen;

namespace Clean.Architecture.Core.Missions;

[ValueObject<Guid>]
public readonly partial struct InspectionRequestId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("InspectionRequestId cannot be empty.");
}

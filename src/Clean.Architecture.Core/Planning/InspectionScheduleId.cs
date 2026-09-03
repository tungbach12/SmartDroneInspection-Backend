using Vogen;

namespace Clean.Architecture.Core.Planning;

[ValueObject<Guid>]
public readonly partial struct InspectionScheduleId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("InspectionScheduleId cannot be empty.");
}

using Vogen;

namespace Clean.Architecture.Core.Planning;

[ValueObject<Guid>]
public readonly partial struct InspectionCalendarEventId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("InspectionCalendarEventId cannot be empty.");
}

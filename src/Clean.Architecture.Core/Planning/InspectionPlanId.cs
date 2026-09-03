using Vogen;

namespace Clean.Architecture.Core.Planning;

[ValueObject<Guid>]
public readonly partial struct InspectionPlanId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("InspectionPlanId cannot be empty.");
}

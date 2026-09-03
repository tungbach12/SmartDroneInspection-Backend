using Vogen;

namespace MinimalClean.Architecture.Web.Domain.Planning;

[ValueObject<Guid>]
public readonly partial struct InspectionPlanId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("InspectionPlanId cannot be empty.");
}

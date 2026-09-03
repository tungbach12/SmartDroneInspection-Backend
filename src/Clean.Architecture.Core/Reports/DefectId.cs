using Vogen;

namespace Clean.Architecture.Core.Reports;

[ValueObject<Guid>]
public readonly partial struct DefectId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("DefectId cannot be empty.");
}

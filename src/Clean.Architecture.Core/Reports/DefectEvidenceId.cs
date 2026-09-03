using Vogen;

namespace Clean.Architecture.Core.Reports;

[ValueObject<Guid>]
public readonly partial struct DefectEvidenceId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("DefectEvidenceId cannot be empty.");
}

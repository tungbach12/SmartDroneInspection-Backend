using Vogen;

namespace Clean.Architecture.Core.Reports;

[ValueObject<Guid>]
public readonly partial struct ReportEvidenceId
{
    private static Validation Validate(Guid value) => value != Guid.Empty ? Validation.Ok : Validation.Invalid("ReportEvidenceId cannot be empty.");
}

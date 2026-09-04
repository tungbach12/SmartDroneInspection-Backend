using FastEndpoints;
using FluentValidation;
using Microsoft.AspNetCore.Http.HttpResults;
using MinimalClean.Architecture.Web.Domain.Reports;
using MinimalClean.Architecture.Web.Domain.Reports.Enums;

namespace MinimalClean.Architecture.Web.Features.Reports.Create;

public sealed record CreateInspectionReportRequest
{
    public Guid OrganizationId { get; init; }
    public Guid InspectionRequestId { get; init; }
    public Guid InspectorId { get; init; }
    public Guid? MissionId { get; init; }
    public string Title { get; init; } = string.Empty;
    public string Findings { get; init; } = string.Empty;
    public string? Recommendations { get; init; }
}

public sealed class CreateInspectionReportValidator : Validator<CreateInspectionReportRequest>
{
    public CreateInspectionReportValidator()
    {
        RuleFor(x => x.Title).NotEmpty().MaximumLength(250);
        RuleFor(x => x.Findings).NotEmpty();
        RuleFor(x => x.InspectionRequestId).NotEmpty();
        RuleFor(x => x.InspectorId).NotEmpty();
    }
}

public sealed class CreateInspectionReportEndpoint(IRepository<InspectionReport> repository)
    : Endpoint<CreateInspectionReportRequest, Results<Created<InspectionReportDto>, ValidationProblem, ProblemHttpResult>>
{
    public override void Configure()
    {
        Post("/reports");
        AllowAnonymous();
        Tags("Reports");
        Summary(s =>
        {
            s.Summary = "Create inspection report (Draft)";
            s.Description = "Creates a new inspection report draft with findings and recommendations.";
        });
    }

    public override async Task<Results<Created<InspectionReportDto>, ValidationProblem, ProblemHttpResult>> ExecuteAsync(CreateInspectionReportRequest req, CancellationToken ct)
    {
        var reportNumber = $"REP-{DateTime.UtcNow:yyyyMMdd}-{Random.Shared.Next(1000, 9999)}";

        var report = new InspectionReport(
            organizationId: req.OrganizationId,
            inspectionRequestId: req.InspectionRequestId,
            inspectorId: req.InspectorId,
            title: req.Title,
            findings: req.Findings,
            missionId: req.MissionId,
            reportNumber: reportNumber,
            recommendations: req.Recommendations,
            status: ReportStatus.Draft);

        await repository.AddAsync(report, ct);
        await repository.SaveChangesAsync(ct);

        var dto = new InspectionReportDto(
            report.Id.Value,
            report.InspectionRequestId,
            report.InspectorId,
            report.Title,
            report.ReportNumber,
            report.Findings,
            report.Summary,
            report.Recommendations,
            report.Status.Name,
            report.CreatedAt);

        return TypedResults.Created($"/reports/{report.Id.Value}", dto);
    }
}

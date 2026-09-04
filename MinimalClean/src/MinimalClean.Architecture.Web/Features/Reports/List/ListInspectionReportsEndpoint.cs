using Ardalis.Specification;
using FastEndpoints;
using Microsoft.AspNetCore.Http.HttpResults;
using MinimalClean.Architecture.Web.Domain.Reports;
using MinimalClean.Architecture.Web.Domain.Reports.Enums;

namespace MinimalClean.Architecture.Web.Features.Reports.List;

public sealed class PagedInspectionReportsSpec : Specification<InspectionReport>
{
    public PagedInspectionReportsSpec(
        Guid organizationId,
        int page,
        int pageSize,
        Guid? inspectionRequestId = null,
        ReportStatus? status = null)
    {
        var query = Query.Where(r => r.OrganizationId == organizationId);

        if (inspectionRequestId.HasValue)
        {
            query.Where(r => r.InspectionRequestId == inspectionRequestId.Value);
        }

        if (status is not null)
        {
            query.Where(r => r.Status == status);
        }

        var skip = Math.Max(0, (page - 1) * pageSize);
        var take = Math.Clamp(pageSize, 1, 100);

        query
            .OrderByDescending(r => r.CreatedAt)
            .Skip(skip)
            .Take(take);
    }
}

public sealed record ListInspectionReportsRequest
{
    public Guid OrganizationId { get; init; }
    public int Page { get; init; } = 1;
    public int PageSize { get; init; } = 20;
    public Guid? InspectionRequestId { get; init; }
    public string? Status { get; init; }
}

public sealed record ListInspectionReportsResponse(
    IReadOnlyList<InspectionReportDto> Items,
    int Page,
    int PageSize,
    int TotalCount);

public sealed class ListInspectionReportsEndpoint(IRepository<InspectionReport> repository)
    : Endpoint<ListInspectionReportsRequest, Ok<ListInspectionReportsResponse>>
{
    public override void Configure()
    {
        Get("/reports");
        AllowAnonymous();
        Tags("Reports");
        Summary(s =>
        {
            s.Summary = "List paginated inspection reports";
            s.Description = "Retrieves inspection reports with optional status filter.";
        });
    }

    public override async Task<Ok<ListInspectionReportsResponse>> ExecuteAsync(ListInspectionReportsRequest req, CancellationToken ct)
    {
        ReportStatus? statusEnum = null;
        if (!string.IsNullOrWhiteSpace(req.Status) && ReportStatus.TryFromName(req.Status, true, out var parsed))
        {
            statusEnum = parsed;
        }

        var spec = new PagedInspectionReportsSpec(
            req.OrganizationId,
            req.Page,
            req.PageSize,
            req.InspectionRequestId,
            statusEnum);

        var reports = await repository.ListAsync(spec, ct);

        var countSpec = new PagedInspectionReportsSpec(
            req.OrganizationId,
            1,
            int.MaxValue,
            req.InspectionRequestId,
            statusEnum);
        var totalCount = await repository.CountAsync(countSpec, ct);

        var dtos = reports.Select(r => new InspectionReportDto(
            r.Id.Value,
            r.InspectionRequestId,
            r.InspectorId,
            r.Title,
            r.ReportNumber,
            r.Findings,
            r.Summary,
            r.Recommendations,
            r.Status.Name,
            r.CreatedAt)).ToList();

        return TypedResults.Ok(new ListInspectionReportsResponse(dtos, req.Page, req.PageSize, totalCount));
    }
}

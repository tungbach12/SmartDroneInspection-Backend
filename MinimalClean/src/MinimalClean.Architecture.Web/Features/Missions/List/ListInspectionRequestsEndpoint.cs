using Ardalis.Specification;
using FastEndpoints;
using Microsoft.AspNetCore.Http.HttpResults;
using MinimalClean.Architecture.Web.Domain.Missions;
using MinimalClean.Architecture.Web.Domain.Missions.Enums;
using MinimalClean.Architecture.Web.Features.Missions.Create;

namespace MinimalClean.Architecture.Web.Features.Missions.List;

public sealed class PagedInspectionRequestsSpec : Specification<InspectionRequest>
{
    public PagedInspectionRequestsSpec(
        Guid organizationId,
        int page,
        int pageSize,
        Guid? assetId = null,
        InspectionRequestStatus? status = null)
    {
        var query = Query.Where(r => r.OrganizationId == organizationId);

        if (assetId.HasValue)
        {
            query.Where(r => r.AssetId == assetId.Value);
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

public sealed record ListInspectionRequestsRequest
{
    public Guid OrganizationId { get; init; }
    public int Page { get; init; } = 1;
    public int PageSize { get; init; } = 20;
    public Guid? AssetId { get; init; }
    public string? Status { get; init; }
}

public sealed record ListInspectionRequestsResponse(
    IReadOnlyList<InspectionRequestDto> Items,
    int Page,
    int PageSize,
    int TotalCount);

public sealed class ListInspectionRequestsEndpoint(IRepository<InspectionRequest> repository)
    : Endpoint<ListInspectionRequestsRequest, Ok<ListInspectionRequestsResponse>>
{
    public override void Configure()
    {
        Get("/missions/requests");
        AllowAnonymous();
        Tags("Missions");
        Summary(s =>
        {
            s.Summary = "List paginated inspection requests";
            s.Description = "Retrieves inspection requests with optional filtering by asset and status.";
        });
    }

    public override async Task<Ok<ListInspectionRequestsResponse>> ExecuteAsync(ListInspectionRequestsRequest req, CancellationToken ct)
    {
        InspectionRequestStatus? statusEnum = null;
        if (!string.IsNullOrWhiteSpace(req.Status) && InspectionRequestStatus.TryFromName(req.Status, true, out var parsed))
        {
            statusEnum = parsed;
        }

        var spec = new PagedInspectionRequestsSpec(
            req.OrganizationId,
            req.Page,
            req.PageSize,
            req.AssetId,
            statusEnum);

        var requests = await repository.ListAsync(spec, ct);

        var countSpec = new PagedInspectionRequestsSpec(
            req.OrganizationId,
            1,
            int.MaxValue,
            req.AssetId,
            statusEnum);
        var totalCount = await repository.CountAsync(countSpec, ct);

        var dtos = requests.Select(r => new InspectionRequestDto(
            r.Id.Value,
            r.AssetId,
            r.Title,
            r.Status.Name,
            r.CreatedAt)).ToList();

        return TypedResults.Ok(new ListInspectionRequestsResponse(dtos, req.Page, req.PageSize, totalCount));
    }
}

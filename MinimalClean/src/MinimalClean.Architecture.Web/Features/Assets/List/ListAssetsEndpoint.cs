using FastEndpoints;
using Microsoft.AspNetCore.Http.HttpResults;
using MinimalClean.Architecture.Web.Domain.Assets;
using MinimalClean.Architecture.Web.Domain.Assets.Enums;
using MinimalClean.Architecture.Web.Features.Assets.Specifications;

namespace MinimalClean.Architecture.Web.Features.Assets.List;

public sealed record ListAssetsRequest
{
    public Guid OrganizationId { get; init; }
    public int Page { get; init; } = 1;
    public int PageSize { get; init; } = 20;
    public string? Search { get; init; }
    public Guid? CategoryId { get; init; }
    public string? Status { get; init; }
    public string? Region { get; init; }
}

public sealed record ListAssetsResponse(
    IReadOnlyList<AssetDto> Items,
    int Page,
    int PageSize,
    int TotalCount);

public sealed class ListAssetsEndpoint(IRepository<Asset> repository) 
    : Endpoint<ListAssetsRequest, Ok<ListAssetsResponse>>
{
    public override void Configure()
    {
        Get("/assets");
        AllowAnonymous();
        Tags("Assets");
        Summary(s =>
        {
            s.Summary = "List paginated assets";
            s.Description = "Retrieves a paginated list of assets with optional search and filters.";
        });
    }

    public override async Task<Ok<ListAssetsResponse>> ExecuteAsync(ListAssetsRequest req, CancellationToken ct)
    {
        AssetStatus? statusEnum = null;
        if (!string.IsNullOrWhiteSpace(req.Status) && AssetStatus.TryFromName(req.Status, true, out var parsed))
        {
            statusEnum = parsed;
        }

        var spec = new PagedAssetsSpec(
            req.OrganizationId,
            req.Page,
            req.PageSize,
            req.Search,
            req.CategoryId,
            statusEnum,
            req.Region);

        var assets = await repository.ListAsync(spec, ct);
        
        // Count spec for total items
        var countSpec = new PagedAssetsSpec(
            req.OrganizationId,
            1,
            int.MaxValue,
            req.Search,
            req.CategoryId,
            statusEnum,
            req.Region);
        var totalCount = await repository.CountAsync(countSpec, ct);

        var dtos = assets.Select(a => new AssetDto(
            a.Id.Value,
            a.Name,
            a.Code,
            a.Description,
            a.Address,
            a.Region,
            a.Status.Name,
            a.CategoryId,
            a.CreatedAt)).ToList();

        return TypedResults.Ok(new ListAssetsResponse(dtos, req.Page, req.PageSize, totalCount));
    }
}

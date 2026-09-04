using FastEndpoints;
using Microsoft.AspNetCore.Http.HttpResults;
using MinimalClean.Architecture.Web.Domain.Assets;
using MinimalClean.Architecture.Web.Features.Assets.Specifications;

namespace MinimalClean.Architecture.Web.Features.Assets.GetById;

public sealed record GetAssetByIdRequest
{
    public Guid OrganizationId { get; init; }
    public Guid Id { get; init; }
}

public sealed class GetAssetByIdEndpoint(IRepository<Asset> repository) 
    : Endpoint<GetAssetByIdRequest, Results<Ok<AssetDto>, NotFound>>
{
    public override void Configure()
    {
        Get("/assets/{id:guid}");
        AllowAnonymous();
        Tags("Assets");
        Summary(s =>
        {
            s.Summary = "Get asset by ID";
            s.Description = "Retrieves details of an asset given its unique identifier and organization.";
        });
    }

    public override async Task<Results<Ok<AssetDto>, NotFound>> ExecuteAsync(GetAssetByIdRequest req, CancellationToken ct)
    {
        var spec = new AssetByIdSpec(req.OrganizationId, req.Id);
        var asset = await repository.FirstOrDefaultAsync(spec, ct);

        if (asset is null)
        {
            return TypedResults.NotFound();
        }

        var dto = new AssetDto(
            asset.Id.Value,
            asset.Name,
            asset.Code,
            asset.Description,
            asset.Address,
            asset.Region,
            asset.Status.Name,
            asset.CategoryId,
            asset.CreatedAt);

        return TypedResults.Ok(dto);
    }
}

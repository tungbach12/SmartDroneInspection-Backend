using FastEndpoints;
using Microsoft.AspNetCore.Http.HttpResults;
using MinimalClean.Architecture.Web.Domain.Assets;
using MinimalClean.Architecture.Web.Features.Assets.Specifications;

namespace MinimalClean.Architecture.Web.Features.Assets.Delete;

public sealed record DeleteAssetRequest
{
    public Guid OrganizationId { get; init; }
    public Guid Id { get; init; }
}

public sealed class DeleteAssetEndpoint(IRepository<Asset> repository) 
    : Endpoint<DeleteAssetRequest, Results<NoContent, NotFound>>
{
    public override void Configure()
    {
        Delete("/assets/{id:guid}");
        AllowAnonymous();
        Tags("Assets");
        Summary(s =>
        {
            s.Summary = "Soft delete an asset";
            s.Description = "Marks an asset as soft-deleted in the organization.";
        });
    }

    public override async Task<Results<NoContent, NotFound>> ExecuteAsync(DeleteAssetRequest req, CancellationToken ct)
    {
        var spec = new AssetByIdSpec(req.OrganizationId, req.Id);
        var asset = await repository.FirstOrDefaultAsync(spec, ct);

        if (asset is null)
        {
            return TypedResults.NotFound();
        }

        asset.IsDeleted = true;
        asset.DeletedAt = DateTime.UtcNow;

        await repository.UpdateAsync(asset, ct);
        await repository.SaveChangesAsync(ct);

        return TypedResults.NoContent();
    }
}

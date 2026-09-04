using FastEndpoints;
using FluentValidation;
using Microsoft.AspNetCore.Http.HttpResults;
using MinimalClean.Architecture.Web.Domain.Assets;
using MinimalClean.Architecture.Web.Domain.Assets.Enums;
using MinimalClean.Architecture.Web.Features.Assets.Specifications;

namespace MinimalClean.Architecture.Web.Features.Assets.Update;

public sealed record UpdateAssetRequest
{
    public Guid OrganizationId { get; init; }
    public Guid Id { get; init; }
    public string Name { get; init; } = string.Empty;
    public string? Description { get; init; }
    public Guid? CategoryId { get; init; }
    public string? Status { get; init; }
    public string? Address { get; init; }
    public string? Region { get; init; }
    public double? Latitude { get; init; }
    public double? Longitude { get; init; }
}

public sealed class UpdateAssetValidator : Validator<UpdateAssetRequest>
{
    public UpdateAssetValidator()
    {
        RuleFor(x => x.Name).NotEmpty().MaximumLength(250);
        RuleFor(x => x.Latitude).InclusiveBetween(-90, 90).When(x => x.Latitude.HasValue);
        RuleFor(x => x.Longitude).InclusiveBetween(-180, 180).When(x => x.Longitude.HasValue);
    }
}

public sealed class UpdateAssetEndpoint(IRepository<Asset> repository) 
    : Endpoint<UpdateAssetRequest, Results<Ok<AssetDto>, NotFound, ValidationProblem>>
{
    public override void Configure()
    {
        Put("/assets/{id:guid}");
        AllowAnonymous();
        Tags("Assets");
        Summary(s =>
        {
            s.Summary = "Update asset details";
            s.Description = "Updates specifications, location, or status of an existing asset.";
        });
    }

    public override async Task<Results<Ok<AssetDto>, NotFound, ValidationProblem>> ExecuteAsync(UpdateAssetRequest req, CancellationToken ct)
    {
        var spec = new AssetByIdSpec(req.OrganizationId, req.Id);
        var asset = await repository.FirstOrDefaultAsync(spec, ct);

        if (asset is null)
        {
            return TypedResults.NotFound();
        }

        AssetStatus? newStatus = null;
        if (!string.IsNullOrWhiteSpace(req.Status) && AssetStatus.TryFromName(req.Status, true, out var parsed))
        {
            newStatus = parsed;
        }

        asset.Update(
            name: req.Name,
            description: req.Description,
            categoryId: req.CategoryId,
            status: newStatus ?? asset.Status,
            latitude: req.Latitude,
            longitude: req.Longitude,
            address: req.Address,
            region: req.Region);

        await repository.UpdateAsync(asset, ct);
        await repository.SaveChangesAsync(ct);

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

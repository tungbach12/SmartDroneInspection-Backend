using FastEndpoints;
using FluentValidation;
using Microsoft.AspNetCore.Http.HttpResults;
using MinimalClean.Architecture.Web.Domain.Assets;
using MinimalClean.Architecture.Web.Domain.Assets.Enums;

namespace MinimalClean.Architecture.Web.Features.Assets.Create;

public sealed class CreateAssetRequest
{
    public string Name { get; init; } = string.Empty;
    public string Code { get; init; } = string.Empty;
    public string? Description { get; init; }
    public Guid? CategoryId { get; init; }
    public Guid OrganizationId { get; init; }
    public string? Address { get; init; }
    public string? Region { get; init; }
    public double? Latitude { get; init; }
    public double? Longitude { get; init; }
}

public sealed class CreateAssetValidator : Validator<CreateAssetRequest>
{
    public CreateAssetValidator()
    {
        RuleFor(x => x.Name).NotEmpty().MaximumLength(250);
        RuleFor(x => x.Code).NotEmpty().MaximumLength(100);
        RuleFor(x => x.Latitude).InclusiveBetween(-90, 90).When(x => x.Latitude.HasValue);
        RuleFor(x => x.Longitude).InclusiveBetween(-180, 180).When(x => x.Longitude.HasValue);
    }
}

public sealed class CreateAssetEndpoint(IRepository<Asset> repository) : Endpoint<CreateAssetRequest, Results<Created<AssetDto>, ValidationProblem, ProblemHttpResult>>
{
    public override void Configure()
    {
        Post("/assets");
        AllowAnonymous();
        Tags("Assets");
        Summary(s =>
        {
            s.Summary = "Create SmartDrone asset (Minimal)";
            s.Description = "Vertical slice: creates asset directly via IRepository, no separate UseCases project.";
        });
    }

    public override async Task<Results<Created<AssetDto>, ValidationProblem, ProblemHttpResult>> ExecuteAsync(CreateAssetRequest req, CancellationToken ct)
    {
        var normalizedCode = req.Code.Trim().ToUpperInvariant();

        // Ardalis Specification for duplicate check (vertical slice still uses Specification)
        var spec = new Specifications.AssetByNormalizedCodeSpec(req.OrganizationId, normalizedCode);
        var existing = await repository.FirstOrDefaultAsync(spec, ct);
        if (existing is not null)
        {
            return TypedResults.Problem($"Asset code '{req.Code}' already exists", statusCode: 409);
        }

        var asset = new Asset(
            organizationId: req.OrganizationId,
            code: req.Code.Trim(),
            normalizedCode: normalizedCode,
            name: req.Name,
            description: req.Description,
            categoryId: req.CategoryId,
            status: AssetStatus.Active,
            latitude: req.Latitude,
            longitude: req.Longitude,
            address: req.Address,
            region: req.Region);

        await repository.AddAsync(asset, ct);
        await repository.SaveChangesAsync(ct);

        var dto = new AssetDto(asset.Id.Value, asset.Name, asset.Code, asset.Description, asset.Address, asset.Region, asset.Status.Name, asset.CategoryId, asset.CreatedAt);
        return TypedResults.Created($"/assets/{asset.Id.Value}", dto);
    }
}

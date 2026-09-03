using Ardalis.GuardClauses;
using Ardalis.Result;
using Ardalis.SharedKernel;
using Clean.Architecture.Core.Assets;
using Clean.Architecture.Core.Assets.Enums;

namespace Clean.Architecture.UseCases.Assets.Create;

public class CreateAssetHandler(IRepository<Asset> repository) : IRequestHandler<CreateAssetCommand, Result<AssetId>>
{
    public async ValueTask<Result<AssetId>> Handle(CreateAssetCommand request, CancellationToken cancellationToken)
    {
        Guard.Against.NullOrWhiteSpace(request.Name);
        Guard.Against.NullOrWhiteSpace(request.Code);

        var normalizedCode = request.Code.Trim().ToUpperInvariant();

        // Check duplicate via Specification (Ardalis pattern)
        var spec = new Assets.Specifications.AssetByNormalizedCodeSpec(request.OrganizationId, normalizedCode);
        var existing = await repository.FirstOrDefaultAsync(spec, cancellationToken);
        if (existing is not null)
        {
            return Result<AssetId>.Conflict($"Asset code '{request.Code}' already exists");
        }

        var asset = new Asset(
            organizationId: request.OrganizationId,
            code: request.Code.Trim(),
            normalizedCode: normalizedCode,
            name: request.Name,
            description: request.Description,
            categoryId: request.CategoryId,
            status: AssetStatus.Active,
            latitude: request.Latitude,
            longitude: request.Longitude,
            address: request.Address,
            region: request.Region);

        var created = await repository.AddAsync(asset, cancellationToken);

        return created.Id;
    }
}

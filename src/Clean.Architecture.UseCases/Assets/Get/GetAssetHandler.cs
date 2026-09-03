using Ardalis.Result;
using Ardalis.SharedKernel;
using Clean.Architecture.Core.Assets;
using Clean.Architecture.UseCases.Assets.Specifications;

namespace Clean.Architecture.UseCases.Assets.Get;

public class GetAssetHandler(IRepository<Asset> repository) : IRequestHandler<GetAssetQuery, Result<AssetDto>>
{
    public async ValueTask<Result<AssetDto>> Handle(GetAssetQuery request, CancellationToken cancellationToken)
    {
        var spec = new AssetByIdSpec(request.AssetId);
        var asset = await repository.FirstOrDefaultAsync(spec, cancellationToken);
        if (asset is null)
        {
            return Result<AssetDto>.NotFound();
        }

        return new AssetDto(
            asset.Id, asset.Name, asset.Code, asset.Description,
            asset.Address, asset.Region, asset.Status.Name,
            asset.CategoryId, asset.CreatedAt);
    }
}

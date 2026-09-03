using Ardalis.Specification;
using Clean.Architecture.Core.Assets;

namespace Clean.Architecture.UseCases.Assets.Specifications;

public sealed class AssetByIdSpec : Specification<Asset>
{
    public AssetByIdSpec(AssetId assetId)
    {
        Query.Where(a => a.Id == assetId);
    }
}

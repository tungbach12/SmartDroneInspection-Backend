using Ardalis.Specification;
using Clean.Architecture.Core.Assets;

namespace Clean.Architecture.UseCases.Assets.Specifications;

public sealed class AssetByNormalizedCodeSpec : Specification<Asset>
{
    public AssetByNormalizedCodeSpec(Guid organizationId, string normalizedCode)
    {
        Query.Where(a => a.OrganizationId == organizationId && a.NormalizedCode == normalizedCode);
    }
}

using Ardalis.Specification;
using MinimalClean.Architecture.Web.Domain.Assets;

namespace MinimalClean.Architecture.Web.Features.Assets.Specifications;

public sealed class AssetByNormalizedCodeSpec : Specification<Asset>
{
    public AssetByNormalizedCodeSpec(Guid organizationId, string normalizedCode)
    {
        Query.Where(a => a.OrganizationId == organizationId && a.NormalizedCode == normalizedCode);
    }
}

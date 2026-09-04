using Ardalis.Specification;
using MinimalClean.Architecture.Web.Domain.Assets;

namespace MinimalClean.Architecture.Web.Features.Assets.Specifications;

public sealed class AssetByIdSpec : Specification<Asset>
{
    public AssetByIdSpec(Guid organizationId, Guid assetId)
    {
        Query
            .Where(a => a.OrganizationId == organizationId && a.Id == AssetId.From(assetId));
    }
}

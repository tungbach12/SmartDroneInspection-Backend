using Ardalis.Specification;
using MinimalClean.Architecture.Web.Domain.Assets;
using MinimalClean.Architecture.Web.Domain.Assets.Enums;

namespace MinimalClean.Architecture.Web.Features.Assets.Specifications;

public sealed class PagedAssetsSpec : Specification<Asset>
{
    public PagedAssetsSpec(
        Guid organizationId,
        int page,
        int pageSize,
        string? search = null,
        Guid? categoryId = null,
        AssetStatus? status = null,
        string? region = null)
    {
        var query = Query.Where(a => a.OrganizationId == organizationId);

        if (!string.IsNullOrWhiteSpace(search))
        {
            var s = search.Trim().ToUpperInvariant();
            query.Where(a => a.NormalizedCode.Contains(s) || a.Name.ToUpper().Contains(s));
        }

        if (categoryId.HasValue)
        {
            query.Where(a => a.CategoryId == categoryId.Value);
        }

        if (status is not null)
        {
            query.Where(a => a.Status == status);
        }

        if (!string.IsNullOrWhiteSpace(region))
        {
            query.Where(a => a.Region == region);
        }

        var skip = Math.Max(0, (page - 1) * pageSize);
        var take = Math.Clamp(pageSize, 1, 100);

        query
            .OrderByDescending(a => a.CreatedAt)
            .Skip(skip)
            .Take(take);
    }
}

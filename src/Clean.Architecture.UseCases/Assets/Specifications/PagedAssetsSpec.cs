using Ardalis.Specification;
using Clean.Architecture.Core.Assets;

namespace Clean.Architecture.UseCases.Assets.Specifications;

public sealed class PagedAssetsSpec : Specification<Asset>
{
    public PagedAssetsSpec(string? search, string? sortBy, bool sortDescending, int page, int pageSize)
    {
        if (!string.IsNullOrWhiteSpace(search))
        {
            var s = search.Trim().ToLower();
            Query.Where(a => a.Name.ToLower().Contains(s) || a.Code.ToLower().Contains(s));
        }

        // Sorting: default CreatedAt desc
        if (!string.IsNullOrWhiteSpace(sortBy))
        {
            var key = sortBy.Trim().ToLower();
            if (key == "name")
            {
                Query.OrderBy(a => a.Name);
                if (sortDescending) Query.OrderByDescending(a => a.Name);
            }
            else if (key == "code")
            {
                Query.OrderBy(a => a.Code);
                if (sortDescending) Query.OrderByDescending(a => a.Code);
            }
            else
            {
                Query.OrderByDescending(a => a.CreatedAt);
            }
        }
        else
        {
            Query.OrderByDescending(a => a.CreatedAt);
        }

        Query.Skip((page - 1) * pageSize).Take(pageSize);
    }
}

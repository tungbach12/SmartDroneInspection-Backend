using Ardalis.Result;
using Ardalis.SharedKernel;
using Ardalis.Specification;
using Clean.Architecture.Core.Assets;

namespace Clean.Architecture.UseCases.Assets.List;

public class ListAssetsHandler(IRepository<Asset> repository) : IRequestHandler<ListAssetsQuery, Result<PagedResult<AssetDto>>>
{
    public async ValueTask<Result<PagedResult<AssetDto>>> Handle(ListAssetsQuery request, CancellationToken cancellationToken)
    {
        // Simple specification for search/sort — could be moved to Specification
        var spec = new Assets.Specifications.PagedAssetsSpec(request.Search, request.SortBy, request.SortDescending, request.Page, request.PageSize);
        var paged = await repository.ListAsync(spec, cancellationToken);
        var total = await repository.CountAsync(spec, cancellationToken);

        var dtos = paged.Select(a => new AssetDto(
            a.Id, a.Name, a.Code, a.Description,
            a.Address, a.Region, a.Status.Name,
            a.CategoryId, a.CreatedAt)).ToList();

        var totalPages = (int)Math.Ceiling((double)total / request.PageSize);
        return Result<PagedResult<AssetDto>>.Success(new PagedResult<AssetDto>(dtos, request.Page, request.PageSize, total, totalPages));
    }
}

using Ardalis.Result;
using Clean.Architecture.Core.Assets;
using Mediator;

namespace Clean.Architecture.UseCases.Assets.List;

public record ListAssetsQuery(
    string? Search,
    string? SortBy,
    bool SortDescending,
    int Page,
    int PageSize) : IRequest<Result<PagedResult<AssetDto>>>;

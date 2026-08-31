using MediatR;
using Microsoft.EntityFrameworkCore;
using SmartDroneInspection.Application.Assets.Dtos;
using SmartDroneInspection.Application.Common.Interfaces;
using SmartDroneInspection.Application.Common.Models;
using SmartDroneInspection.Domain.Assets;

namespace SmartDroneInspection.Application.Assets.Queries;

public record GetAssetsQuery(AssetListQuery Filters) : IRequest<PagedResult<AssetDto>>;

public class GetAssetsQueryHandler(IApplicationDbContext db)
    : IRequestHandler<GetAssetsQuery, PagedResult<AssetDto>>
{
    private static readonly Dictionary<string, System.Linq.Expressions.Expression<Func<Asset, object>>> SortMap =
        new()
        {
            ["name"] = a => a.Name,
            ["code"] = a => a.Code,
            ["createdat"] = a => a.CreatedAt,
        };

    public async Task<PagedResult<AssetDto>> Handle(GetAssetsQuery request, CancellationToken ct)
    {
        var f = request.Filters;
        var query = db.Assets.AsNoTracking();

        if (!string.IsNullOrWhiteSpace(f.Search))
        {
            var search = f.Search.Trim().ToLower();
            query = query.Where(a =>
                a.Name.ToLower().Contains(search) ||
                a.Code.ToLower().Contains(search));
        }

        if (!string.IsNullOrWhiteSpace(f.SortBy) &&
            SortMap.TryGetValue(f.SortBy.Trim().ToLower(), out var sort))
        {
            query = f.SortDescending ? query.OrderByDescending(sort) : query.OrderBy(sort);
        }
        else
        {
            query = query.OrderByDescending(a => a.CreatedAt);
        }

        var total = await query.CountAsync(ct);
        var page = await query.Skip(f.Skip).Take(f.Take).ToListAsync(ct);

        return new PagedResult<AssetDto>(
            page.Select(a => new AssetDto(
                a.Id, a.Name, a.Code, a.Description,
                a.Address, a.Region, a.Status.ToString(),
                a.CategoryId, a.CreatedAt)).ToList(),
            f.Page, f.PageSize, total);
    }
}

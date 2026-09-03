using MediatR;
using Microsoft.EntityFrameworkCore;
using SmartDroneInspection.Application.Assets.Dtos;
using SmartDroneInspection.Application.Common.Interfaces;

namespace SmartDroneInspection.Application.Assets.Queries;

public record GetAssetByIdQuery(Guid Id) : IRequest<AssetDto>;

public class GetAssetByIdQueryHandler(IApplicationDbContext db)
    : IRequestHandler<GetAssetByIdQuery, AssetDto>
{
    public async Task<AssetDto> Handle(GetAssetByIdQuery request, CancellationToken ct)
    {
        var asset = await db.Assets.AsNoTracking()
            .FirstOrDefaultAsync(a => a.Id == request.Id, ct)
            ?? throw new KeyNotFoundException($"Asset {request.Id} not found");

        return new AssetDto(
            asset.Id, asset.Name, asset.Code, asset.Description,
            asset.Address, asset.Region, asset.Status.ToString(),
            asset.CategoryId, asset.CreatedAt);
    }
}

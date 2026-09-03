using FluentValidation;
using MediatR;
using Microsoft.EntityFrameworkCore;
using SmartDroneInspection.Application.Assets.Dtos;
using SmartDroneInspection.Application.Common.Interfaces;
using SmartDroneInspection.Domain.Assets;

namespace SmartDroneInspection.Application.Assets.Commands;

public record CreateAssetCommand(
    string Name,
    string Code,
    string? Description,
    Guid? CategoryId,
    Guid OrganizationId,
    string? Address,
    string? Region,
    double? Latitude,
    double? Longitude) : IRequest<AssetDto>;

public class CreateAssetCommandValidator : AbstractValidator<CreateAssetCommand>
{
    public CreateAssetCommandValidator()
    {
        RuleFor(x => x.Name).NotEmpty().MaximumLength(250);
        RuleFor(x => x.Code).NotEmpty().MaximumLength(100);
        RuleFor(x => x.Description).MaximumLength(2000);
        RuleFor(x => x.Latitude).InclusiveBetween(-90, 90).When(x => x.Latitude.HasValue);
        RuleFor(x => x.Longitude).InclusiveBetween(-180, 180).When(x => x.Longitude.HasValue);
    }
}

public class CreateAssetCommandHandler(IApplicationDbContext db)
    : IRequestHandler<CreateAssetCommand, AssetDto>
{
    public async Task<AssetDto> Handle(CreateAssetCommand request, CancellationToken ct)
    {
        var normalizedCode = request.Code.Trim().ToUpperInvariant();
        var codeExists = await db.Assets
            .AnyAsync(a => a.OrganizationId == request.OrganizationId
                && a.NormalizedCode == normalizedCode, ct);
        if (codeExists)
        {
            throw new InvalidOperationException($"Asset code '{request.Code}' already exists");
        }

        var asset = new Asset
        {
            OrganizationId = request.OrganizationId,
            Code = request.Code.Trim(),
            NormalizedCode = normalizedCode,
            Name = request.Name,
            Description = request.Description,
            CategoryId = request.CategoryId,
            Address = request.Address,
            Region = request.Region,
            Latitude = request.Latitude,
            Longitude = request.Longitude,
        };

        db.Assets.Add(asset);
        await db.SaveChangesAsync(ct);

        return new AssetDto(
            asset.Id, asset.Name, asset.Code, asset.Description,
            asset.Address, asset.Region, asset.Status.ToString(),
            asset.CategoryId, asset.CreatedAt);
    }
}

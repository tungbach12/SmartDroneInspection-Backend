using Ardalis.Result;
using Ardalis.SharedKernel;
using Clean.Architecture.Core.Assets;
using Mediator;

namespace Clean.Architecture.UseCases.Assets.Create;

public record CreateAssetCommand(
    string Name,
    string Code,
    string? Description,
    Guid? CategoryId,
    Guid OrganizationId,
    string? Address,
    string? Region,
    double? Latitude,
    double? Longitude) : IRequest<Result<AssetId>>;

using Clean.Architecture.Core.Assets;

namespace Clean.Architecture.UseCases.Assets;

public record AssetDto(
    AssetId Id,
    string Name,
    string Code,
    string? Description,
    string? Address,
    string? Region,
    string Status,
    Guid? CategoryId,
    DateTime CreatedAt);

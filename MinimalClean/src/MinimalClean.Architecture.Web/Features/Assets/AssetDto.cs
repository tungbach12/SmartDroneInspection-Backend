namespace MinimalClean.Architecture.Web.Features.Assets;

public record AssetDto(
    Guid Id,
    string Name,
    string Code,
    string? Description,
    string? Address,
    string? Region,
    string Status,
    Guid? CategoryId,
    DateTime CreatedAt);

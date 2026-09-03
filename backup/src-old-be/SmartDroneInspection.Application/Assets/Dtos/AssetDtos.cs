using SmartDroneInspection.Application.Common.Models;

namespace SmartDroneInspection.Application.Assets.Dtos;

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

public record CreateAssetRequest(
    string Name,
    string Code,
    string? Description,
    Guid? CategoryId,
    Guid OrganizationId,
    string? Address,
    string? Region,
    double? Latitude,
    double? Longitude);

public record AssetListQuery : PagedQuery;

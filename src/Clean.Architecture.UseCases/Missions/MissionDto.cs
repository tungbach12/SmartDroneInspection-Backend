using Clean.Architecture.Core.Missions;

namespace Clean.Architecture.UseCases.Missions;

public record InspectionRequestDto(
    InspectionRequestId Id,
    Guid AssetId,
    string Title,
    string Status,
    DateTime CreatedAt);

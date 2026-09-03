namespace SmartDroneInspection.Application.Common.Interfaces;

/// <summary>
/// Contract for the external SmartDroneHub platform (spec MF3).
/// SmartDroneInspection is a consumer, never a drone controller.
/// </summary>
public interface ISmartDroneHubClient
{
    Task<Guid> CreateMissionAsync(CreateMissionRequest request, CancellationToken ct = default);
    Task<MissionStatusDto?> GetMissionStatusAsync(Guid missionId, CancellationToken ct = default);
    Task<MissionTelemetryDto?> GetMissionTelemetryAsync(Guid missionId, CancellationToken ct = default);
    Task<IReadOnlyList<MissionImageDto>> GetMissionImagesAsync(Guid missionId, CancellationToken ct = default);
    Task<MissionFlightReportDto?> GetFlightReportAsync(Guid missionId, CancellationToken ct = default);
}

public record CreateMissionRequest(
    Guid InspectionId,
    string AssetCode,
    double Latitude,
    double Longitude,
    string MissionType,
    string? Notes);

public record MissionStatusDto(
    Guid MissionId,
    string Status,
    DateTime? StartedAt,
    DateTime? CompletedAt,
    string? ErrorMessage);

public record MissionTelemetryDto(
    Guid MissionId,
    double Latitude,
    double Longitude,
    double Altitude,
    double BatteryPercent,
    DateTime Timestamp);

public record MissionImageDto(
    Guid ImageId,
    string ObjectKey,
    DateTime CapturedAt,
    double? Latitude,
    double? Longitude);

public record MissionFlightReportDto(
    Guid MissionId,
    string Summary,
    TimeSpan FlightDuration,
    double MaxAltitude,
    DateTime GeneratedAt);

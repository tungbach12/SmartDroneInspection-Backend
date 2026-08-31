using System.Text.Json;
using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Missions;

public class DroneMission : BaseEntity, IHasVersion
{
    public Guid OrganizationId { get; set; }
    public Guid InspectionRequestId { get; set; }
    public Guid? SmartDroneHubMissionId { get; set; }
    public string? ExternalStatusCode { get; set; }
    public MissionType MissionType { get; set; }
    public string? Notes { get; set; }
    public double? PlannedAltitudeMeters { get; set; }
    public double? PlannedDistanceMeters { get; set; }
    public DroneMissionStatus Status { get; set; } = DroneMissionStatus.Created;
    public MissionCreatedVia CreatedVia { get; set; } = MissionCreatedVia.Api;
    public Guid? LaunchedByUserId { get; set; }
    public DateTime? LaunchedAt { get; set; }
    public DateTime? StartedAt { get; set; }
    public DateTime? CompletedAt { get; set; }
    public DateTime? CancelledAt { get; set; }
    public Guid? CancelledByUserId { get; set; }
    public string? CancelReason { get; set; }
    public string? FailureReason { get; set; }
    public double? TotalDistanceMeters { get; set; }
    public int? TotalFlightTimeSeconds { get; set; }
    public double? MaxAltitudeMeters { get; set; }
    public int? MaxBatteryUsedPercent { get; set; }
    public JsonDocument? WeatherConditions { get; set; }
    public DateTime? LastSyncedAt { get; set; }
    public int Version { get; set; } = 1;
}

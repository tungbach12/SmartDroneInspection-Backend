using Clean.Architecture.Core.Common;
using Clean.Architecture.Core.Missions.Enums;
using Ardalis.GuardClauses;
using System.Text.Json;

namespace Clean.Architecture.Core.Missions;

public class DroneMission : EntityBase<DroneMission, DroneMissionId>, IHasVersion, IAggregateRoot
{
    private DroneMission() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public DroneMission(
        Guid organizationId = default,
        Guid inspectionRequestId = default,
        MissionType missionType = default!,
        Guid? smartDroneHubMissionId = default!,
        string? externalStatusCode = default!,
        string? notes = default!,
        double? plannedAltitudeMeters = default!,
        double? plannedDistanceMeters = default!,
        DroneMissionStatus status = default!,
        MissionCreatedVia createdVia = default!,
        Guid? launchedByUserId = default!,
        DateTime? launchedAt = default!,
        DateTime? startedAt = default!,
        DateTime? completedAt = default!,
        DateTime? cancelledAt = default!,
        Guid? cancelledByUserId = default!,
        string? cancelReason = default!,
        string? failureReason = default!,
        double? totalDistanceMeters = default!,
        int? totalFlightTimeSeconds = default!,
        double? maxAltitudeMeters = default!,
        int? maxBatteryUsedPercent = default!,
        JsonDocument? weatherConditions = default!,
        DateTime? lastSyncedAt = default!)  
    {
        OrganizationId = Guard.Against.Default(organizationId, nameof(organizationId));
        InspectionRequestId = Guard.Against.Default(inspectionRequestId, nameof(inspectionRequestId));
        MissionType = missionType;
        SmartDroneHubMissionId = smartDroneHubMissionId;
        ExternalStatusCode = externalStatusCode;
        Notes = notes;
        PlannedAltitudeMeters = plannedAltitudeMeters;
        PlannedDistanceMeters = plannedDistanceMeters;
        Status = status;
        CreatedVia = createdVia;
        LaunchedByUserId = launchedByUserId;
        LaunchedAt = launchedAt;
        StartedAt = startedAt;
        CompletedAt = completedAt;
        CancelledAt = cancelledAt;
        CancelledByUserId = cancelledByUserId;
        CancelReason = cancelReason;
        FailureReason = failureReason;
        TotalDistanceMeters = totalDistanceMeters;
        TotalFlightTimeSeconds = totalFlightTimeSeconds;
        MaxAltitudeMeters = maxAltitudeMeters;
        MaxBatteryUsedPercent = maxBatteryUsedPercent;
        WeatherConditions = weatherConditions;
        LastSyncedAt = lastSyncedAt;
    }

    public Guid OrganizationId { get; private set; }
    public Guid InspectionRequestId { get; private set; }
    public Guid? SmartDroneHubMissionId { get; private set; }
    public string? ExternalStatusCode { get; private set; }
    public MissionType MissionType { get; private set; } = default!;
    public string? Notes { get; private set; }
    public double? PlannedAltitudeMeters { get; private set; }
    public double? PlannedDistanceMeters { get; private set; }
    public DroneMissionStatus Status { get; private set; } = DroneMissionStatus.Created;
    public MissionCreatedVia CreatedVia { get; private set; } = MissionCreatedVia.Api;
    public Guid? LaunchedByUserId { get; private set; }
    public DateTime? LaunchedAt { get; private set; }
    public DateTime? StartedAt { get; private set; }
    public DateTime? CompletedAt { get; private set; }
    public DateTime? CancelledAt { get; private set; }
    public Guid? CancelledByUserId { get; private set; }
    public string? CancelReason { get; private set; }
    public string? FailureReason { get; private set; }
    public double? TotalDistanceMeters { get; private set; }
    public int? TotalFlightTimeSeconds { get; private set; }
    public double? MaxAltitudeMeters { get; private set; }
    public int? MaxBatteryUsedPercent { get; private set; }
    public JsonDocument? WeatherConditions { get; private set; }
    public DateTime? LastSyncedAt { get; private set; }
    public int Version { get; set; } = 1;

    public DroneMission UpdateOrganizationId(Guid newOrganizationId)
    {
        OrganizationId = newOrganizationId;
        return this;
    }

    public DroneMission UpdateInspectionRequestId(Guid newInspectionRequestId)
    {
        InspectionRequestId = newInspectionRequestId;
        return this;
    }

    public DroneMission UpdateSmartDroneHubMissionId(Guid? newSmartDroneHubMissionId)
    {
        SmartDroneHubMissionId = newSmartDroneHubMissionId;
        return this;
    }

    public DroneMission UpdateExternalStatusCode(string? newExternalStatusCode)
    {
        ExternalStatusCode = newExternalStatusCode;
        return this;
    }

    public DroneMission UpdateMissionType(MissionType newMissionType)
    {
        MissionType = newMissionType;
        return this;
    }

    public DroneMission UpdateNotes(string? newNotes)
    {
        Notes = newNotes;
        return this;
    }

    public DroneMission UpdatePlannedAltitudeMeters(double? newPlannedAltitudeMeters)
    {
        PlannedAltitudeMeters = newPlannedAltitudeMeters;
        return this;
    }

    public DroneMission UpdatePlannedDistanceMeters(double? newPlannedDistanceMeters)
    {
        PlannedDistanceMeters = newPlannedDistanceMeters;
        return this;
    }

    public DroneMission UpdateStatus(DroneMissionStatus newStatus)
    {
        Status = newStatus;
        return this;
    }

    public DroneMission UpdateCreatedVia(MissionCreatedVia newCreatedVia)
    {
        CreatedVia = newCreatedVia;
        return this;
    }

    public DroneMission UpdateLaunchedByUserId(Guid? newLaunchedByUserId)
    {
        LaunchedByUserId = newLaunchedByUserId;
        return this;
    }

    public DroneMission UpdateLaunchedAt(DateTime? newLaunchedAt)
    {
        LaunchedAt = newLaunchedAt;
        return this;
    }

    public DroneMission UpdateStartedAt(DateTime? newStartedAt)
    {
        StartedAt = newStartedAt;
        return this;
    }

    public DroneMission UpdateCompletedAt(DateTime? newCompletedAt)
    {
        CompletedAt = newCompletedAt;
        return this;
    }

    public DroneMission UpdateCancelledAt(DateTime? newCancelledAt)
    {
        CancelledAt = newCancelledAt;
        return this;
    }

    public DroneMission UpdateCancelledByUserId(Guid? newCancelledByUserId)
    {
        CancelledByUserId = newCancelledByUserId;
        return this;
    }

    public DroneMission UpdateCancelReason(string? newCancelReason)
    {
        CancelReason = newCancelReason;
        return this;
    }

    public DroneMission UpdateFailureReason(string? newFailureReason)
    {
        FailureReason = newFailureReason;
        return this;
    }

    public DroneMission UpdateTotalDistanceMeters(double? newTotalDistanceMeters)
    {
        TotalDistanceMeters = newTotalDistanceMeters;
        return this;
    }

    public DroneMission UpdateTotalFlightTimeSeconds(int? newTotalFlightTimeSeconds)
    {
        TotalFlightTimeSeconds = newTotalFlightTimeSeconds;
        return this;
    }

    public DroneMission UpdateMaxAltitudeMeters(double? newMaxAltitudeMeters)
    {
        MaxAltitudeMeters = newMaxAltitudeMeters;
        return this;
    }

    public DroneMission UpdateMaxBatteryUsedPercent(int? newMaxBatteryUsedPercent)
    {
        MaxBatteryUsedPercent = newMaxBatteryUsedPercent;
        return this;
    }

    public DroneMission UpdateWeatherConditions(JsonDocument? newWeatherConditions)
    {
        WeatherConditions = newWeatherConditions;
        return this;
    }

    public DroneMission UpdateLastSyncedAt(DateTime? newLastSyncedAt)
    {
        LastSyncedAt = newLastSyncedAt;
        return this;
    }

}

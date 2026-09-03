using Ardalis.GuardClauses;

namespace Clean.Architecture.Core.Missions;

public class MissionTelemetry : EntityBase<MissionTelemetry, MissionTelemetryId>, IAggregateRoot
{
    private MissionTelemetry() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public MissionTelemetry(
        Guid droneMissionId = default,
        long sequenceNumber = default,
        double latitude = default,
        double longitude = default,
        double altitudeMeters = default,
        int batteryPercent = default,
        DateTime recordedAt = default,
        DateTime serverReceivedAt = default,
        double? groundSpeedMps = default!,
        int? signalStrengthPercent = default!,
        double? headingDegrees = default!)  
    {
        DroneMissionId = Guard.Against.Default(droneMissionId, nameof(droneMissionId));
        SequenceNumber = sequenceNumber;
        Latitude = latitude;
        Longitude = longitude;
        AltitudeMeters = altitudeMeters;
        BatteryPercent = batteryPercent;
        RecordedAt = recordedAt;
        ServerReceivedAt = serverReceivedAt;
        GroundSpeedMps = groundSpeedMps;
        SignalStrengthPercent = signalStrengthPercent;
        HeadingDegrees = headingDegrees;
    }

    public Guid DroneMissionId { get; private set; }
    public long SequenceNumber { get; private set; }
    public double Latitude { get; private set; }
    public double Longitude { get; private set; }
    public double AltitudeMeters { get; private set; }
    public double? GroundSpeedMps { get; private set; }
    public int BatteryPercent { get; private set; }
    public int? SignalStrengthPercent { get; private set; }
    public double? HeadingDegrees { get; private set; }
    public DateTime RecordedAt { get; private set; }
    public DateTime ServerReceivedAt { get; private set; }

    public MissionTelemetry UpdateDroneMissionId(Guid newDroneMissionId)
    {
        DroneMissionId = newDroneMissionId;
        return this;
    }

    public MissionTelemetry UpdateSequenceNumber(long newSequenceNumber)
    {
        SequenceNumber = newSequenceNumber;
        return this;
    }

    public MissionTelemetry UpdateLatitude(double newLatitude)
    {
        Latitude = newLatitude;
        return this;
    }

    public MissionTelemetry UpdateLongitude(double newLongitude)
    {
        Longitude = newLongitude;
        return this;
    }

    public MissionTelemetry UpdateAltitudeMeters(double newAltitudeMeters)
    {
        AltitudeMeters = newAltitudeMeters;
        return this;
    }

    public MissionTelemetry UpdateGroundSpeedMps(double? newGroundSpeedMps)
    {
        GroundSpeedMps = newGroundSpeedMps;
        return this;
    }

    public MissionTelemetry UpdateBatteryPercent(int newBatteryPercent)
    {
        BatteryPercent = newBatteryPercent;
        return this;
    }

    public MissionTelemetry UpdateSignalStrengthPercent(int? newSignalStrengthPercent)
    {
        SignalStrengthPercent = newSignalStrengthPercent;
        return this;
    }

    public MissionTelemetry UpdateHeadingDegrees(double? newHeadingDegrees)
    {
        HeadingDegrees = newHeadingDegrees;
        return this;
    }

    public MissionTelemetry UpdateRecordedAt(DateTime newRecordedAt)
    {
        RecordedAt = newRecordedAt;
        return this;
    }

    public MissionTelemetry UpdateServerReceivedAt(DateTime newServerReceivedAt)
    {
        ServerReceivedAt = newServerReceivedAt;
        return this;
    }

}
